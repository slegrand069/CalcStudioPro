package com.example.calcstudiopro.functions.domain

import com.example.calcstudiopro.domain.functions.CompiledMathExpression
import com.example.calcstudiopro.domain.functions.ExpressionCompilation
import com.example.calcstudiopro.domain.functions.ExpressionEvaluation
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.domain.functions.MathExpressionError
import java.util.Collections
import java.util.UUID

/** Stable slot identities and their fixed display order. */
enum class FunctionSlotId(val number: Int, val code: String) {
    F1(1, "f1"), F2(2, "f2"), F3(3, "f3"), F4(4, "f4"), F5(5, "f5"), F6(6, "f6");

    companion object {
        val ordered: List<FunctionSlotId> = Collections.unmodifiableList(entries.toList())
    }
}

enum class FunctionColor(val argb: Long) {
    BLUE(0xFF2563EB),
    RED(0xFFDC2626),
    GREEN(0xFF059669),
    AMBER(0xFFD97706),
    VIOLET(0xFF7C3AED),
    CYAN(0xFF0891B2),
    SUN_YELLOW(0xFFFACC15),
    VIVID_ORANGE(0xFFFF6B00),
    NAVY(0xFF172554),
    RASPBERRY(0xFFE11D74),
    LIME(0xFF84CC16),
    SLATE(0xFF475569)
}

/** Six reusable, distinct defaults. A slot may select any color, including another slot's color. */
object FunctionColorPalette {
    val colors: List<FunctionColor> = Collections.unmodifiableList(FunctionColor.entries.toList())
    val defaults: List<FunctionColor> = Collections.unmodifiableList(
        listOf(
            FunctionColor.BLUE,
            FunctionColor.RED,
            FunctionColor.GREEN,
            FunctionColor.AMBER,
            FunctionColor.VIOLET,
            FunctionColor.CYAN
        )
    )

    fun defaultFor(id: FunctionSlotId): FunctionColor = defaults[id.number - 1]
}

/** One fixed function position. Compiled ASTs are in-memory only and excluded from snapshots. */
class FunctionSlot private constructor(
    val id: FunctionSlotId,
    val validatedExpression: String?,
    val draft: String,
    val color: FunctionColor,
    val isVisible: Boolean,
    internal val compiledExpression: CompiledMathExpression?
) {
    val isDraftModified: Boolean
        get() = draft != (validatedExpression ?: "")

    fun editDraft(text: String): FunctionSlot = create(
        id, validatedExpression, text, color, isVisible, compiledExpression
    )

    fun withColor(newColor: FunctionColor): FunctionSlot = create(
        id, validatedExpression, draft, newColor, isVisible, compiledExpression
    )

    fun withVisibility(visible: Boolean): FunctionSlot = create(
        id, validatedExpression, draft, color, visible, compiledExpression
    )

    fun validate(engine: MathExpressionEngine): FunctionValidationResult {
        if (draft.isBlank()) {
            val cleared = create(id, null, "", color, isVisible, null)
            return FunctionValidationResult.Accepted(cleared, expression = null)
        }
        return when (val compilation = engine.compile(draft)) {
            is ExpressionCompilation.Success -> {
                val accepted = create(id, draft, draft, color, isVisible, compilation.expression)
                FunctionValidationResult.Accepted(accepted, expression = draft)
            }
            is ExpressionCompilation.Failure -> FunctionValidationResult.Rejected(this, compilation.error)
        }
    }

    fun evaluate(
        x: Double,
        parameters: StudyParameters,
        angleMode: FunctionAngleMode
    ): FunctionEvaluationResult {
        val compiled = compiledExpression ?: return FunctionEvaluationResult.Empty(id)
        return when (val evaluation = compiled.evaluate(x, parameters.values, angleMode)) {
            is ExpressionEvaluation.Success -> FunctionEvaluationResult.Value(id, evaluation.value)
            is ExpressionEvaluation.Failure -> FunctionEvaluationResult.Failure(id, evaluation.error)
        }
    }

    internal fun revision(): FunctionSlotRevision = FunctionSlotRevision(
        id = id,
        validatedExpression = validatedExpression,
        draft = draft,
        color = color,
        isVisible = isVisible
    )

    companion object {
        internal fun empty(id: FunctionSlotId): FunctionSlot = create(
            id = id,
            validatedExpression = null,
            draft = "",
            color = FunctionColorPalette.defaultFor(id),
            isVisible = true,
            compiledExpression = null
        )

        internal fun restore(
            revision: FunctionSlotRevision,
            compiledExpression: CompiledMathExpression?
        ): FunctionSlot = create(
            id = revision.id,
            validatedExpression = revision.validatedExpression,
            draft = revision.draft,
            color = revision.color,
            isVisible = revision.isVisible,
            compiledExpression = compiledExpression
        )

        private fun create(
            id: FunctionSlotId,
            validatedExpression: String?,
            draft: String,
            color: FunctionColor,
            isVisible: Boolean,
            compiledExpression: CompiledMathExpression?
        ) = FunctionSlot(id, validatedExpression, draft, color, isVisible, compiledExpression)
    }
}

