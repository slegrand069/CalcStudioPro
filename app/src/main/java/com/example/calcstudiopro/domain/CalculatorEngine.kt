
package com.example.calcstudiopro.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.asin
import kotlin.math.acos
import kotlin.math.atan

class CalculatorEngine {

    fun evaluate(expression: String, angleUnit: AngleUnit = AngleUnit.DEGREES): Double {
        val normalized = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace(',', '.')
            .replace("π", "pi")

        val parser = Parser(normalized, angleUnit)
        return parser.parse()
    }

    private class Parser(
        private val input: String,
        private val angleUnit: AngleUnit
    ) {
        private var pos = 0

        fun parse(): Double {
            val result = parseExpression()
            skipSpaces()

            require(pos == input.length) {
                "Expression invalide"
            }
            require(result.isFinite()) {
                "Résultat non fini"
            }
            return result
        }

        // Addition et soustraction
        private fun parseExpression(): Double {
            var value = parseTerm()

            while (true) {
                skipSpaces()
                when {
                    eat('+') -> {
                        val start = pos
                        val right = parseTerm()
                        val rightText = input.substring(start, pos).trim()
                        value += if (rightText.endsWith("%")) {
                            value * right
                        } else {
                            right
                        }
                    }

                    eat('-') -> {
                        val start = pos
                        val right = parseTerm()
                        val rightText = input.substring(start, pos).trim()
                        value -= if (rightText.endsWith("%")) {
                            value * right
                        } else {
                            right
                        }
                    }

                    else -> return value
                }
            }
        }

        // Multiplication et division
        private fun parseTerm(): Double {
            var value = parseUnary()

            while (true) {
                skipSpaces()
                value = when {
                    eat('*') -> value * parseUnary()
                    eat('/') -> {
                        val divisor = parseUnary()
                        require(divisor != 0.0) {
                            "Division par zéro"
                        }
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        // Signes + et -
        private fun parseUnary(): Double {
            skipSpaces()
            return when {
                eat('+') -> parseUnary()
                eat('-') -> -parseUnary()
                else -> parsePower()
            }
        }

        // Puissance, associativité à droite
        private fun parsePower(): Double {
            val base = parsePostfix()
            skipSpaces()

            return if (eat('^')) {
                base.pow(parseUnary())
            } else {
                base
            }
        }

        // Factorielle et pourcentage postfixés
        private fun parsePostfix(): Double {
            var value = parsePrimary()

            while (true) {
                skipSpaces()
                when {
                    eat('!') -> {
                        require(value >= 0.0 &&
                                value <= 170.0 &&
                                value % 1.0 == 0.0) {
                            "Factorielle invalide"
                        }
                        var factorial = 1.0
                        for (i in 2..value.toInt()) {
                            factorial *= i
                        }
                        value = factorial
                    }

                    eat('%') -> value /= 100.0
                    else -> return value
                }
            }
        }

        // Nombres, constantes, fonctions et parenthèses
        private fun parsePrimary(): Double {
            skipSpaces()

            if (eat('(')) {
                val value = parseExpression()
                skipSpaces()
                require(eat(')')) {
                    "Parenthèse fermante manquante"
                }
                return value
            }

            if (pos < input.length && input[pos].isLetter()) {
                val start = pos
                while (pos < input.length && input[pos].isLetter()) {
                    pos++
                }
                val name = input.substring(start, pos)

                when (name) {
                    "pi" -> return PI
                    "e" -> return kotlin.math.E
                }

                skipSpaces()
                require(eat('(')) {
                    "Parenthèse ouvrante manquante"
                }
                val argument = parseExpression()
                skipSpaces()
                require(eat(')')) {
                    "Parenthèse fermante manquante"
                }

                return when (name) {
                    "ln" -> {
                        require(argument > 0.0) {
                            "Logarithme invalide"
                        }
                        ln(argument)
                    }

                    "log" -> {
                        require(argument > 0.0) {
                            "Logarithme invalide"
                        }
                        log10(argument)
                    }

                    "exp" -> exp(argument)

                    "sin" -> sin(angleUnit.toRadians(argument))
                    "cos" -> cos(angleUnit.toRadians(argument))
                    "tan" -> {
                        val radians = angleUnit.toRadians(argument)
                        require(kotlin.math.abs(cos(radians)) > 1e-12) {
                            "Tangente indéfinie"
                        }
                        tan(radians)
                    }


                    "asin" -> {
                        require(argument in -1.0..1.0) {
                            "Arcsinus invalide"
                        }
                        angleUnit.fromRadians(asin(argument))
                    }

                    "acos" -> {
                        require(argument in -1.0..1.0) {
                            "Arccosinus invalide"
                        }
                        angleUnit.fromRadians(acos(argument))
                    }

                    "atan" -> angleUnit.fromRadians(atan(argument))

                    else -> throw IllegalArgumentException(
                        "Fonction inconnue"
                    )
                }
            }

            return parseNumber()
        }

        private fun parseNumber(): Double {
            val start = pos
            var decimalFound = false

            while (pos < input.length) {
                val c = input[pos]
                when {
                    c.isDigit() -> pos++
                    c == '.' && !decimalFound -> {
                        decimalFound = true
                        pos++
                    }
                    else -> break
                }
            }

            require(pos > start) {
                "Nombre attendu"
            }

            val number = input.substring(start, pos)
            require(number != ".") {
                "Nombre invalide"
            }

            return number.toDouble()
        }

        private fun eat(char: Char): Boolean {
            if (pos < input.length && input[pos] == char) {
                pos++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }
    }
}