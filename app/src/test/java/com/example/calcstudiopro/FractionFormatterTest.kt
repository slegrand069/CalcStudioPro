package com.example.calcstudiopro

import com.example.calcstudiopro.domain.ApproximationResult
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Test

class FractionFormatterTest {
    @Test
    fun formatsFractionsWithoutScientificNotation() {
        assertEquals(
            "1393/985",
            formatFraction(ApproximationResult.Success(BigInteger.valueOf(1393), BigInteger.valueOf(985), 3))
        )
        assertEquals(
            "-7/5",
            formatFraction(ApproximationResult.Success(BigInteger.valueOf(-7), BigInteger.valueOf(5), 2))
        )
    }
}
