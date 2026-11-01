package com.example.calcstudiopro.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ConversionEngineTest {
    private fun convert(value: Double, from: String, to: String) =
        ConversionEngine.convert(value, UnitCatalog.get(from)!!, UnitCatalog.get(to)!!)

    @Test fun metricAndImperialConversions() {
        assertEquals(1.0, convert(1000.0, "m", "km"), 1e-12)
        assertEquals(0.621371192237, convert(1.0, "km", "mi"), 1e-12)
        assertEquals(2.20462262185, convert(1.0, "kg", "lb"), 1e-10)
        assertEquals(1.0, convert(10000.0, "m2", "hectare"), 1e-12)
        assertEquals(3.785411784, convert(1.0, "us_gallon", "l"), 1e-9)
    }

    @Test fun temperatureConversionsAndPhysicalLimit() {
        assertEquals(32.0, convert(0.0, "celsius", "fahrenheit"), 1e-12)
        assertEquals(273.15, convert(0.0, "celsius", "kelvin"), 1e-12)
        assertEquals(-273.15, convert(-459.67, "fahrenheit", "celsius"), 1e-10)
        assertEquals(-40.0, convert(233.15, "kelvin", "fahrenheit"), 1e-10)
        assertThrows(IllegalArgumentException::class.java) { convert(-274.0, "celsius", "kelvin") }
        assertEquals(0.0, convert(0.0, "m", "km"), 0.0)
        assertEquals(12.0, convert(12.0, "m", "m"), 0.0)
    }

    @Test fun validationRejectsIncompatibleAndNonFiniteValues() {
        assertThrows(IllegalArgumentException::class.java) {
            ConversionEngine.convert(1.0, UnitCatalog.get("m")!!, UnitCatalog.get("kg")!!)
        }
        assertThrows(IllegalArgumentException::class.java) { convert(Double.POSITIVE_INFINITY, "m", "km") }
        assertNull(ConversionEngine.parse(""))
        assertNull(ConversionEngine.parse("-"))
        assertNull(ConversionEngine.parse("1e999"))
        assertNull(ConversionEngine.parse("abc"))
        assertEquals(-1.25, ConversionEngine.parse("-1,25")!!, 0.0)
    }
}
