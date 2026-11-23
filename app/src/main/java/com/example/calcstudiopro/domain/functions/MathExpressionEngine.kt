package com.example.calcstudiopro.domain.functions

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cbrt
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Angle convention used only by the compiled-functions engine. */
enum class FunctionAngleMode { RAD, DEG }

/** A source location is a zero-based UTF-16 offset, suitable for Android text selections. */
sealed class MathExpressionError(open val position: Int?) {
    data class Syntax(override val position: Int?, val detail: String) : MathExpressionError(position)
    data class MissingParenthesis(override val position: Int?) : MathExpressionError(position)
    data class UnknownFunction(override val position: Int?, val name: String) : MathExpressionError(position)
    data class ArgumentCount(override val position: Int?, val name: String, val expected: String, val actual: Int) : MathExpressionError(position)
    data class InvalidVariable(override val position: Int?, val name: String) : MathExpressionError(position)
    data class Domain(override val position: Int?, val operation: String, val detail: String) : MathExpressionError(position)
    data class NonFiniteResult(override val position: Int?) : MathExpressionError(position)
}

sealed interface ExpressionCompilation {
    data class Success(val expression: CompiledMathExpression) : ExpressionCompilation
    data class Failure(val error: MathExpressionError) : ExpressionCompilation
}

sealed interface ExpressionEvaluation {
    data class Success(val value: Double) : ExpressionEvaluation
    data class Failure(val error: MathExpressionError) : ExpressionEvaluation
}

/**
 * Parses and compiles mathematical text to an immutable AST. Evaluation never reparses the source.
 * Unknown single letters are numeric parameters and default to 1.0 unless overridden.
 */
class MathExpressionEngine {
    fun compile(source: String): ExpressionCompilation = try {
        if (source.length > MAX_SOURCE_LENGTH) ExpressionCompilation.Failure(
            MathExpressionError.Syntax(MAX_SOURCE_LENGTH, "Expression trop longue")
        ) else ExpressionCompilation.Success(CompiledMathExpression(Parser(source).parse()))
    } catch (fault: ParseFault) {
        ExpressionCompilation.Failure(fault.error)
    } catch (fault: MathExpressionErrorException) {
        ExpressionCompilation.Failure(fault.error)
    } catch (_: StackOverflowError) {
        ExpressionCompilation.Failure(MathExpressionError.Syntax(null, "Expression trop complexe"))
    }

    private companion object { const val MAX_SOURCE_LENGTH = 4096 }
}

class CompiledMathExpression internal constructor(private val root: Node) {
    fun evaluate(
        x: Double,
        parameters: Map<Char, Double> = emptyMap(),
        angleMode: FunctionAngleMode = FunctionAngleMode.RAD
    ): ExpressionEvaluation = try {
        val normalizedParameters = parameters.entries.associate { it.key.lowercaseChar() to it.value }
        val value = root.evaluate(EvaluationContext(x, normalizedParameters, angleMode))
        if (value.isFinite()) ExpressionEvaluation.Success(value)
        else ExpressionEvaluation.Failure(MathExpressionError.NonFiniteResult(null))
    } catch (fault: MathExpressionErrorException) {
        ExpressionEvaluation.Failure(fault.error)
    } catch (_: ArithmeticException) {
        ExpressionEvaluation.Failure(MathExpressionError.NonFiniteResult(null))
    } catch (_: StackOverflowError) {
        ExpressionEvaluation.Failure(MathExpressionError.Syntax(null, "Expression trop complexe"))
    }
}

internal data class EvaluationContext(
    val x: Double,
    val parameters: Map<Char, Double>,
    val angleMode: FunctionAngleMode
)

internal sealed interface Node {
    val position: Int
    fun evaluate(context: EvaluationContext): Double
}

private data class NumberNode(val number: Double, override val position: Int) : Node {
    override fun evaluate(context: EvaluationContext) = number
}

private data class VariableNode(val name: Char, override val position: Int) : Node {
    override fun evaluate(context: EvaluationContext): Double {
        val value = when (name.lowercaseChar()) {
        'x' -> context.x
        't' -> fail(MathExpressionError.InvalidVariable(position, name.toString()))
        'e' -> kotlin.math.E
        'π' -> PI
        else -> context.parameters[name.lowercaseChar()] ?: 1.0
        }
        if (!value.isFinite()) fail(MathExpressionError.NonFiniteResult(position))
        return value
    }
}