sealed interface FunctionValidationResult {
    data class Accepted(val slot: FunctionSlot, val expression: String?) : FunctionValidationResult
    data class Rejected(val slot: FunctionSlot, val error: MathExpressionError) : FunctionValidationResult
}

sealed interface FunctionEvaluationResult {
    data class Empty(val slotId: FunctionSlotId) : FunctionEvaluationResult
    data class Value(val slotId: FunctionSlotId, val value: Double) : FunctionEvaluationResult
    data class Failure(val slotId: FunctionSlotId, val error: MathExpressionError) : FunctionEvaluationResult
}

sealed interface StudyValidationResult {
    data class Accepted(val study: FunctionStudy, val slotId: FunctionSlotId, val expression: String?) : StudyValidationResult
    data class Rejected(val study: FunctionStudy, val slotId: FunctionSlotId, val error: MathExpressionError) : StudyValidationResult
}

enum class ParameterUpdateError { INVALID_NAME, NON_FINITE_VALUE }

sealed interface ParameterUpdateResult {
    data class Updated(val parameters: StudyParameters) : ParameterUpdateResult
    data class Rejected(val error: ParameterUpdateError) : ParameterUpdateResult
}

/** Immutable, normalized parameter registry shared by all six slots in one study. */
class StudyParameters private constructor(values: Map<Char, Double>) {
    val values: Map<Char, Double> = Collections.unmodifiableMap(LinkedHashMap(values))

    fun value(name: Char): Double = values[name.lowercaseChar()] ?: 1.0

    fun withValue(name: Char, value: Double): ParameterUpdateResult {
        val normalizedName = name.lowercaseChar()
        if (!name.isLetter() || normalizedName in RESERVED_NAMES) {
            return ParameterUpdateResult.Rejected(ParameterUpdateError.INVALID_NAME)
        }
        if (!value.isFinite()) return ParameterUpdateResult.Rejected(ParameterUpdateError.NON_FINITE_VALUE)
        return ParameterUpdateResult.Updated(StudyParameters(values + (normalizedName to value)))
    }

    fun reset(name: Char): ParameterUpdateResult {
        val normalizedName = name.lowercaseChar()
        if (!name.isLetter() || normalizedName in RESERVED_NAMES) {
            return ParameterUpdateResult.Rejected(ParameterUpdateError.INVALID_NAME)
        }
        return ParameterUpdateResult.Updated(StudyParameters(values - normalizedName))
    }

    override fun equals(other: Any?): Boolean = other is StudyParameters && values == other.values
    override fun hashCode(): Int = values.hashCode()
    override fun toString(): String = "StudyParameters(values=$values)"

    companion object {
        private val RESERVED_NAMES = setOf('x', 'e', 't', 'π')
        val defaults: StudyParameters = StudyParameters(emptyMap())

        internal fun restore(values: Map<Char, Double>): StudyParameterRestoreResult {
            var restored = defaults
            val normalizedNames = HashSet<Char>()
            for ((name, value) in values) {
                val normalized = name.lowercaseChar()
                if (!normalizedNames.add(normalized)) return StudyParameterRestoreResult.Invalid
                when (val result = restored.withValue(name, value)) {
                    is ParameterUpdateResult.Updated -> restored = result.parameters
                    is ParameterUpdateResult.Rejected -> return StudyParameterRestoreResult.Invalid
                }
            }
            return StudyParameterRestoreResult.Valid(restored)
        }
    }
}

internal sealed interface StudyParameterRestoreResult {
    data class Valid(val parameters: StudyParameters) : StudyParameterRestoreResult
    data object Invalid : StudyParameterRestoreResult
}

enum class FunctionStudyKind { WORKING_SESSION, NAMED_STUDY, RECOVERY_SESSION }

sealed interface FunctionStudyRestoreResult {
    data class Restored(val study: FunctionStudy) : FunctionStudyRestoreResult
    data class InvalidSnapshot(val reason: String) : FunctionStudyRestoreResult
    data class SavedExpressionNoLongerCompiles(
        val slotId: FunctionSlotId,
        val error: MathExpressionError
    ) : FunctionStudyRestoreResult
}

