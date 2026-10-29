package com.example.calcstudiopro

import com.example.calcstudiopro.domain.NumberBase

/** Formats the signed 64-bit value using its selected radix. */
internal fun formatProgrammerResult(value: Long, base: NumberBase): String = when (base) {
    NumberBase.DEC -> value.toString()
    else -> value.toULong().toString(base.radix).uppercase()
}

/** Adds visual groups of four digits for the result display only. */
internal fun formatProgrammerResultForDisplay(value: Long, base: NumberBase): String {
    val ungrouped = formatProgrammerResult(value, base)
    if (base == NumberBase.DEC || ungrouped.length <= 4) return ungrouped
    return ungrouped.reversed().chunked(4).joinToString(" ").reversed()
}
