package com.example.calcstudiopro.geometry

import org.junit.Assert.*
import org.junit.Test

class GeometryViewModelTest {
    @Test fun `initial state computes 567`() {
        val vm = GeometryViewModel()
        assertEquals("5", vm.state.value.aText); assertEquals("6", vm.state.value.bText)
        assertEquals("7", vm.state.value.cText); assertEquals(18.0, vm.state.value.result?.perimeter ?: 0.0, 0.0)
        assertTrue(vm.state.value.canSave)
    }
    @Test fun `every edit recalculates and preserves entered text`() {
        val vm = GeometryViewModel()
        vm.editA("3,")
        assertEquals("3,", vm.state.value.aText); assertNotNull(vm.state.value.result)
        vm.editB("4.0"); assertEquals("4.0", vm.state.value.bText)
        vm.editC("5"); assertEquals(12.0, vm.state.value.result?.perimeter ?: 0.0, 0.0)
    }
    @Test fun `incomplete and impossible input clears results and save`() {
        val vm = GeometryViewModel()
        vm.editA("")
        assertNull(vm.state.value.result); assertFalse(vm.state.value.canSave)
        vm.editA("1"); vm.editB("2"); vm.editC("3")
        assertNull(vm.state.value.result); assertFalse(vm.state.value.canSave)
    }
}
