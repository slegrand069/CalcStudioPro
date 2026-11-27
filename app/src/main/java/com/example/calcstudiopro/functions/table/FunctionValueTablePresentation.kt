package com.example.calcstudiopro.functions.table

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Formatting and tab-separated copy are presentation-only; calculation values remain untouched. */
object FunctionValueTablePresentation {
    fun format(value: Double, decimalPlaces: Int, scientificNotation: Boolean): String {
        require(value.isFinite())
        require(decimalPlaces in 0..12)
        val symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE)
        val scientific = scientificNotation && value != 0.0 &&
            (kotlin.math.abs(value) >= 1e12 || kotlin.math.abs(value) < Math.pow(10.0, -decimalPlaces.coerceAtLeast(1).toDouble()))
        val pattern = if (scientific) {
            "0" + (if (decimalPlaces == 0) "" else ".${"0".repeat(decimalPlaces)}") + "E0"
        } else {
            "#,##0" + (if (decimalPlaces == 0) "" else ".${"0".repeat(decimalPlaces)}")
        }
        val formatter = DecimalFormat(pattern, symbols).apply {
            roundingMode = RoundingMode.HALF_UP
            isGroupingUsed = !scientific
        }
        // Avoid displaying a rounded negative zero while retaining tiny values in scientific mode.
        val roundedToZero = !scientific && BigDecimal.valueOf(value)
            .setScale(decimalPlaces, RoundingMode.HALF_UP).compareTo(BigDecimal.ZERO) == 0
        return formatter.format(if (roundedToZero) 0.0 else value)
    }

    fun format(cell: FunctionValueTableCell, decimalPlaces: Int, scientificNotation: Boolean): String = when (cell) {
        is FunctionValueTableCell.Value -> format(cell.value, decimalPlaces, scientificNotation)
        is FunctionValueTableCell.Undefined -> "ND"
        is FunctionValueTableCell.NumericNonFinite -> "Num."
        is FunctionValueTableCell.EvaluationError -> "Erreur"
    }

    fun toClipboardText(result: FunctionValueTableResult, decimalPlaces: Int, scientificNotation: Boolean): String {
        val header = listOf("x") + result.columns.map { it.slotId.displayName }
        return buildString {
            append(header.joinToString("\t"))
            result.abscissas.forEachIndexed { row, x ->
                append('\n')
                append(format(x, decimalPlaces, scientificNotation))
                result.columns.forEach { column ->
                    append('\t')
                    append(format(column.cells[row], decimalPlaces, scientificNotation))
                }
            }
        }
    }

    private val com.example.calcstudiopro.functions.domain.FunctionSlotId.displayName: String
        get() = "f$number(x)"
}
