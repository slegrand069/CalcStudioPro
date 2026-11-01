package com.example.calcstudiopro.converter

import org.junit.Assert.*
import org.junit.Test

class ConverterStateTest {
    @Test fun formattingIsFrenchAndUsesTenSignificantDigits() {
        assertEquals("12", ConversionFormatter.format(12.0))
        assertEquals("1,25", ConversionFormatter.format(1.2500))
        assertEquals("1,234567891", ConversionFormatter.format(1.23456789123))
        assertTrue(ConversionFormatter.format(1e-8).contains("10^"))
        assertTrue(ConversionFormatter.format(1e12).contains("10^"))
    }

    @Test fun bidirectionalEditingAndIntermediateInput() {
        val vm = ConverterViewModel()
        vm.editSource("1")
        assertEquals("0,1", vm.state.value.targetText)
        vm.editTarget("250")
        assertEquals("2500", vm.state.value.sourceText)
        assertEquals(ActiveConverterField.TARGET, vm.state.value.activeField)
        vm.editSource("-")
        assertEquals("-", vm.state.value.sourceText)
        assertFalse(vm.state.value.canSave)
        vm.editSource("")
        assertEquals("", vm.state.value.sourceText)
    }

    @Test fun unitChangeKeepsActiveInputAndCategoryClears() {
        val vm = ConverterViewModel()
        vm.editSource("1,5")
        vm.selectUnit(true, "m")
        assertEquals("1,5", vm.state.value.sourceText)
        assertEquals("150", vm.state.value.targetText)
        vm.selectCategory(UnitCategory.TEMPERATURE)
        assertEquals("", vm.state.value.sourceText)
        assertEquals("celsius", vm.state.value.sourceUnitId)
        assertFalse(vm.state.value.canSave)
    }

    @Test fun inversionExchangesValuesWithoutReconversion() {
        val vm = ConverterViewModel()
        vm.editSource("1,23456789")
        val source = vm.state.value.sourceText
        val target = vm.state.value.targetText
        vm.invert()
        assertEquals(target, vm.state.value.sourceText)
        assertEquals(source, vm.state.value.targetText)
        assertEquals("cm", vm.state.value.sourceUnitId)
        assertEquals("mm", vm.state.value.targetUnitId)
        assertEquals(ActiveConverterField.TARGET, vm.state.value.activeField)
    }
}
