package com.example.calcstudiopro.functions.table

import com.example.calcstudiopro.domain.functions.MathExpressionError
import com.example.calcstudiopro.functions.domain.FunctionColor
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import java.util.Collections
import java.util.LinkedHashSet

/** The user's chosen rule for constructing the X column. */
sealed interface FunctionValueTableGenerationMode {
    data class IntervalAndStep(val xMin: Double, val xMax: Double, val step: Double) : FunctionValueTableGenerationMode
    data class IntervalAndCount(val xMin: Double, val xMax: Double, val pointCount: Int) : FunctionValueTableGenerationMode
    data class CustomValues(val text: String) : FunctionValueTableGenerationMode
}

data class FunctionValueTableSlotSelection(val slotId: FunctionSlotId, val selected: Boolean)

/** Immutable, study-ready settings. Selection order is always the stable f1..f6 order. */
class FunctionValueTableSettings(
    val generationMode: FunctionValueTableGenerationMode = FunctionValueTableGenerationMode.IntervalAndStep(-2.0, 2.0, 0.5),
    selectedSlots: Set<FunctionSlotId> = emptySet(),
    val decimalPlaces: Int = 4,
    val scientificNotation: Boolean = true
) {
    val slots: List<FunctionValueTableSlotSelection> = Collections.unmodifiableList(
        FunctionSlotId.ordered.map { FunctionValueTableSlotSelection(it, it in selectedSlots) }
    )
    val selectedSlots: Set<FunctionSlotId> = Collections.unmodifiableSet(
        slots.asSequence().filter { it.selected }.map { it.slotId }.toCollection(LinkedHashSet())
    )

    fun copy(
        generationMode: FunctionValueTableGenerationMode = this.generationMode,
        selectedSlots: Set<FunctionSlotId> = this.selectedSlots,
        decimalPlaces: Int = this.decimalPlaces,
        scientificNotation: Boolean = this.scientificNotation
    ) = FunctionValueTableSettings(generationMode, selectedSlots, decimalPlaces, scientificNotation)

    override fun equals(other: Any?): Boolean = other is FunctionValueTableSettings &&
        generationMode == other.generationMode && selectedSlots == other.selectedSlots &&
        decimalPlaces == other.decimalPlaces && scientificNotation == other.scientificNotation

    override fun hashCode(): Int = listOf(generationMode, selectedSlots, decimalPlaces, scientificNotation).hashCode()

    override fun toString(): String = "FunctionValueTableSettings(generationMode=$generationMode, selectedSlots=$selectedSlots, decimalPlaces=$decimalPlaces, scientificNotation=$scientificNotation)"
}

sealed interface FunctionValueTableValidationError {
    data object InvalidDecimalPlaces : FunctionValueTableValidationError
    data object EmptyCustomValues : FunctionValueTableValidationError
    data object NonFiniteMinimum : FunctionValueTableValidationError
    data object NonFiniteMaximum : FunctionValueTableValidationError
    data object MinimumGreaterThanMaximum : FunctionValueTableValidationError
    data object NonFiniteStep : FunctionValueTableValidationError
    data object NonPositiveStep : FunctionValueTableValidationError
    data object StepBelowDoubleResolution : FunctionValueTableValidationError
    data object InvalidPointCount : FunctionValueTableValidationError
    data object TooManyRows : FunctionValueTableValidationError
    data class MalformedCustomValue(val oneBasedIndex: Int, val text: String) : FunctionValueTableValidationError
    data class NonFiniteCustomValue(val oneBasedIndex: Int) : FunctionValueTableValidationError
    data class NonFiniteGeneratedAbscissa(val oneBasedIndex: Int) : FunctionValueTableValidationError
    data class EmptySelectedSlot(val slotId: FunctionSlotId) : FunctionValueTableValidationError
}

sealed interface FunctionValueTableAbscissas {
    class Valid(values: List<Double>) : FunctionValueTableAbscissas {
        val values: List<Double> = Collections.unmodifiableList(ArrayList(values))
        override fun equals(other: Any?): Boolean = other is Valid && values == other.values
        override fun hashCode(): Int = values.hashCode()
    }

    data class Invalid(val error: FunctionValueTableValidationError) : FunctionValueTableAbscissas
}

/** One cell keeps the engine's typed outcome; formatting never changes its Double value. */
sealed interface FunctionValueTableCell {
    data class Value(val value: Double) : FunctionValueTableCell {
        init { require(value.isFinite()) }
    }
    data class Undefined(val error: MathExpressionError.Domain) : FunctionValueTableCell
    data class NumericNonFinite(val error: MathExpressionError.NonFiniteResult) : FunctionValueTableCell
    data class EvaluationError(val error: MathExpressionError) : FunctionValueTableCell
}

class FunctionValueTableColumn(
    val slotId: FunctionSlotId,
    val expression: String,
    val color: FunctionColor,
    cells: List<FunctionValueTableCell>
) {
    val cells: List<FunctionValueTableCell> = Collections.unmodifiableList(ArrayList(cells))
}

class FunctionValueTableResult(
    abscissas: List<Double>,
    columns: List<FunctionValueTableColumn>
) {
    val abscissas: List<Double> = Collections.unmodifiableList(ArrayList(abscissas))
    val columns: List<FunctionValueTableColumn> = Collections.unmodifiableList(ArrayList(columns))

    init {
        require(this.abscissas.size <= FunctionValueTableAbscissaGenerator.MAX_ROWS)
        require(this.abscissas.all(Double::isFinite))
        require(this.columns.map { it.slotId }.distinct().size == this.columns.size)
        require(this.columns.map { it.slotId } == this.columns.map { it.slotId }.sortedBy { it.number })
        require(this.columns.all { it.cells.size == this.abscissas.size })
    }
}

sealed interface FunctionValueTableCalculation {
    data class Completed(val result: FunctionValueTableResult) : FunctionValueTableCalculation
    data class Invalid(val error: FunctionValueTableValidationError) : FunctionValueTableCalculation
}
