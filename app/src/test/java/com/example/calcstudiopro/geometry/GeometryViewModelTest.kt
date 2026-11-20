package com.example.calcstudiopro.geometry

import org.junit.Assert.*
import org.junit.Test
import com.example.calcstudiopro.domain.AngleUnit

class GeometryViewModelTest {
    @Test fun `results appear only after explicit calculation`() {
        val vm=GeometryViewModel()
        assertNull(vm.state.value.result)
        vm.calculate()
        assertEquals(18.0,vm.state.value.result?.perimeter?:0.0,0.0)
    }
    @Test fun `edits invalidate results and preserve entered values`() {
        val vm=GeometryViewModel();vm.calculate()
        vm.editA("3,")
        assertEquals("3,",vm.state.value.aText);assertNull(vm.state.value.result);assertFalse(vm.state.value.canSave)
        vm.editA("3");vm.editB("4.0");vm.editC("5");vm.calculate()
        assertEquals(12.0,vm.state.value.result?.perimeter?:0.0,0.0)
    }
    @Test fun `figure and type changes reset prior solutions`() {
        val vm=GeometryViewModel();vm.calculate();assertTrue(vm.state.value.canSave)
        vm.selectFigure(GeometryFigure.CIRCLE)
        assertTrue(vm.state.value.solutions.isEmpty())
        vm.edit("radius","3");vm.calculate()
        assertEquals(9* Math.PI,vm.state.value.solutions.single().values["area"]!!,1e-12)
        vm.selectVariant(GeometryVariant.ARC_SECTOR)
        assertFalse(vm.state.value.canSave)
    }
    @Test fun `angle unit switch preserves the measured angle and clears output`() {
        val vm=GeometryViewModel();vm.edit("A","90");vm.calculate()
        vm.setAngleUnit(AngleUnit.RADIANS)
        assertEquals(Math.PI/2,vm.state.value.fields["A"]!!.toDouble(),1e-12)
        assertTrue(vm.state.value.solutions.isEmpty())
        vm.setAngleUnit(AngleUnit.DEGREES)
        assertEquals(90.0,vm.state.value.fields["A"]!!.toDouble(),1e-10)
    }

    @Test fun `special triangle defaults preserve imposed fields and inverse entry`() {
        val vm = GeometryViewModel()
        vm.selectVariant(GeometryVariant.RIGHT)
        assertEquals("5", vm.state.value.fields["a"])
        assertEquals("6", vm.state.value.fields["b"])
        assertEquals("", vm.state.value.fields["c"])
        vm.calculate()
        assertEquals(90.0, vm.state.value.solutions.single().values["C"]!!, 1e-12)

        vm.selectVariant(GeometryVariant.ISOSCELES)
        assertEquals("5", vm.state.value.fields["a"])
        assertEquals("5", vm.state.value.fields["b"])
        assertEquals("7", vm.state.value.fields["c"])
        vm.edit("a", "")
        vm.edit("c", "8")
        vm.edit("hC", "3")
        vm.calculate()
        assertEquals(5.0, vm.state.value.solutions.single().values["a"]!!, 1e-12)
        assertEquals(5.0, vm.state.value.solutions.single().values["b"]!!, 1e-12)
    }

    @Test fun `square defaults leave diagonal inputs blank and imposed`() {
        val vm = GeometryViewModel()
        vm.selectFigure(GeometryFigure.PARALLELOGRAM)
        vm.selectVariant(GeometryVariant.SQUARE)
        assertEquals("16", vm.state.value.fields["a"])
        assertEquals("16", vm.state.value.fields["b"])
        assertEquals("", vm.state.value.fields["diagonal1"])
        assertEquals("", vm.state.value.fields["diagonal2"])
        vm.edit("a", "")
        vm.edit("diagonal1", "10")
        vm.calculate()
        assertEquals(10.0 / kotlin.math.sqrt(2.0), vm.state.value.solutions.single().values["a"]!!, 1e-12)
    }

    @Test fun `rhombus perimeter alone returns partial sides and is not saveable`() {
        val vm = GeometryViewModel()
        vm.selectFigure(GeometryFigure.PARALLELOGRAM)
        vm.selectVariant(GeometryVariant.RHOMBUS)
        vm.edit("a", "")
        vm.edit("angle", "")
        vm.edit("perimeter", "64")
        vm.calculate()
        val solution = vm.state.value.solutions.single()
        assertEquals(16.0, solution.values["a"]!!, 1e-12)
        assertEquals(16.0, solution.values["b"]!!, 1e-12)
        assertNotNull(solution.note)
        assertFalse(vm.state.value.canSave)
    }

    @Test fun `isosceles base edits invalidate and replace the previous solution`() {
        val vm = GeometryViewModel()
        vm.selectVariant(GeometryVariant.ISOSCELES)
        assertEquals("7", vm.state.value.fields["c"])
        vm.calculate()
        assertTrue(vm.state.value.canSave)
        vm.edit("c", "")
        assertTrue(vm.state.value.solutions.isEmpty())
        vm.edit("c", "8,")
        assertEquals("8,", vm.state.value.fields["c"])
        vm.edit("c", "8.5")
        vm.calculate()
        assertEquals(8.5, vm.state.value.solutions.single().values["c"]!!, 1e-12)
        vm.edit("c", "10")
        vm.calculate()
        assertTrue(vm.state.value.solutions.isEmpty())
        assertNotNull(vm.state.value.error)
    }
}