@JvmInline
value class FunctionStudyId(val value: String) {
    init { require(value.isNotBlank()) }

    companion object {
        fun new(): FunctionStudyId = FunctionStudyId(UUID.randomUUID().toString())
    }
}

data class FunctionSlotRevision(
    val id: FunctionSlotId,
    val validatedExpression: String?,
    val draft: String,
    val color: FunctionColor,
    val isVisible: Boolean
)

/** Explicit immutable comparison point; ASTs are intentionally not part of study revisions. */
class FunctionStudyRevision internal constructor(
    val studyId: FunctionStudyId,
    val kind: FunctionStudyKind,
    val name: String?,
    slots: List<FunctionSlotRevision>,
    parameters: Map<Char, Double>,
    val angleMode: FunctionAngleMode
) {
    val slots: List<FunctionSlotRevision> = Collections.unmodifiableList(slots.toList())
    val parameters: Map<Char, Double> = Collections.unmodifiableMap(LinkedHashMap(parameters))

    override fun equals(other: Any?): Boolean = other is FunctionStudyRevision &&
        studyId == other.studyId && kind == other.kind && name == other.name &&
        slots == other.slots && parameters == other.parameters && angleMode == other.angleMode

    override fun hashCode(): Int = listOf(studyId, kind, name, slots, parameters, angleMode).hashCode()
}

/** Immutable six-slot study model. Storage and lifecycle operations belong to a later layer. */
class FunctionStudy private constructor(
    val id: FunctionStudyId,
    val kind: FunctionStudyKind,
    val name: String?,
    slots: List<FunctionSlot>,
    val parameters: StudyParameters,
    val angleMode: FunctionAngleMode
) {
    val slots: List<FunctionSlot> = Collections.unmodifiableList(slots.toList())

    init {
        require(this.slots.map { it.id } == FunctionSlotId.ordered) { "A study must contain the six ordered function slots" }
        require(kind != FunctionStudyKind.NAMED_STUDY || !name.isNullOrBlank()) { "Named studies require a name" }
    }

    val usedParameters: Set<Char>
        get() = Collections.unmodifiableSet(
            slots.asSequence()
                .mapNotNull(FunctionSlot::validatedExpression)
                .flatMap { FunctionParameterScanner.scan(it).asSequence() }
                .toSortedSet()
        )

    fun slot(id: FunctionSlotId): FunctionSlot = slots[id.number - 1]

    fun withDraft(id: FunctionSlotId, draft: String): FunctionStudy = replacing(slot(id).editDraft(draft))

    fun withColor(id: FunctionSlotId, color: FunctionColor): FunctionStudy = replacing(slot(id).withColor(color))

    fun withVisibility(id: FunctionSlotId, visible: Boolean): FunctionStudy = replacing(slot(id).withVisibility(visible))

    fun withAngleMode(mode: FunctionAngleMode): FunctionStudy =
        FunctionStudy(id, kind, name, slots, parameters, mode)

    fun withParameter(name: Char, value: Double): StudyParameterUpdate = when (val update = parameters.withValue(name, value)) {
        is ParameterUpdateResult.Updated -> StudyParameterUpdate.Updated(
            FunctionStudy(id, kind, this.name, slots, update.parameters, angleMode)
        )
        is ParameterUpdateResult.Rejected -> StudyParameterUpdate.Rejected(update.error)
    }

    fun resetParameter(name: Char): StudyParameterUpdate = when (val update = parameters.reset(name)) {
        is ParameterUpdateResult.Updated -> StudyParameterUpdate.Updated(
            FunctionStudy(id, kind, this.name, slots, update.parameters, angleMode)
        )
        is ParameterUpdateResult.Rejected -> StudyParameterUpdate.Rejected(update.error)
    }

    fun validateFunction(id: FunctionSlotId, engine: MathExpressionEngine): StudyValidationResult =
        when (val result = slot(id).validate(engine)) {
            is FunctionValidationResult.Accepted -> StudyValidationResult.Accepted(
                replacing(result.slot), id, result.expression
            )
            is FunctionValidationResult.Rejected -> StudyValidationResult.Rejected(this, id, result.error)
        }

    fun evaluate(id: FunctionSlotId, x: Double): FunctionEvaluationResult =
        slot(id).evaluate(x, parameters, angleMode)

    fun revision(): FunctionStudyRevision = FunctionStudyRevision(
        studyId = id,
        kind = kind,
        name = name,
        slots = slots.map(FunctionSlot::revision),
        parameters = parameters.values,
        angleMode = angleMode
    )

    fun hasUnsavedChangesSince(reference: FunctionStudyRevision): Boolean = revision() != reference

    private fun replacing(slot: FunctionSlot): FunctionStudy {
        val updated = slots.map { if (it.id == slot.id) slot else it }
        return FunctionStudy(id, kind, name, updated, parameters, angleMode)
    }

    companion object {
        fun newWorkingSession(id: FunctionStudyId = FunctionStudyId.new()): FunctionStudy = create(
            id, FunctionStudyKind.WORKING_SESSION, name = null
        )

        fun newNamedStudy(name: String, id: FunctionStudyId = FunctionStudyId.new()): FunctionStudy {
            require(name.isNotBlank()) { "Named studies require a non-blank name" }
            return create(id, FunctionStudyKind.NAMED_STUDY, name)
        }

        fun newRecoverySession(id: FunctionStudyId = FunctionStudyId.new()): FunctionStudy = create(
            id, FunctionStudyKind.RECOVERY_SESSION, name = null
        )

        fun restore(revision: FunctionStudyRevision, engine: MathExpressionEngine): FunctionStudyRestoreResult {
            if (revision.slots.map { it.id } != FunctionSlotId.ordered) {
                return FunctionStudyRestoreResult.InvalidSnapshot("Six slots in fixed order are required")
            }
            if (revision.kind == FunctionStudyKind.NAMED_STUDY && revision.name.isNullOrBlank()) {
                return FunctionStudyRestoreResult.InvalidSnapshot("Named studies require a name")
            }
            val restoredParameters = when (val result = StudyParameters.restore(revision.parameters)) {
                is StudyParameterRestoreResult.Valid -> result.parameters
                StudyParameterRestoreResult.Invalid -> return FunctionStudyRestoreResult.InvalidSnapshot("Invalid parameter registry")
            }
            val restoredSlots = ArrayList<FunctionSlot>(FunctionSlotId.ordered.size)
            for (slot in revision.slots) {
                val compiled = if (slot.validatedExpression == null) {
                    null
                } else {
                    if (slot.validatedExpression.isBlank()) {
                        return FunctionStudyRestoreResult.InvalidSnapshot("A validated expression cannot be blank")
                    }
                    when (val compilation = engine.compile(slot.validatedExpression)) {
                        is ExpressionCompilation.Success -> compilation.expression
                        is ExpressionCompilation.Failure -> return FunctionStudyRestoreResult.SavedExpressionNoLongerCompiles(
                            slot.id,
                            compilation.error
                        )
                    }
                }
                restoredSlots += FunctionSlot.restore(slot, compiled)
            }
            return runCatching {
                FunctionStudy(
                    id = revision.studyId,
                    kind = revision.kind,
                    name = revision.name,
                    slots = restoredSlots,
                    parameters = restoredParameters,
                    angleMode = revision.angleMode
                )
            }.fold(
                onSuccess = FunctionStudyRestoreResult::Restored,
                onFailure = { FunctionStudyRestoreResult.InvalidSnapshot(it.message ?: "Invalid study snapshot") }
            )
        }

        private fun create(id: FunctionStudyId, kind: FunctionStudyKind, name: String?): FunctionStudy = FunctionStudy(
            id = id,
            kind = kind,
            name = name,
            slots = FunctionSlotId.ordered.map(FunctionSlot::empty),
            parameters = StudyParameters.defaults,
            angleMode = FunctionAngleMode.RAD
        )
    }
}

