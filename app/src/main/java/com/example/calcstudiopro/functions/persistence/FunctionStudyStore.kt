package com.example.calcstudiopro.functions.persistence

import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.domain.FunctionStudyId
import com.example.calcstudiopro.functions.domain.FunctionStudyKind
import com.example.calcstudiopro.functions.domain.FunctionStudyRestoreResult
import com.example.calcstudiopro.functions.domain.FunctionStudyRevision
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionColor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.CRC32
import java.util.stream.Collectors
import kotlin.concurrent.withLock

enum class StudyStoreError {
    STORAGE,
    FILE_TOO_LARGE,
    CONCURRENT_MODIFICATION,
    CORRUPT_DATA,
    INCOMPATIBLE_VERSION,
    INVALID_STUDY,
    EXPRESSION_RECOMPILE_FAILED,
    NOT_FOUND,
    DUPLICATE_ID,
    STUDY_ID_MISMATCH,
    RECOVERY_ALREADY_EXISTS
}

sealed interface StudyStoreResult<out T> {
    data class Success<T>(val value: T) : StudyStoreResult<T>
    data class Failure(val error: StudyStoreError, val detail: String? = null) : StudyStoreResult<Nothing>
}

data class NamedStudySummary(val id: FunctionStudyId, val name: String)

internal fun interface AtomicFileWriter {
    @Throws(IOException::class)
    fun write(target: Path, bytes: ByteArray)
}

internal fun interface BoundedRecordReader {
    /** Returns null only when the record is absent; other access failures must be thrown. */
    @Throws(IOException::class)
    fun read(path: Path): ByteArray?
}

internal class StudyStoreFault(val error: StudyStoreError, detail: String) : RuntimeException(detail)

private object NioAtomicFileWriter : AtomicFileWriter {
    override fun write(target: Path, bytes: ByteArray) {
        val parent = target.parent ?: throw IOException("Missing parent directory")
        Files.createDirectories(parent)
        val temporary = parent.resolve(".${target.fileName}.${UUID.randomUUID()}.tmp")
        var moved = false
        try {
            FileChannel.open(temporary, CREATE_NEW, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
                moved = true
            } catch (unsupported: AtomicMoveNotSupportedException) {
                throw IOException("Atomic replacement is not supported by this filesystem", unsupported)
            }
        } finally {
            if (!moved) Files.deleteIfExists(temporary)
        }
    }
}

internal object NioBoundedRecordReader : BoundedRecordReader {
    override fun read(path: Path): ByteArray? {
        val channel = try {
            FileChannel.open(path, READ)
        } catch (_: NoSuchFileException) {
            return null
        }
        channel.use {
            val sizeAtOpen = channel.size()
            if (sizeAtOpen > MAX_FILE_BYTES) {
                throw StudyStoreFault(StudyStoreError.FILE_TOO_LARGE, "Study file exceeds 4 MiB")
            }
            // Read one byte beyond the observed size to detect growth during the read.
            val buffer = ByteBuffer.allocate((sizeAtOpen + 1L).toInt())
            while (buffer.hasRemaining()) {
                val count = channel.read(buffer)
                if (count < 0) break
                if (count == 0) throw IOException("Study file read made no progress")
            }
            val bytesRead = buffer.position()
            if (bytesRead > MAX_FILE_BYTES) {
                throw StudyStoreFault(StudyStoreError.FILE_TOO_LARGE, "Study file exceeds 4 MiB")
            }
            if (bytesRead.toLong() != sizeAtOpen) throw IOException("Study file changed while being read")
            return buffer.array().copyOf(bytesRead)
        }
    }

    private const val MAX_FILE_BYTES = 4 * 1024 * 1024
}

/** Storage DTOs intentionally contain only durable values, never compiled expressions or ASTs. */
private data class PersistedStudy(
    val id: String,
    val kind: String,
    val name: String?,
    val angleMode: String,
    val slots: List<PersistedSlot>,
    val parameters: Map<Char, Double>
) {
    companion object {
        fun from(revision: FunctionStudyRevision) = PersistedStudy(
            id = revision.studyId.value,
            kind = revision.kind.name,
            name = revision.name,
            angleMode = revision.angleMode.name,
            slots = revision.slots.map {
                PersistedSlot(it.id.code, it.validatedExpression, it.draft, it.color.name, it.isVisible)
            },
            parameters = revision.parameters.toMap()
        )
    }
}

