package com.example.calcstudiopro.statistics.graph.domain

import com.example.calcstudiopro.statistics.graph.model.AxisRange
import com.example.calcstudiopro.statistics.graph.model.GraphTickMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class AxisTick(val value: Double, val label: String)

object GraphTicks {
    fun calculate(range: AxisRange, targetCount: Int = 6, mode: GraphTickMode = GraphTickMode.DECIMAL): List<AxisTick> {
        require(targetCount >= 2)
        val scale = maxOf(abs(range.minimum), abs(range.maximum))
        if (scale == 0.0 || !scale.isFinite()) return listOf(AxisTick(0.0, format(0.0, mode)))
        val low = range.minimum / scale
        val high = range.maximum / scale
        val span = high - low
        val roughNormalized = span / targetCount
        if (!roughNormalized.isFinite() || roughNormalized <= 0.0) return listOf(range.minimum, range.maximum).distinct().map { AxisTick(it, format(it, mode)) }
        val logRough = log10(roughNormalized) + log10(scale)
        val exp = floor(logRough)
        val fraction = 10.0.pow(logRough - exp)
        // A logarithm near an exact 1/2/5 boundary can differ by a few ulps.
        val niceFraction = when { fraction <= 1.0 + 1e-12 -> 1.0; fraction <= 2.0 + 1e-12 -> 2.0; fraction <= 5.0 + 1e-12 -> 5.0; else -> 10.0 }
        val power = 10.0.pow(exp)
        val step = if (power == 0.0) 10.0.pow(exp + 1.0) * niceFraction / 10.0 else niceFraction * power
        if (!step.isFinite() || step <= 0.0) return listOf(range.minimum, range.maximum).distinct().map { AxisTick(it, format(it, mode)) }
        val normalizedStep = step / scale
        val integerStep = if (mode == GraphTickMode.INTEGER) maxOf(1.0, kotlin.math.ceil(step)) else null
        if (integerStep != null) {
            val firstInteger = ceil(range.minimum / integerStep) * integerStep
            val lastInteger = floor(range.maximum / integerStep) * integerStep
            val values = ArrayList<Double>()
            var integerValue = firstInteger
            var integerCount = 0
            while (integerValue <= lastInteger && integerCount < 40) {
                if (integerValue.isFinite()) values += integerValue
                integerValue += integerStep
                integerCount++
            }
            if (values.isNotEmpty()) return values.distinct().map { AxisTick(it, format(it, mode)) }
            return listOf(range.minimum, range.maximum).filter { it % 1.0 == 0.0 }.distinct().map { AxisTick(it, format(it, mode)) }
        }
        if (!normalizedStep.isFinite() || normalizedStep <= 0.0) return listOf(range.minimum, range.maximum).distinct().map { AxisTick(it, format(it, mode)) }
        val first = ceil(low / normalizedStep - 1e-12) * normalizedStep
        val last = floor(high / normalizedStep + 1e-12) * normalizedStep
        val values = ArrayList<Double>()
        var value = first
        var count = 0
        while (value <= last + normalizedStep * 1e-10 && count < 40) {
            val actual = (value * scale).coerceIn(range.minimum, range.maximum)
            val tick = if (mode == GraphTickMode.INTEGER) actual.roundToNearestIntegerIfClose() else actual
            if (tick.isFinite() && (mode != GraphTickMode.INTEGER || tick % 1.0 == 0.0) && (values.isEmpty() || tick != values.last())) values += tick
            value += normalizedStep
            count++
        }
        if (values.isEmpty()) values += listOf(range.minimum, range.maximum)
        return values.distinct().map { AxisTick(it, format(it, mode)) }
    }

    fun format(value: Double, mode: GraphTickMode = GraphTickMode.DECIMAL): String {
        val label = GraphNumberFormatter.format(value, wholeNumber = mode == GraphTickMode.INTEGER, percentage = mode == GraphTickMode.PERCENT)
        return label
    }

    private fun Double.roundToNearestIntegerIfClose(): Double {
        val nearest = kotlin.math.round(this)
        return if (abs(this - nearest) <= 1e-8 * maxOf(1.0, abs(this))) nearest else this
    }
}
