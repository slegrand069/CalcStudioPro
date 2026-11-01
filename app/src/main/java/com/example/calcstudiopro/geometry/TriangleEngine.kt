package com.example.calcstudiopro.geometry

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

/** Pure SSS triangle calculations. Lengths and coordinates are expressed in the input unit. */
object TriangleEngine {
    data class Point(val x: Double, val y: Double)
    data class Result(
        val a: Double, val b: Double, val c: Double,
        val perimeter: Double, val semiperimeter: Double, val area: Double,
        val angleA: Double, val angleB: Double, val angleC: Double,
        val heightA: Double, val heightB: Double, val heightC: Double,
        val nature: String, val pointA: Point, val pointB: Point, val pointC: Point
    )

    fun calculate(a: Double, b: Double, c: Double): Result {
        require(a.isFinite() && b.isFinite() && c.isFinite() && a > 0.0 && b > 0.0 && c > 0.0) { "Les côtés doivent être des nombres finis strictement positifs." }
        val scale = max(a, max(b, c))
        val xA = a / scale; val xB = b / scale; val xC = c / scale
        val sorted = listOf(xA, xB, xC).sortedDescending()
        require(sorted[1] + sorted[2] > sorted[0]) { "Ces longueurs ne forment pas un triangle." }
        val perimeter = a + b + c
        val semiperimeter = perimeter / 2.0
        require(perimeter.isFinite()) { "Le périmètre dépasse la précision disponible." }

        // Kahan's rearrangement of Heron's formula is stable for nearly degenerate triangles.
        val u = sorted[0]; val v = sorted[1]; val w = sorted[2]
        val radicand = (u + (v + w)) * (w - (u - v)) * (w + (u - v)) * (u + (v - w))
        val normalizedArea = sqrt(max(0.0, radicand)) / 4.0
        val area = normalizedArea * scale * scale
        require(area.isFinite() && area > 0.0) { "L’aire n’est pas représentable avec ces valeurs." }

        fun angle(opposite: Double, side1: Double, side2: Double): Double {
            val o = opposite / scale; val p = side1 / scale; val q = side2 / scale
            val cosine = ((p * p + q * q - o * o) / (2.0 * p * q)).coerceIn(-1.0, 1.0)
            return Math.toDegrees(acos(cosine))
        }
        val angleA = angle(a, b, c); val angleB = angle(b, a, c)
        val angleC = 180.0 - angleA - angleB
        val heightA = 2.0 * area / a; val heightB = 2.0 * area / b; val heightC = 2.0 * area / c
        require(listOf(heightA, heightB, heightC, angleA, angleB, angleC).all { it.isFinite() }) { "Résultats numériques non représentables." }

        fun same(x: Double, y: Double) = abs(x - y) <= 1e-10 * max(x, y)
        val equilateral = same(a, b) && same(b, c)
        val isosceles = same(a, b) || same(b, c) || same(a, c)
        val longest = max(a, max(b, c)) / scale
        val squares = listOf(xA * xA, xB * xB, xC * xC).sorted()
        val right = abs(squares[0] + squares[1] - squares[2]) <= 1e-10 * squares[2]
        val nature = when {
            equilateral -> "Équilatéral"
            right && isosceles -> "Rectangle isocèle"
            isosceles -> "Isocèle"
            right -> "Rectangle"
            else -> "Quelconque"
        }
        // A=(0,0), B=(c,0), C=(x,y), retaining a normalized local frame.
        val cx = (xB * xB + xC * xC - xA * xA) / (2.0 * xC)
        val cy = sqrt(max(0.0, xB * xB - cx * cx))
        require(cx.isFinite() && cy.isFinite() && cy > 0.0 && longest.isFinite()) { "Coordonnées du triangle non représentables." }
        return Result(a, b, c, perimeter, semiperimeter, area, angleA, angleB, angleC,
            heightA, heightB, heightC, nature, Point(0.0, 0.0), Point(xC * scale, 0.0), Point(cx * scale, cy * scale))
    }

    fun format(value: Double): String {
        require(value.isFinite())
        val magnitude = kotlin.math.abs(value)
        val pattern = if (magnitude != 0.0 && (magnitude < 0.01 || magnitude >= 1_000_000_000.0)) "0.##E0" else "0.##"
        return java.text.DecimalFormat(pattern, java.text.DecimalFormatSymbols(java.util.Locale.FRANCE)).apply {
            roundingMode = java.math.RoundingMode.HALF_UP
            isGroupingUsed = false
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }.format(value)
    }

    fun formatCanvasAngle(value: Double): String = java.text.DecimalFormat("0.0", java.text.DecimalFormatSymbols(java.util.Locale.FRANCE)).format(value)
}