private data class PersistedSlot(
    val id: String,
    val validatedExpression: String?,
    val draft: String,
    val color: String,
    val isVisible: Boolean
)

/** Local, versioned binary persistence. Each record is CRC checked and atomically replaced. */
class FunctionStudyStore private constructor(
    directory: Path,
    private val engine: MathExpressionEngine,
    private val atomicWriter: AtomicFileWriter,
    private val recordReader: BoundedRecordReader,
    @Suppress("UNUSED_PARAMETER") marker: Unit
) {
    private val directory = directory.toAbsolutePath().normalize()
    private val operationLock = ReentrantLock()

    constructor(directory: Path, engine: MathExpressionEngine = MathExpressionEngine()) :
        this(directory, engine, NioAtomicFileWriter, NioBoundedRecordReader, Unit)

    internal constructor(directory: Path, engine: MathExpressionEngine, atomicWriter: AtomicFileWriter) :
        this(directory, engine, atomicWriter, NioBoundedRecordReader, Unit)

    internal constructor(
        directory: Path,
        engine: MathExpressionEngine,
        atomicWriter: AtomicFileWriter,
        recordReader: BoundedRecordReader
    ) : this(directory, engine, atomicWriter, recordReader, Unit)

    /** A missing file returns null; callers may then use a fresh default session. */
    fun loadWorkingSession(): StudyStoreResult<FunctionStudy?> = guarded {
        val revision = readRevisionOrNull(workingPath()) ?: return@guarded null
        val study = restore(revision)
        requireKind(study, FunctionStudyKind.WORKING_SESSION)
        study
    }

    /** Creates an in-memory default only when no persisted working session exists. */
    fun loadOrCreateWorkingSession(): StudyStoreResult<FunctionStudy> = when (val result = loadWorkingSession()) {
        is StudyStoreResult.Failure -> result
        is StudyStoreResult.Success -> StudyStoreResult.Success(result.value ?: FunctionStudy.newWorkingSession())
    }

    /** Persist each immutable session state explicitly; callers decide when a domain change triggers this. */
    fun saveWorkingSession(
        study: FunctionStudy,
        expectedRevision: FunctionStudyRevision? = null
    ): StudyStoreResult<Unit> = guarded {
        requireKind(study, FunctionStudyKind.WORKING_SESSION)
        val path = workingPath()
        val existing = readRevisionOrNull(path)
        if (existing != null) {
            val old = existing
            requireKind(old, FunctionStudyKind.WORKING_SESSION)
            restore(old)
            if (old != study.revision()) {
                if (expectedRevision == null || old != expectedRevision) {
                    fault(StudyStoreError.CONCURRENT_MODIFICATION, "Working session changed since the expected revision")
                }
                atomicWriter.write(path, FunctionStudyCodec.encode(study.revision()))
            }
        } else {
            if (expectedRevision != null) fault(StudyStoreError.CONCURRENT_MODIFICATION, "Working session no longer exists")
            atomicWriter.write(path, FunctionStudyCodec.encode(study.revision()))
        }
    }

    fun createNamedStudy(study: FunctionStudy): StudyStoreResult<Unit> = guarded {
        requireKind(study, FunctionStudyKind.NAMED_STUDY)
        val path = namedPath(study.id)
        if (recordReader.read(path) != null) fault(StudyStoreError.DUPLICATE_ID, "Study ID already exists")
        atomicWriter.write(path, FunctionStudyCodec.encode(study.revision()))
    }

    fun loadNamedStudy(id: FunctionStudyId): StudyStoreResult<FunctionStudy> = guarded {
        val path = namedPath(id)
        val revision = readRevisionOrNull(path) ?: fault(StudyStoreError.NOT_FOUND, "Named study not found")
        if (revision.studyId != id) fault(StudyStoreError.STUDY_ID_MISMATCH, "Stored study ID does not match requested ID")
        restore(revision).also { requireKind(it, FunctionStudyKind.NAMED_STUDY) }
    }

    fun listNamedStudies(): StudyStoreResult<List<NamedStudySummary>> = guarded {
        val directory = namedDirectory()
        val paths = try {
            Files.list(directory)
        } catch (_: NoSuchFileException) {
            return@guarded emptyList()
        }
        paths.use { entries ->
            Collections.unmodifiableList(
                entries.filter { it.fileName.toString().endsWith(FILE_SUFFIX) }
                    .map { path ->
                        val study = restore(readRevision(path))
                        requireKind(study, FunctionStudyKind.NAMED_STUDY)
                        if (namedPath(study.id) != path) {
                            fault(StudyStoreError.STUDY_ID_MISMATCH, "Stored study ID does not match its file name")
                        }
                        NamedStudySummary(study.id, requireNotNull(study.name))
                    }
                    .sorted(compareBy<NamedStudySummary> { it.name.lowercase() }.thenBy { it.id.value })
                    .collect(Collectors.toList())
            )
        }
    }

    fun updateNamedStudy(
        study: FunctionStudy,
        expectedRevision: FunctionStudyRevision? = null
    ): StudyStoreResult<Unit> = guarded {
        requireKind(study, FunctionStudyKind.NAMED_STUDY)
        val path = namedPath(study.id)
        val old = readRevisionOrNull(path) ?: fault(StudyStoreError.NOT_FOUND, "Named study not found")
        requireKind(old, FunctionStudyKind.NAMED_STUDY)
        if (old.studyId != study.id) fault(StudyStoreError.STUDY_ID_MISMATCH, "Study ID does not match stored record")
        if (old.name != study.name) fault(StudyStoreError.INVALID_STUDY, "Use renameNamedStudy to change the name")
        restore(old)
        if (old != study.revision()) {
            if (expectedRevision == null || old != expectedRevision) {
                fault(StudyStoreError.CONCURRENT_MODIFICATION, "Named study changed since the expected revision")
            }
            atomicWriter.write(path, FunctionStudyCodec.encode(study.revision()))
        }
    }

    fun renameNamedStudy(
        id: FunctionStudyId,
        newName: String,
        expectedRevision: FunctionStudyRevision? = null
    ): StudyStoreResult<Unit> = guarded {
        if (newName.isBlank()) fault(StudyStoreError.INVALID_STUDY, "Named studies require a non-blank name")
        val path = namedPath(id)
        val old = readRevisionOrNull(path) ?: fault(StudyStoreError.NOT_FOUND, "Named study not found")
        requireKind(old, FunctionStudyKind.NAMED_STUDY)
        if (old.studyId != id) fault(StudyStoreError.STUDY_ID_MISMATCH, "Study ID does not match stored record")
        if (expectedRevision != null && old != expectedRevision) {
            fault(StudyStoreError.CONCURRENT_MODIFICATION, "Named study changed since the expected revision")
        }
        restore(old)
        val renamed = old.copyWithName(newName)
        atomicWriter.write(path, FunctionStudyCodec.encode(renamed))
    }

    fun deleteNamedStudy(
        id: FunctionStudyId,
        expectedRevision: FunctionStudyRevision? = null
    ): StudyStoreResult<Unit> = guarded {
        val path = namedPath(id)
        val old = readRevisionOrNull(path) ?: fault(StudyStoreError.NOT_FOUND, "Named study not found")
        requireKind(old, FunctionStudyKind.NAMED_STUDY)
        if (old.studyId != id) fault(StudyStoreError.STUDY_ID_MISMATCH, "Study ID does not match stored record")
        if (expectedRevision != null && old != expectedRevision) {
            fault(StudyStoreError.CONCURRENT_MODIFICATION, "Named study changed since the expected revision")
        }
        restore(old)
        if (!Files.deleteIfExists(path)) fault(StudyStoreError.NOT_FOUND, "Named study not found")
    }

    fun loadRecoverySession(): StudyStoreResult<FunctionStudy?> = guarded {
        val revision = readRevisionOrNull(recoveryPath()) ?: return@guarded null
        restore(revision).also { requireKind(it, FunctionStudyKind.RECOVERY_SESSION) }
    }

    /** Existing recovery data is replaced only when the caller explicitly opts in. */
    fun saveRecoverySession(
        study: FunctionStudy,
        allowReplace: Boolean = false,
        expectedRevision: FunctionStudyRevision? = null
    ): StudyStoreResult<Unit> = guarded {
        requireKind(study, FunctionStudyKind.RECOVERY_SESSION)
        val path = recoveryPath()
        val existing = readRevisionOrNull(path)
        if (existing != null) {
            val old = existing
            requireKind(old, FunctionStudyKind.RECOVERY_SESSION)
            restore(old)
            if (!allowReplace) fault(StudyStoreError.RECOVERY_ALREADY_EXISTS, "Recovery session already exists")
            if (old != study.revision()) {
                if (expectedRevision == null || old != expectedRevision) {
                    fault(StudyStoreError.CONCURRENT_MODIFICATION, "Recovery session changed since the expected revision")
                }
                atomicWriter.write(path, FunctionStudyCodec.encode(study.revision()))
            }
        } else {
            if (expectedRevision != null) fault(StudyStoreError.CONCURRENT_MODIFICATION, "Recovery session no longer exists")
            atomicWriter.write(path, FunctionStudyCodec.encode(study.revision()))
        }
    }

    fun deleteRecoverySession(): StudyStoreResult<Unit> = guarded {
        val path = recoveryPath()
        val old = readRevisionOrNull(path) ?: fault(StudyStoreError.NOT_FOUND, "Recovery session not found")
        requireKind(old, FunctionStudyKind.RECOVERY_SESSION)
        restore(old)
        if (!Files.deleteIfExists(path)) fault(StudyStoreError.NOT_FOUND, "Recovery session not found")
    }

    private fun restore(revision: FunctionStudyRevision): FunctionStudy = when (val restored = FunctionStudy.restore(revision, engine)) {
        is FunctionStudyRestoreResult.Restored -> restored.study
        is FunctionStudyRestoreResult.InvalidSnapshot -> fault(StudyStoreError.CORRUPT_DATA, restored.reason)
        is FunctionStudyRestoreResult.SavedExpressionNoLongerCompiles -> fault(
            StudyStoreError.EXPRESSION_RECOMPILE_FAILED,
            "${restored.slotId.code}: ${restored.error}"
        )
    }

    private fun requireKind(study: FunctionStudy, expected: FunctionStudyKind) {
        if (study.kind != expected) fault(StudyStoreError.INVALID_STUDY, "Expected $expected, found ${study.kind}")
    }

    private fun requireKind(revision: FunctionStudyRevision, expected: FunctionStudyKind) {
        if (revision.kind != expected) fault(StudyStoreError.INVALID_STUDY, "Expected $expected, found ${revision.kind}")
    }

    private fun readRevision(path: Path): FunctionStudyRevision =
        readRevisionOrNull(path) ?: fault(StudyStoreError.NOT_FOUND, "Study file not found")

    private fun readRevisionOrNull(path: Path): FunctionStudyRevision? {
        val bytes = try {
            recordReader.read(path)
        } catch (error: StudyStoreFault) {
            throw error
        } catch (error: IOException) {
            fault(StudyStoreError.STORAGE, error.message ?: "Read failed")
        }
        return bytes?.let(FunctionStudyCodec::decode)
    }

    private fun workingPath() = directory.resolve(WORKING_FILE)
    private fun recoveryPath() = directory.resolve(RECOVERY_FILE)
    private fun namedDirectory() = directory.resolve(NAMED_DIRECTORY)
    private fun namedPath(id: FunctionStudyId) = namedDirectory().resolve("${idHash(id)}$FILE_SUFFIX")

    private fun idHash(id: FunctionStudyId): String = MessageDigest.getInstance("SHA-256")
        .digest(id.value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun <T> guarded(operation: () -> T): StudyStoreResult<T> = operationLock.withLock {
        try {
            Files.createDirectories(directory)
            FileChannel.open(directory.resolve(LOCK_FILE), CREATE, WRITE).use { channel ->
                channel.lock().use { StudyStoreResult.Success(operation()) }
            }
        } catch (error: StudyStoreFault) {
            StudyStoreResult.Failure(error.error, error.message)
        } catch (error: IOException) {
            StudyStoreResult.Failure(StudyStoreError.STORAGE, error.message)
        } catch (error: SecurityException) {
            StudyStoreResult.Failure(StudyStoreError.STORAGE, error.message)
        } catch (error: OverlappingFileLockException) {
            StudyStoreResult.Failure(StudyStoreError.STORAGE, "Concurrent access from another store instance")
        }
    }

    private fun fault(error: StudyStoreError, detail: String): Nothing = throw StudyStoreFault(error, detail)

    private companion object {
        const val WORKING_FILE = "working.cspf"
        const val RECOVERY_FILE = "recovery.cspf"
        const val NAMED_DIRECTORY = "named"
        const val LOCK_FILE = ".store.lock"
        const val FILE_SUFFIX = ".cspf"
    }
}

private object FunctionStudyCodec {
    private const val MAGIC = 0x43535046 // CSPF
    private const val VERSION = 1
    private const val ENVELOPE_SIZE = 20
    private const val MAX_FILE_BYTES = 4 * 1024 * 1024
    private const val MAX_STRING_BYTES = 1024 * 1024
    private const val MAX_PARAMETERS = 512

    fun encode(revision: FunctionStudyRevision): ByteArray {
        val record = PersistedStudy.from(revision)
        val payload = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeString(record.id)
                output.writeString(record.kind)
                output.writeNullableString(record.name)
                output.writeString(record.angleMode)
                output.writeInt(record.slots.size)
                record.slots.forEach { slot ->
                    output.writeString(slot.id)
                    output.writeNullableString(slot.validatedExpression)
                    output.writeString(slot.draft)
                    output.writeString(slot.color)
                    output.writeBooleanByte(slot.isVisible)
                }
                output.writeInt(record.parameters.size)
                record.parameters.toSortedMap().forEach { (name, value) ->
                    output.writeChar(name.code)
                    output.writeDouble(value)
                }
            }
            bytes.toByteArray()
        }
        if (payload.size > MAX_FILE_BYTES - ENVELOPE_SIZE) fault(StudyStoreError.INVALID_STUDY, "Study exceeds storage limit")
        if (revision.parameters.size > MAX_PARAMETERS) fault(StudyStoreError.INVALID_STUDY, "Too many parameters")
        val checksum = CRC32().apply { update(payload) }.value
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(payload.size)
                output.write(payload)
                output.writeLong(checksum)
            }
            bytes.toByteArray()
        }
    }

    fun decode(bytes: ByteArray): FunctionStudyRevision {
        if (bytes.size > MAX_FILE_BYTES) corrupt("File exceeds the supported size")
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC) corrupt("Invalid file signature")
                val version = input.readInt()
                if (version != VERSION) fault(StudyStoreError.INCOMPATIBLE_VERSION, "Unsupported version $version")
                val payloadSize = input.readInt()
                if (payloadSize < 0 || payloadSize > MAX_FILE_BYTES || payloadSize != input.available() - Long.SIZE_BYTES) {
                    corrupt("Invalid payload length")
                }
                val payload = ByteArray(payloadSize)
                input.readFully(payload)
                val expectedChecksum = input.readLong()
                if (input.available() != 0) corrupt("Trailing file data")
                val actualChecksum = CRC32().apply { update(payload) }.value
                if (actualChecksum != expectedChecksum) corrupt("Checksum mismatch")
                return decodePayload(payload)
            }
        } catch (error: StudyStoreFault) {
            throw error
        } catch (error: EOFException) {
            corrupt("Truncated file")
        } catch (error: java.nio.charset.CharacterCodingException) {
            corrupt("Malformed UTF-8")
        } catch (error: IllegalArgumentException) {
            corrupt(error.message ?: "Invalid field")
        }
    }

    private fun decodePayload(payload: ByteArray): FunctionStudyRevision =
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            val idText = input.readString()
            if (idText.isBlank()) corrupt("Missing study ID")
            val kind = input.readString().also { enumValue<FunctionStudyKind>(it) }
            val name = input.readNullableString()
            val angleMode = input.readString().also { enumValue<com.example.calcstudiopro.domain.functions.FunctionAngleMode>(it) }
            val count = input.readInt()
            if (count != FunctionSlotId.ordered.size) corrupt("Expected exactly six slots")
            val slots = ArrayList<PersistedSlot>(count)
            repeat(count) {
                val code = input.readString()
                if (FunctionSlotId.entries.none { it.code == code }) corrupt("Unknown slot ID $code")
                val validated = input.readNullableString()
                val draft = input.readString()
                val color = input.readString().also { enumValue<FunctionColor>(it) }
                val visible = input.readBooleanByte()
                slots += PersistedSlot(code, validated, draft, color, visible)
            }
            if (slots.map { it.id } != FunctionSlotId.ordered.map { it.code }) corrupt("Slots are not in fixed order")
            val parameterCount = input.readInt()
            if (parameterCount < 0 || parameterCount > MAX_PARAMETERS) corrupt("Invalid parameter count")
            val parameters = LinkedHashMap<Char, Double>(parameterCount)
            repeat(parameterCount) {
                val nameChar = input.readChar()
                val value = input.readDouble()
                if (parameters.put(nameChar, value) != null) corrupt("Duplicate parameter")
            }
            if (input.available() != 0) corrupt("Trailing payload data")
            val record = PersistedStudy(idText, kind, name, angleMode, slots, parameters)
            FunctionStudyRevision(
                FunctionStudyId(record.id),
                enumValue<FunctionStudyKind>(record.kind),
                record.name,
                record.slots.map { slot ->
                    com.example.calcstudiopro.functions.domain.FunctionSlotRevision(
                        FunctionSlotId.entries.first { it.code == slot.id },
                        slot.validatedExpression,
                        slot.draft,
                        enumValue<FunctionColor>(slot.color),
                        slot.isVisible
                    )
                },
                record.parameters,
                enumValue<com.example.calcstudiopro.domain.functions.FunctionAngleMode>(record.angleMode)
            )
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String): T =
        enumValues<T>().firstOrNull { it.name == value } ?: corrupt("Unknown ${T::class.java.simpleName}: $value")

    private fun DataOutputStream.writeString(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        if (encoded.size > MAX_STRING_BYTES) fault(StudyStoreError.INVALID_STUDY, "String exceeds storage limit")
        writeInt(encoded.size)
        write(encoded)
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBooleanByte(value != null)
        if (value != null) writeString(value)
    }

    private fun DataOutputStream.writeBooleanByte(value: Boolean) = writeByte(if (value) 1 else 0)

    private fun DataInputStream.readString(): String {
        val length = readInt()
        if (length < 0 || length > MAX_STRING_BYTES || length > available()) corrupt("Invalid string length")
        val encoded = ByteArray(length)
        readFully(encoded)
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(encoded)).toString()
    }

    private fun DataInputStream.readNullableString(): String? = if (readBooleanByte()) readString() else null

    private fun DataInputStream.readBooleanByte(): Boolean = when (val value = readUnsignedByte()) {
        0 -> false
        1 -> true
        else -> corrupt("Invalid boolean value $value")
    }

    private fun corrupt(detail: String): Nothing = fault(StudyStoreError.CORRUPT_DATA, detail)

    private fun fault(error: StudyStoreError, detail: String): Nothing = throw StudyStoreFault(error, detail)
}

private fun FunctionStudyRevision.copyWithName(newName: String) = FunctionStudyRevision(
    studyId = studyId,
    kind = kind,
    name = newName,
    slots = slots,
    parameters = parameters,
    angleMode = angleMode
)
