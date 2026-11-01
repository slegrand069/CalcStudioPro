package com.example.calcstudiopro.geometry

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class TriangleEngineTest {
    @Test fun `345 right triangle`() {
        val r = TriangleEngine.calculate(3.0, 4.0, 5.0)
        assertEquals(12.0, r.perimeter, 1e-12); assertEquals(6.0, r.area, 1e-12)
        assertEquals(90.0, r.angleC, 1e-10); assertEquals(36.86989765, r.angleA, 1e-7)
        assertEquals(53.13010235, r.angleB, 1e-7)
        assertEquals(4.0, r.heightA, 1e-12); assertEquals(3.0, r.heightB, 1e-12)
        assertEquals(2.4, r.heightC, 1e-12); assertEquals("Rectangle", r.nature)
    }
    @Test fun `equilateral and right isosceles`() {
        val e = TriangleEngine.calculate(2.0, 2.0, 2.0)
        assertEquals(sqrt(3.0), e.area, 1e-12); assertEquals("Équilatéral", e.nature)
        val i = TriangleEngine.calculate(1.0, 1.0, sqrt(2.0))
        assertEquals(90.0, i.angleC, 1e-8); assertEquals("Rectangle isocèle", i.nature)
    }
    @Test fun `567 measurements and coordinates`() {
        val r = TriangleEngine.calculate(5.0, 6.0, 7.0)
        assertEquals(18.0, r.perimeter, 1e-12); assertEquals(14.69693846, r.area, 1e-7)
        assertEquals(180.0, r.angleA + r.angleB + r.angleC, 1e-12)
        assertEquals(7.0, r.pointB.x, 1e-12); assertEquals(6.0, r.pointA.distance(r.pointC), 1e-12)
        assertEquals(5.0, r.pointB.distance(r.pointC), 1e-12)
    }
    @Test fun `invalid and extreme triangles`() {
        listOf(Triple(0.0, 2.0, 2.0), Triple(-1.0, 2.0, 2.0), Triple(1.0, 2.0, 3.0),
            Triple(1.0, 2.0, 10.0), Triple(Double.NaN, 2.0, 2.0), Triple(Double.POSITIVE_INFINITY, 2.0, 2.0),
            Triple(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE), Triple(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE))
            .forEach { t -> assertThrows(IllegalArgumentException::class.java) { TriangleEngine.calculate(t.first, t.second, t.third) } }
        val flat = TriangleEngine.calculate(1.0, 1.0, 1.999999999)
        assertTrue(flat.area.isFinite() && flat.area > 0)
        val huge = TriangleEngine.calculate(1e100, 1e100, 1e100)
        assertTrue(huge.area.isFinite())
    }
    private fun TriangleEngine.Point.distance(other: TriangleEngine.Point) = kotlin.math.hypot(x - other.x, y - other.y)
}
