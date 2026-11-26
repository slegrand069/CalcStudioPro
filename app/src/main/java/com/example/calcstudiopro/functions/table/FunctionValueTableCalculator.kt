package com.example.calcstudiopro.functions.table

import com.example.calcstudiopro.domain.functions.ExpressionEvaluation
import com.example.calcstudiopro.domain.functions.MathExpressionError
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy

/** Synchronous reference calculator. Callers in later lots must invoke it off the UI thread. */
object FunctionValueTableCalculator {
    fun calculate(study: FunctionStudy, settings: FunctionValueTableSettings): FunctionValueTableCalculation {
        if (settings.decimalPlaces !in 0..12) {
            return FunctionValueTableCalculation.Invalid(FunctionValueTableValidationError.InvalidDecimalPlaces)
        }
        val abscissas = when (val generated = FunctionValueTableAbscissaGenerator.generate(settings.generationMode)) {
            is FunctionValueTableAbscissas.Invalid -> return FunctionValueTableCalculation.Invalid(generated.error)
            is FunctionValueTableAbscissas.Valid -> generated.values
        }
        return calculate(study, settings, abscissas) {}
    }

    internal fun calculate(
        study: FunctionStudy,
        settings: FunctionValueTableSettings,
        abscissas: List<Double>,
        cancellationCheck: () -> Unit
    ): FunctionValueTableCalculation {
        if (settings.decimalPlaces !in 0..12) {
            return FunctionValueTableCalculation.Invalid(FunctionValueTableValidationError.InvalidDecimalPlaces)
        }
        if (abscissas.size > FunctionValueTableAbscissaGenerator.MAX_ROWS) {
            return FunctionValueTableCalculation.Invalid(FunctionValueTableValidationError.TooManyRows)
        }
        if (abscissas.any { !it.isFinite() }) {
            return FunctionValueTableCalculation.Invalid(FunctionValueTableValidationError.NonFiniteGeneratedAbscissa(1))
        }
        val selected = FunctionSlotId.ordered.filter { it in settings.selectedSlots }
        val empty = selected.firstOrNull { study.slot(it).compiledExpression == null }
        if (empty != null) return FunctionValueTableCalculation.Invalid(FunctionValueTableValidationError.EmptySelectedSlot(empty))

        val columns = selected.map { id ->
            val slot = study.slot(id)
            val expression = requireNotNull(slot.validatedExpression)
            val compiled = requireNotNull(slot.compiledExpression)
            val cells = ArrayList<FunctionValueTableCell>(abscissas.size)
            for (x in abscissas) {
                cancellationCheck()
                cells += when (val evaluation = compiled.evaluate(x, study.parameters.values, study.angleMode)) {
                    is ExpressionEvaluation.Success -> FunctionValueTableCell.Value(evaluation.value)
                    is ExpressionEvaluation.Failure -> when (val error = evaluation.error) {
                        is MathExpressionError.Domain -> FunctionValueTableCell.Undefined(error)
                        is MathExpressionError.NonFiniteResult -> FunctionValueTableCell.NumericNonFinite(error)
                        else -> FunctionValueTableCell.EvaluationError(error)
                    }
                }
            }
            FunctionValueTableColumn(id, expression, slot.color, cells)
        }
        return FunctionValueTableCalculation.Completed(FunctionValueTableResult(abscissas, columns))
    }
}