sealed interface StudyParameterUpdate {
    data class Updated(val study: FunctionStudy) : StudyParameterUpdate
    data class Rejected(val error: ParameterUpdateError) : StudyParameterUpdate
}

/** Mirrors the expression lexer's function/constant rules to report only actual parameter letters. */
private object FunctionParameterScanner {
    private val functions = listOf(
        "asin", "acos", "atan", "sqrt", "cbrt", "floor", "ceil", "sign", "sin", "cos", "tan",
        "min", "max", "root", "pow", "log", "ln", "exp", "abs"
    )

    fun scan(source: String): Set<Char> {
        val used = linkedSetOf<Char>()
        var index = 0
        while (index < source.length) {
            val character = source[index]
            if (!character.isLetter()) {
                index++
                continue
            }
            if (source.regionMatches(index, "pi", 0, 2, ignoreCase = true)) {
                index += 2
                continue
            }
            val function = functions.firstOrNull { candidate ->
                if (!source.regionMatches(index, candidate, 0, candidate.length, ignoreCase = true)) return@firstOrNull false
                var afterName = index + candidate.length
                while (afterName < source.length && source[afterName].isWhitespace()) afterName++
                afterName < source.length && source[afterName] == '('
            }
            if (function != null) {
                index += function.length
                continue
            }
            val name = character.lowercaseChar()
            if (name !in setOf('x', 'e', 't', 'π')) used += name
            index++
        }
        return used
    }
}