private data class UnaryNode(val operator: Char, val operand: Node, override val position: Int) : Node {
    override fun evaluate(context: EvaluationContext): Double {
        val value = operand.evaluate(context)
        return if (operator == '-') -value else value
    }
}

private data class BinaryNode(val operator: Char, val left: Node, val right: Node, override val position: Int) : Node {
    override fun evaluate(context: EvaluationContext): Double {
        val a = left.evaluate(context)
        val b = right.evaluate(context)
        if ((operator == '/' || operator == '÷') && b == 0.0) {
            fail(MathExpressionError.Domain(position, "division", "Division par zéro"))
        }
        if (operator == '^' && a == 0.0 && b < 0.0) {
            fail(MathExpressionError.Domain(position, "power", "Zéro ne peut pas être élevé à un exposant négatif"))
        }
        if (operator == '^' && a < 0.0 && b % 1.0 != 0.0) {
            fail(MathExpressionError.Domain(position, "power", "Une base négative exige un exposant entier"))
        }
        return when (operator) {
            '+' -> a + b
            '-' -> a - b
            '*', '·' -> a * b
            '/' -> a / b
            '^' -> a.pow(b)
            else -> fail(MathExpressionError.Syntax(position, "Opérateur inconnu"))
        }.also { if (!it.isFinite()) fail(MathExpressionError.NonFiniteResult(position)) }
    }
}

private data class FunctionNode(
    val name: String,
    val arguments: List<Node>,
    override val position: Int
) : Node {
    override fun evaluate(context: EvaluationContext): Double {
        val values = arguments.map { it.evaluate(context) }
        fun arity(expected: Int) {
            if (values.size != expected) fail(MathExpressionError.ArgumentCount(position, name, expected.toString(), values.size))
        }
        fun angle(value: Double) = if (context.angleMode == FunctionAngleMode.DEG) Math.toRadians(value) else value
        fun outputAngle(value: Double) = if (context.angleMode == FunctionAngleMode.DEG) Math.toDegrees(value) else value
        val result = when (name) {
            "sin" -> { arity(1); sin(angle(values[0])) }
            "cos" -> { arity(1); cos(angle(values[0])) }
            "tan" -> {
                arity(1)
                val radians = angle(values[0])
                if (abs(cos(radians)) <= 1e-15) fail(MathExpressionError.Domain(position, name, "Tangente indéfinie"))
                tan(radians)
            }
            "asin" -> { arity(1); if (values[0] !in -1.0..1.0) fail(domain(name)); outputAngle(asin(values[0])) }
            "acos" -> { arity(1); if (values[0] !in -1.0..1.0) fail(domain(name)); outputAngle(acos(values[0])) }
            "atan" -> { arity(1); outputAngle(atan(values[0])) }
            "ln" -> { arity(1); if (values[0] <= 0.0) fail(domain(name)); ln(values[0]) }
            "log" -> { arity(1); if (values[0] <= 0.0) fail(domain(name)); log10(values[0]) }
            "exp" -> { arity(1); exp(values[0]) }
            "sqrt" -> { arity(1); if (values[0] < 0.0) fail(domain(name)); sqrt(values[0]) }
            "cbrt" -> { arity(1); cbrt(values[0]) }
            "abs" -> { arity(1); abs(values[0]) }
            "floor" -> { arity(1); floor(values[0]) }
            "ceil" -> { arity(1); ceil(values[0]) }
            "sign" -> { arity(1); sign(values[0]) }
            "pow" -> {
                arity(2)
                if (values[0] == 0.0 && values[1] < 0.0) fail(domain(name))
                if (values[0] < 0.0 && values[1] % 1.0 != 0.0) fail(domain(name))
                values[0].pow(values[1])
            }
            "root" -> {
                arity(2)
                val degree = values[0]
                val radicand = values[1]
                if (!degree.isFinite() || degree == 0.0 || degree % 1.0 != 0.0) fail(domain(name))
                if (radicand < 0.0 && abs(degree % 2.0) != 1.0) fail(domain(name))
                if (degree < 0.0 && radicand == 0.0) fail(domain(name))
                val magnitude = abs(radicand).pow(1.0 / abs(degree))
                val positiveDegreeRoot = if (radicand < 0.0) -magnitude else magnitude
                if (degree < 0.0) 1.0 / positiveDegreeRoot else positiveDegreeRoot
            }
            "min" -> { if (values.size < 2) fail(MathExpressionError.ArgumentCount(position, name, "au moins 2", values.size)); values.minOrNull()!! }
            "max" -> { if (values.size < 2) fail(MathExpressionError.ArgumentCount(position, name, "au moins 2", values.size)); values.maxOrNull()!! }
            else -> fail(MathExpressionError.UnknownFunction(position, name))
        }
        if (!result.isFinite()) fail(MathExpressionError.NonFiniteResult(position))
        return result
    }

    private fun domain(function: String) = MathExpressionError.Domain(position, function, "Argument hors domaine")
}

