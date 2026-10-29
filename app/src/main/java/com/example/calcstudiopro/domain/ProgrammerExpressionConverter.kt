package com.example.calcstudiopro.domain

import com.example.calcstudiopro.formatProgrammerResult
import java.math.BigDecimal
import java.math.BigInteger

/** Converts numeric literals while preserving the expression's operators and structure. */
class ProgrammerExpressionConverter {
    fun convert(expression: String, from: NumberBase, to: NumberBase): String {
        if (from == to) return expression

        val output = StringBuilder(expression.length)
        data class FunctionFrame(val name: String?)
        val frames = mutableListOf<FunctionFrame>()
        fun allowsDecimalComma(): Boolean = frames.lastOrNull()?.name != "pow"
        var position = 0
        while (position < expression.length) {
            val character = expression[position]
            if (from == NumberBase.DEC && character in listOf('-', '−') && isUnaryMinus(expression, position) &&
                position + 1 < expression.length && expression[position + 1].isDigit()
            ) {
                val start = position++
                while (position < expression.length && expression[position].isDigit()) position++
                if (position < expression.length && expression[position] in listOf('.', ',') &&
                    (expression[position] == '.' || allowsDecimalComma())
                ) {
                    position++
                    while (position < expression.length && expression[position].isDigit()) position++
                }
                output.append(convertLiteral(expression.substring(start, position), from, to))
            } else if (character.isDigit() || (from == NumberBase.DEC && character == '.') ||
                (from == NumberBase.DEC && character == ',' && allowsDecimalComma())
            ) {
                val start = position
                position++
                while (position < expression.length && from.accepts(expression[position].uppercaseChar())) {
                    position++
                }
                if (from == NumberBase.DEC && position < expression.length && expression[position] in listOf('.', ',')) {
                    if (expression[position] == '.' || allowsDecimalComma()) {
                        position++
                        while (position < expression.length && expression[position].isDigit()) position++
                    }
                }
                output.append(convertLiteral(expression.substring(start, position).replace(',', '.'), from, to))
            } else if (character.isLetter()) {
                val start = position
                while (position < expression.length && expression[position].isLetter()) position++
                val word = expression.substring(start, position)
                val normalized = word.uppercase()
                val isOperatorOrFunction = normalized in OPERATORS_AND_FUNCTIONS ||
                    (from == NumberBase.DEC && normalized in DECIMAL_CONSTANTS)
                if (from == NumberBase.HEX && !isOperatorOrFunction && word.all { from.accepts(it.uppercaseChar()) }) {
                    output.append(convertLiteral(word, from, to))
                } else {
                    output.append(word)
                }
            } else {
                when (character) {
                    '(' -> {
                        var nameStart = position
                        while (nameStart > 0 && expression[nameStart - 1].isLetter()) nameStart--
                        val name = expression.substring(nameStart, position).lowercase().ifEmpty { null }
                        frames += FunctionFrame(name)
                    }
                    ')' -> if (frames.isNotEmpty()) frames.removeAt(frames.lastIndex)
                }
                output.append(character)
                position++
            }
        }
        return output.toString()
    }

    private fun convertLiteral(literal: String, from: NumberBase, to: NumberBase): String {
        val integer = try {
            if (from == NumberBase.DEC) {
                BigDecimal(literal.replace('−', '-').replace(',', '.')).toBigInteger() // Truncate toward zero; do not round.
            } else {
                BigInteger(literal.uppercase(), from.radix)
            }
        } catch (_: NumberFormatException) {
            throw IllegalArgumentException("Nombre invalide")
        }

        val value = if (from == NumberBase.DEC) {
            try {
                integer.longValueExact()
            } catch (_: ArithmeticException) {
                throw ArithmeticException("Overflow")
            }
        } else {
            require(integer.bitLength() <= Long.SIZE_BITS) { throw ArithmeticException("Overflow") }
            integer.toLong()
        }

        return formatProgrammerResult(value, to)
    }

    private fun isUnaryMinus(expression: String, position: Int): Boolean {
        val previous = expression.substring(0, position).trimEnd().lastOrNull()
        return previous == null || previous in listOf('+', '-', '−', '*', '×', '/', '÷', '^', '(', ',')
    }


    private companion object {
        val OPERATORS_AND_FUNCTIONS = setOf("AND", "OR", "XOR", "NOT", "LSH", "RSH", "POW", "SQRT", "SIN", "COS", "TAN", "LOG", "EXP")
        val DECIMAL_CONSTANTS = setOf("PI", "E")
    }
}
