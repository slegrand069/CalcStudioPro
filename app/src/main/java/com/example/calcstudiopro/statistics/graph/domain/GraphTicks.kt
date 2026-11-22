package com.example.calcstudiopro.statistics.graph.domain

import com.example.calcstudiopro.statistics.graph.model.AxisRange
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class AxisTick(val value: Double, val label: String)

object GraphTicks {
    fun calculate(range: AxisRange, targetCount: Int = 6): List<AxisTick> {
        require(targetCount >= 2)
        val scale = maxOf(kotlin.math.abs(range.minimum), kotlin.math.abs(range.maximum))
        val min = range.minimum / scale
        val max = range.maximum / scale
        val roughStep = (max - min) / targetCount
        if (!roughStep.isFinite() || roughStep <= 0.0) return listOf(range.minimum, range.maximum).distinct().map { AxisTick(it, format(it)) }
        val exponent = floor(log10(roughStep))
        val magnitude = 10.0.pow(exponent)
        val fraction = roughStep / magnitude
        val niceFraction = when {
            fraction <= 1.0 -> 1.0
            fraction <= 2.0 -> 2.0
            fraction <= 5.0 -> 5.0
            else -> 10.0
        }
        val step = niceFraction * magnitude
        val first = ceil(min / step - 1e-12) * step
        val last = floor(max / step + 1e-12) * step
        if (!step.isFinite() || step <= 0.0 || !first.isFinite() || !last.isFinite()) {
            return listOf(range.minimum, range.maximum).distinct().map { AxisTick(it, format(it)) }
        }
        val ticks = ArrayList<Double>()
        var value = first
        var count = 0
        while (value <= last + step * 1e-10 && count < 20) {
            val tick = (value * scale).coerceIn(range.minimum, range.maximum)
            if (tick.isFinite() && (ticks.isEmpty() || tick != ticks.last())) ticks += tick
            value += step
            count++
        }
        if (ticks.isEmpty()) ticks += listOf(range.minimum, range.maximum)
        return ticks.distinct().map { AxisTick(it, format(it)) }
    }

    fun format(value: Double): String {
        if (!value.isFinite()) return "—"
        if (value == 0.0) return "0"
        return BigDecimal.valueOf(value)
            .round(MathContext(4, RoundingMode.HALF_UP))
            .stripTrailingZeros()
            .toString()
            .replace('.', ',')
    }
}
