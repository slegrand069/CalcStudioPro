package com.example.calcstudiopro.functions.table

import com.example.calcstudiopro.functions.domain.FunctionColor
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import org.junit.Assert.assertEquals
import org.junit.Test

class FunctionValueTablePresentationTest {
    @Test fun formatsFrenchDecimalsAndAvoidsNegativeZero() {
        assertEquals("12,35", FunctionValueTablePresentation.format(12.345, 2, false))
        assertEquals("0,00", FunctionValueTablePresentation.format(-0.0001, 2, false))
        assertEquals("1,2300E-5", FunctionValueTablePresentation.format(0.0000123, 4, true))
        assertEquals("1,0000", FunctionValueTablePresentation.format(1.0, 4, false))
    }

    @Test fun formatsAllTypedCellStatesWithoutChangingValues() {
        assertEquals("ND", FunctionValueTablePresentation.format(FunctionValueTableCell.Undefined(com.example.calcstudiopro.domain.functions.MathExpressionError.Domain(null, "sqrt", "negative")), 3, true))
        assertEquals("Num.", FunctionValueTablePresentation.format(FunctionValueTableCell.NumericNonFinite(com.example.calcstudiopro.domain.functions.MathExpressionError.NonFiniteResult(null)), 3, true))
        assertEquals("1,2346", FunctionValueTablePresentation.format(FunctionValueTableCell.Value(1.23456789), 4, false))
    }

    @Test fun copiesRowsAsTabSeparatedTextInStableSlotAndAbscissaOrder() {
        val result = FunctionValueTableResult(
            listOf(1.0, -1.0, 1.0),
            listOf(
                FunctionValueTableColumn(FunctionSlotId.F2, "x", FunctionColor.GREEN, listOf(
                    FunctionValueTableCell.Value(2.0), FunctionValueTableCell.Undefined(com.example.calcstudiopro.domain.functions.MathExpressionError.Domain(null, "sqrt", "negative")), FunctionValueTableCell.NumericNonFinite(com.example.calcstudiopro.domain.functions.MathExpressionError.NonFiniteResult(null))
                )),
                FunctionValueTableColumn(FunctionSlotId.F5, "x", FunctionColor.RASPBERRY, listOf(
                    FunctionValueTableCell.Value(3.0), FunctionValueTableCell.Value(4.0), FunctionValueTableCell.Value(5.0)
                ))
            )
        )
        assertEquals(
            "x\tf2(x)\tf5(x)\n1,0\t2,0\t3,0\n-1,0\tND\t4,0\n1,0\tNum.\t5,0",
            FunctionValueTablePresentation.toClipboardText(result, 1, false)
        )
    }
}
