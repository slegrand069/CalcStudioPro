package com.example.calcstudiopro.history.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/** Immutable snapshot of the inputs and outputs that were available when an operation was saved. */
data class HistoryStructuredData(
    val operation: String,
    val inputs: List<HistoryDatum> = emptyList(),
    val results: List<HistoryDatum> = emptyList(),
    val details: List<HistoryDatum> = emptyList()
)

/** [numericValue] is the original finite value, never parsed back from rounded display text. */
data class HistoryDatum(val label: String, val displayValue: String, val numericValue: Double? = null)

/** Versioned, bounded storage codec. Older JSON details safely decode as null and keep their legacy card. */
object HistoryStructuredDataCodec {
    private const val PREFIX = "csp-history-v2:"
    private const val VERSION = 1
    private const val MAX_ENCODED_CHARS = 8_000_000
    private const val MAX_ITEMS_PER_GROUP = 100_000

    fun encode(data: HistoryStructuredData): String = buildString {
        append(PREFIX)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(VERSION)
            output.writeUTF(data.operation)
            writeItems(output, data.inputs)
            writeItems(output, data.results)
            writeItems(output, data.details)
        }
        append(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray()))
    }

    fun decode(stored: String?): HistoryStructuredData? {
        if (stored == null || !stored.startsWith(PREFIX) || stored.length > MAX_ENCODED_CHARS) return null
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(stored.substring(PREFIX.length))
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == VERSION)
                val operation = input.readUTF()
                val data = HistoryStructuredData(operation, readItems(input), readItems(input), readItems(input))
                require(input.available() == 0)
                data
            }
        }.getOrNull()
    }

    private fun writeItems(output: DataOutputStream, items: List<HistoryDatum>) {
        require(items.size <= MAX_ITEMS_PER_GROUP)
        output.writeInt(items.size)
        items.forEach { item ->
            output.writeUTF(item.label)
            output.writeUTF(item.displayValue)
            output.writeBoolean(item.numericValue != null)
            item.numericValue?.let { value -> require(value.isFinite()); output.writeDouble(value) }
        }
    }

    private fun readItems(input: DataInputStream): List<HistoryDatum> {
        val count = input.readInt()
        require(count in 0..MAX_ITEMS_PER_GROUP)
        return List(count) {
            val label = input.readUTF()
            val display = input.readUTF()
            val numeric = if (input.readBoolean()) input.readDouble().also { require(it.isFinite()) } else null
            HistoryDatum(label, display, numeric)
        }
    }
}
