package com.example.calcstudiopro.domain

import java.math.BigInteger

/** Integer expression evaluator for BIN/OCT/HEX and integer-only DEC operations. */
class ProgrammerCalculatorEngine {
    fun evaluate(expression: String, base: NumberBase): Long {
        val normalized = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
        return Parser(normalized, base).parse()
    }

    fun shiftLeft(value: Long): Long = checkedOverflow {
        Math.multiplyExact(value, 2L)
    }

    fun shiftRight(value: Long): Long = value shr 1

    private class Parser(
        private val input: String,
        private val base: NumberBase
    ) {
        private var position = 0

        fun parse(): Long {
            val result = parseXor()
            skipSpaces()
            require(position == input.length) { "Syntaxe incorrecte" }
            return result
        }

        // XOR has the lowest precedence, then OR, then AND.
        private fun parseXor(): Long {
            var value = parseOr()
            while (consumeWord("XOR")) value = value xor parseOr()
            return value
        }

        private fun parseOr(): Long {
            var value = parseAnd()
            while (consumeWord("OR")) value = value or parseAnd()
            return value
        }

        private fun parseAnd(): Long {
            var value = parseAddition()
            while (consumeWord("AND")) value = value and parseAddition()
            return value
        }

        private fun parseAddition(): Long {
            var value = parseTerm()
            while (true) {
                skipSpaces()
                value = when {
                    eat('+') -> checkedOverflow { Math.addExact(value, parseTerm()) }
                    eat('-') -> checkedOverflow { Math.subtractExact(value, parseTerm()) }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Long {
            var value = parseUnary()
            while (true) {
                skipSpaces()
                when {
                    eat('*') -> value = checkedOverflow { Math.multiplyExact(value, parseUnary()) }
                    eat('/') -> {
                        val divisor = parseUnary()
                        require(divisor != 0L) { "Division par zéro" }
                        if (value == Long.MIN_VALUE && divisor == -1L) throw overflow()
                        value /= divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseUnary(): Long {
            skipSpaces()
            return when {
                eat('+') -> parseUnary()
                eat('-') -> {
                    val afterMinus = position
                    if (base == NumberBase.DEC) {
                        skipSpaces()
                        val start = position
                        while (position < input.length && input[position].isDigit()) position++
                        if (position > start && input.substring(start, position) == "9223372036854775808") {
                            parsePower(Long.MIN_VALUE)
                        } else {
                            position = afterMinus
                            checkedOverflow { Math.negateExact(parseUnary()) }
                        }
                    } else {
                        checkedOverflow { Math.negateExact(parseUnary()) }
                    }
                }
                consumeWord("NOT") -> parseUnary().inv()
                else -> parsePower()
            }
        }

        private fun parsePower(initialBase: Long? = null): Long {
            val value = initialBase ?: parsePostfix()
            skipSpaces()
            return if (eat('^')) power(value, parseUnary()) else value
        }

        private fun parsePostfix(): Long {
            var value = parsePrimary()
            while (true) {
                skipSpaces()
                if (!eat('!')) return value
                require(value >= 0L) { "Factorielle invalide" }
                var factorial = 1L
                for (factor in 2L..value) {
                    factorial = checkedOverflow { Math.multiplyExact(factorial, factor) }
                }
                value = factorial
            }
        }

        private fun parsePrimary(): Long {
            skipSpaces()
            if (eat('(')) {
                val value = parseXor()
                skipSpaces()
                require(eat(')')) { "Parenthèse fermante manquante" }
                return value
            }

            if (consumeWord("POW")) {
                skipSpaces()
                require(eat('(')) { "Parenthèse ouvrante manquante" }
                val value = parseXor()
                skipSpaces()
                require(eat(',')) { "Séparateur d'arguments manquant" }
                val exponent = parseXor()
                skipSpaces()
                require(eat(')')) { "Parenthèse fermante manquante" }
                return power(value, exponent)
            }

            return parseNumber()
        }

        private fun parseNumber(): Long {
            val start = position
            while (position < input.length && base.accepts(input[position].uppercaseChar())) {
                position++
            }
            require(position > start) { "Nombre attendu" }

            val digits = input.substring(start, position).uppercase()
            val parsed = try {
                BigInteger(digits, base.radix)
            } catch (_: NumberFormatException) {
                throw IllegalArgumentException("Nombre invalide")
            }

            if (base == NumberBase.DEC) {
                return try {
                    parsed.longValueExact()
                } catch (_: ArithmeticException) {
                    throw overflow()
                }
            }

            require(parsed.bitLength() <= Long.SIZE_BITS) { throw overflow() }
            return parsed.toLong() // Values with bit 63 set are signed two's-complement values.
        }

        private fun consumeWord(word: String): Boolean {
            skipSpaces()
            if (!input.regionMatches(position, word, 0, word.length, ignoreCase = true)) return false
            val end = position + word.length
            if (end < input.length && input[end].isLetterOrDigit()) return false
            position = end
            return true
        }

        private fun power(value: Long, exponent: Long): Long {
            if (exponent < 0L) {
                if (value == 1L) return 1L
                if (value == -1L) return if (exponent and 1L == 0L) 1L else -1L
                if (value == 0L) throw ArithmeticException("Division par zéro")
                throw IllegalArgumentException("Résultat non entier")
            }

            var factor = value
            var remaining = exponent
            var result = 1L
            while (remaining > 0L) {
                if (remaining and 1L != 0L) {
                    result = checkedOverflow { Math.multiplyExact(result, factor) }
                }
                remaining = remaining ushr 1
                if (remaining > 0L) {
                    factor = checkedOverflow { Math.multiplyExact(factor, factor) }
                }
            }
            return result
        }

        private fun eat(character: Char): Boolean {
            if (position < input.length && input[position] == character) {
                position++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (position < input.length && input[position].isWhitespace()) position++
        }
    }

    private companion object {
        fun overflow(): ArithmeticException = ArithmeticException("Overflow")

        inline fun checkedOverflow(operation: () -> Long): Long = try {
            operation()
        } catch (_: ArithmeticException) {
            throw ArithmeticException("Overflow")
        }
    }
}