private enum class TokenType { NUMBER, LETTER, FUNCTION, PLUS, MINUS, STAR, SLASH, CARET, LEFT, RIGHT, SEMICOLON, END }
private data class Token(val type: TokenType, val text: String, val position: Int, val number: Double? = null)

private class ParseFault(val error: MathExpressionError) : RuntimeException()
private class MathExpressionErrorException(val error: MathExpressionError) : RuntimeException()
private fun fail(error: MathExpressionError): Nothing = throw MathExpressionErrorException(error)

private class Parser(private val source: String) {
    private val tokens = Lexer(source).scan()
    private var index = 0
    private val current get() = tokens[index]

    fun parse(): Node {
        if (current.type == TokenType.END) syntax("Expression vide", current.position)
        val result = expression()
        if (current.type != TokenType.END) syntax("Élément inattendu : ${current.text}", current.position)
        return result
    }

    private fun expression(): Node {
        var node = product()
        while (current.type == TokenType.PLUS || current.type == TokenType.MINUS) {
            val op = advance()
            node = BinaryNode(if (op.type == TokenType.PLUS) '+' else '-', node, product(), op.position)
        }
        return node
    }

    private fun product(): Node {
        var node = unary()
        while (true) {
            val token = current
            when (token.type) {
                TokenType.STAR, TokenType.SLASH -> {
                    advance()
                    node = BinaryNode(if (token.type == TokenType.STAR) '*' else '/', node, unary(), token.position)
                }
                else -> if (startsPrimary(token.type)) {
                    // Juxtaposition has the same precedence as explicit multiplication/division.
                    node = BinaryNode('·', node, unary(), token.position)
                } else return node
            }
        }
    }

    private fun unary(): Node = when (current.type) {
        TokenType.PLUS, TokenType.MINUS -> {
            val token = advance()
            UnaryNode(if (token.type == TokenType.MINUS) '-' else '+', unary(), token.position)
        }
        else -> power()
    }

    private fun power(): Node {
        val base = primary()
        if (current.type == TokenType.CARET) {
            val operator = advance()
            return BinaryNode('^', base, unary(), operator.position)
        }
        return base
    }

    private fun primary(): Node {
        val token = current
        return when (token.type) {
            TokenType.NUMBER -> { advance(); NumberNode(token.number!!, token.position) }
            TokenType.LETTER -> { advance(); VariableNode(token.text[0], token.position) }
            TokenType.LEFT -> {
                advance()
                val inner = expression()
                if (current.type != TokenType.RIGHT) throw ParseFault(MathExpressionError.MissingParenthesis(current.position))
                advance()
                inner
            }
            TokenType.FUNCTION -> parseFunction()
            TokenType.RIGHT -> throw ParseFault(MathExpressionError.MissingParenthesis(token.position))
            else -> syntax("Valeur attendue", token.position)
        }
    }

