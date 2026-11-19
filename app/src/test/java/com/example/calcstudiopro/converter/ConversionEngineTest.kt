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

    @Test fun speedPressurePowerAndEnergyConversions() {
        assertEquals(3.6, convert(1.0, "m_per_s", "km_per_h"), 1e-12)
        assertEquals(1.852, convert(1.0, "kn", "km_per_h"), 1e-12)
        assertEquals(1.0, convert(3.6, "km_per_h", "m_per_s"), 1e-12)
        assertEquals(1.0, convert(1.0, "mph", "mph"), 0.0)

        assertEquals(100000.0, convert(1.0, "bar", "pa"), 0.0)
        assertEquals(101325.0, convert(1.0, "atm", "pa"), 0.0)
        assertEquals(1.0, convert(100000.0, "pa", "bar"), 0.0)
        assertEquals(6894.757293168, convert(1.0, "psi", "pa"), 1e-9)
        assertEquals(133.322387415, convert(1.0, "mmhg", "pa"), 1e-12)

        assertEquals(735.49875, convert(1.0, "ch", "w"), 1e-10)
        assertEquals(745.69987158227022, convert(1.0, "hp", "w"), 1e-10)
        assertEquals(1.0, convert(1000.0, "w", "kw"), 0.0)

        assertEquals(3_600_000.0, convert(1.0, "kwh", "j"), 0.0)
        assertEquals(4.184, convert(1.0, "cal", "j"), 0.0)
        assertEquals(1.0, convert(3_600_000.0, "j", "kwh"), 0.0)
        assertEquals(1.602176634e-19, convert(1.0, "ev", "j"), 0.0)
    }

    @Test fun newCategoriesSupportRoundTripsAndRejectOverflow() {
        val categories = listOf(UnitCategory.SPEED, UnitCategory.PRESSURE, UnitCategory.POWER, UnitCategory.ENERGY)
        categories.forEach { category ->
            val base = UnitCatalog.forCategory(category).first()
            UnitCatalog.forCategory(category).forEach { unit ->
                val converted = ConversionEngine.convert(1.2345, base, unit)
                val restored = ConversionEngine.convert(converted, unit, base)
                assertEquals("Round trip failed for ${unit.id}", 1.2345, restored, 1e-12)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { convert(Double.MAX_VALUE, "m_per_s", "mph") }
        assertThrows(IllegalArgumentException::class.java) { convert(1.0, "km_per_h", "pa") }
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
