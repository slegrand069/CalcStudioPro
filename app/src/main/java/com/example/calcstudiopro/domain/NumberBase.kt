package com.example.calcstudiopro.domain

/** Numeric bases available in the programmer calculator. */
enum class NumberBase(val radix: Int, val label: String) {
    BIN(2, "BIN"),
    OCT(8, "OCT"),
    DEC(10, "DEC"),
    HEX(16, "HEX");

    fun accepts(character: Char): Boolean = character.digitToIntOrNull(radix) != null

    companion object {
        fun fromLabel(label: String): NumberBase =
            entries.firstOrNull { it.label == label }
                ?: throw IllegalArgumentException("Base inconnue")
    }
}