    private fun parseFunction(): Node {
        val function = advance()
        if (current.type != TokenType.LEFT) syntax("Parenthèse ouvrante attendue après ${function.text}", current.position)
        advance()
        val args = mutableListOf<Node>()
        if (current.type != TokenType.RIGHT) {
            if (current.type == TokenType.END) {
                throw ParseFault(MathExpressionError.MissingParenthesis(current.position))
            }
            args += expression()
            while (current.type == TokenType.SEMICOLON) {
                advance()
                if (current.type == TokenType.END) {
                    throw ParseFault(MathExpressionError.MissingParenthesis(current.position))
                }
                args += expression()
            }
        }
        if (current.type != TokenType.RIGHT) throw ParseFault(MathExpressionError.MissingParenthesis(current.position))
        advance()
        return FunctionNode(function.text, args, function.position)
    }

    private fun startsPrimary(type: TokenType) = type == TokenType.NUMBER || type == TokenType.LETTER || type == TokenType.FUNCTION || type == TokenType.LEFT
    private fun advance(): Token = tokens[index++]
    private fun syntax(detail: String, position: Int): Nothing = throw ParseFault(MathExpressionError.Syntax(position, detail))
}

private class Lexer(private val source: String) {
    private val functions = setOf("asin", "acos", "atan", "sqrt", "cbrt", "floor", "ceil", "sign", "sin", "cos", "tan", "min", "max", "root", "pow", "log", "ln", "exp", "abs")
    private val tokens = ArrayList<Token>()
    private var position = 0

    fun scan(): List<Token> {
        while (position < source.length) {
            val c = source[position]
            when {
                c.isWhitespace() -> position++
                c.isDigit() || c == '.' || c == ',' -> readNumber()
                c.isLetter() -> readLetterOrFunction()
                else -> {
                    val type = when (c) {
                        '+' -> TokenType.PLUS
                        '-', '−' -> TokenType.MINUS
                        '*', '×' -> TokenType.STAR
                        '/', '÷' -> TokenType.SLASH
                        '^' -> TokenType.CARET
                        '(' -> TokenType.LEFT
                        ')' -> TokenType.RIGHT
                        ';' -> TokenType.SEMICOLON
                        else -> throw ParseFault(MathExpressionError.Syntax(position, "Caractère invalide : $c"))
                    }
                    tokens += Token(type, c.toString(), position++)
                }
            }
        }
        tokens += Token(TokenType.END, "", source.length)
        return tokens
    }

    private fun readNumber() {
        val start = position
        var separator = false
        while (position < source.length) {
            val c = source[position]
            if (c.isDigit()) position++
            else if ((c == '.' || c == ',') && !separator) { separator = true; position++ }
            else break
        }
        val text = source.substring(start, position)
        val number = text.replace(',', '.').toDoubleOrNull()
            ?: throw ParseFault(MathExpressionError.Syntax(start, "Nombre invalide"))
        tokens += Token(TokenType.NUMBER, text, start, number)
    }

    private fun readLetterOrFunction() {
        val start = position
        val lowerSource = source.lowercase()
        if (lowerSource.startsWith("pi", position)) {
            tokens += Token(TokenType.NUMBER, source.substring(position, position + 2), position, PI)
            position += 2
            return
        }
        val runEnd = generateSequence(position) { current ->
            (current + 1).takeIf { it < source.length && source[it].isLetter() }
        }.last()
        var afterRun = runEnd + 1
        while (afterRun < source.length && source[afterRun].isWhitespace()) afterRun++
        if (afterRun < source.length && source[afterRun] == '(') {
            val run = lowerSource.substring(start, runEnd + 1)
            val containsKnownFunction = functions.any(run::contains)
            if (run.length > 1 && !containsKnownFunction) {
                throw ParseFault(MathExpressionError.UnknownFunction(start, source.substring(start, runEnd + 1)))
            }
        }
        val function = functions.sortedByDescending(String::length).firstOrNull { candidate ->
            lowerSource.startsWith(candidate, position) && {
                var end = position + candidate.length
                while (end < source.length && source[end].isWhitespace()) end++
                end < source.length && source[end] == '('
            }()
        }
        if (function != null) {
            tokens += Token(TokenType.FUNCTION, function, start)
            position += function.length
        } else {
            val letter = source[position++]
            if (letter.lowercaseChar() == 't') throw ParseFault(MathExpressionError.InvalidVariable(start, letter.toString()))
            tokens += Token(TokenType.LETTER, letter.toString(), start)
        }
    }
}
