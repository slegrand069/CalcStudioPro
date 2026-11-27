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
import java.util.Collections

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

/** Immutable structural facts collected from the parsed AST, not inferred from sampled values. */
class CompiledExpressionMetadata internal constructor(
    val dependsOnX: Boolean,
    val parameterDependencies: Set<Char>,
    val dependsOnAngleMode: Boolean
)

/** A typed derivative component. Only [Value] can contain a numeric derivative. */
sealed interface DerivativeEvaluation {
    data class Value(val value: Double) : DerivativeEvaluation {
        init { require(value.isFinite()) }
    }
    data class Failure(val reason: DerivativeFailure) : DerivativeEvaluation
}

sealed interface DerivativeFailure {
    data class FunctionOutOfDomain(val error: MathExpressionError) : DerivativeFailure
    data class NotDefined(val detail: String) : DerivativeFailure
    data class NonFinite(val detail: String) : DerivativeFailure
    data class Unsupported(val operation: String, val detail: String) : DerivativeFailure
    data class Indeterminate(val detail: String) : DerivativeFailure
    data class NumericallyUnstable(val detail: String) : DerivativeFailure
}

/** Value and derivatives have independent statuses; no non-finite Double is exposed as valid. */
data class DifferentiatedExpressionEvaluation(
    val value: ExpressionEvaluation,
    val firstDerivative: DerivativeEvaluation,
    val secondDerivative: DerivativeEvaluation,
    val metadata: CompiledExpressionMetadata,
    /** Diagnostic for a finite scalar produced by a likely underflow; scalar evaluation is unchanged. */
    val valueDiagnostic: DerivativeFailure? = null
)

class CompiledMathExpression internal constructor(private val root: Node) {
    val metadata: CompiledExpressionMetadata = root.metadata.toPublic()
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

    /** Evaluates the existing compiled AST once for its value and propagates a second-order jet. */
    fun evaluateDifferentiated(
        x: Double,
        parameters: Map<Char, Double> = emptyMap(),
        angleMode: FunctionAngleMode = FunctionAngleMode.RAD
    ): DifferentiatedExpressionEvaluation {
        val normalizedParameters = parameters.entries.associate { it.key.lowercaseChar() to it.value }
        val context = EvaluationContext(x, normalizedParameters, angleMode)
        val value = evaluate(x, parameters, angleMode)
        if (value is ExpressionEvaluation.Failure) {
            val failed = DerivativeEvaluation.Failure(derivativeFailureFor(value.error))
            return DifferentiatedExpressionEvaluation(value, failed, failed, metadata)
        }
        val jet = try {
            root.differentiate(context)
        } catch (fault: MathExpressionErrorException) {
            val failed = DerivativeEvaluation.Failure(derivativeFailureFor(fault.error))
            return DifferentiatedExpressionEvaluation(value, failed, failed, metadata)
        } catch (_: ArithmeticException) {
            val failed = DerivativeEvaluation.Failure(DerivativeFailure.NonFinite("Débordement pendant la différentiation"))
            return DifferentiatedExpressionEvaluation(value, failed, failed, metadata)
        } catch (_: StackOverflowError) {
            val failed = DerivativeEvaluation.Failure(DerivativeFailure.Unsupported("expression", "Expression trop complexe"))
            return DifferentiatedExpressionEvaluation(value, failed, failed, metadata)
        }
        return DifferentiatedExpressionEvaluation(
            value, jet.first.toPublic(), jet.second.toPublic(), metadata,
            jet.valueDiagnostic
        )
    }

    private fun derivativeFailureFor(error: MathExpressionError): DerivativeFailure = when (error) {
        is MathExpressionError.Domain -> DerivativeFailure.FunctionOutOfDomain(error)
        is MathExpressionError.NonFiniteResult -> DerivativeFailure.NonFinite("La fonction n’est pas représentable en Double")
        else -> DerivativeFailure.Unsupported("évaluation", error.toString())
    }
}

internal data class EvaluationContext(
    val x: Double,
    val parameters: Map<Char, Double>,
    val angleMode: FunctionAngleMode
)

internal data class AstMetadata(
    val dependsOnX: Boolean,
    val parameterDependencies: Set<Char>,
    val dependsOnAngleMode: Boolean
) {
    fun toPublic() = CompiledExpressionMetadata(
        dependsOnX,
        Collections.unmodifiableSet(LinkedHashSet(parameterDependencies)),
        dependsOnAngleMode
    )
}

internal sealed interface Node {
    val position: Int
    val metadata: AstMetadata
    fun evaluate(context: EvaluationContext): Double
    fun differentiate(context: EvaluationContext): Jet
}

private data class NumberNode(val number: Double, override val position: Int) : Node {
    override val metadata = AstMetadata(false, emptySet(), false)
    override fun evaluate(context: EvaluationContext) = number
    override fun differentiate(context: EvaluationContext) = Jet.constant(number)
}

private data class VariableNode(val name: Char, override val position: Int) : Node {
    override val metadata = when (val normalized = name.lowercaseChar()) {
        'x' -> AstMetadata(true, emptySet(), false)
        'e', 'π', 't' -> AstMetadata(false, emptySet(), false)
        else -> AstMetadata(false, setOf(normalized), false)
    }
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
    override fun differentiate(context: EvaluationContext): Jet = when (name.lowercaseChar()) {
        'x' -> Jet.variable(context.x)
        't' -> fail(MathExpressionError.InvalidVariable(position, name.toString()))
        'e' -> Jet.constant(kotlin.math.E)
        'π' -> Jet.constant(PI)
        else -> Jet.constant(context.parameters[name.lowercaseChar()] ?: 1.0)
    }
}

private data class UnaryNode(val operator: Char, val operand: Node, override val position: Int) : Node {
    override val metadata = operand.metadata
    override fun evaluate(context: EvaluationContext): Double {
        val value = operand.evaluate(context)
        return if (operator == '-') -value else value
    }
    override fun differentiate(context: EvaluationContext): Jet {
        val value = operand.differentiate(context)
        if (operator == '+') return value
        return Jet(-value.value, value.first.negate(), value.second.negate(), value.valueDiagnostic)
    }
}

private data class BinaryNode(val operator: Char, val left: Node, val right: Node, override val position: Int) : Node {
    override val metadata = combineMetadata(left.metadata, right.metadata)
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
    override fun differentiate(context: EvaluationContext): Jet {
        val a = left.differentiate(context)
        val b = right.differentiate(context)
        val result = when (operator) {
            '+' -> Jet(a.value + b.value, a.first.add(b.first), a.second.add(b.second))
            '-' -> Jet(a.value - b.value, a.first.subtract(b.first), a.second.subtract(b.second))
            '*', '·' -> productJet(a, b)
            '/', '÷' -> quotientJet(a, b)
            '^' -> powerJet(a, b, right.metadata.dependsOnX)
            else -> fail(MathExpressionError.Syntax(position, "Opérateur inconnu"))
        }
        val localUnderflow = result.value == 0.0 && a.value != 0.0 && b.value != 0.0 && operator in setOf('*', '·', '/', '^')
        return result.copy(valueDiagnostic = a.valueDiagnostic ?: b.valueDiagnostic ?: if (localUnderflow)
            DerivativeFailure.NumericallyUnstable("Sous-flux possible dans l’opération $operator") else result.valueDiagnostic)
    }
}

private data class FunctionNode(
    val name: String,
    val arguments: List<Node>,
    override val position: Int
) : Node {
    override val metadata: AstMetadata = combineMetadata(arguments.map { it.metadata }).let { combined ->
        combined.copy(dependsOnAngleMode = combined.dependsOnAngleMode || name in ANGLE_FUNCTIONS)
    }
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

    override fun differentiate(context: EvaluationContext): Jet {
        val args = arguments.map { it.differentiate(context) }
        val value = evaluate(context)
        if (!metadata.dependsOnX) {
            return Jet.constant(value).copy(valueDiagnostic = args.firstNotNullOfOrNull { it.valueDiagnostic }
                ?: functionValueDiagnostic(name, args.map { it.value }, value))
        }
        if (name == "min" || name == "max") return minMaxJet(name, args).copy(
            valueDiagnostic = args.firstNotNullOfOrNull { it.valueDiagnostic }
        )
        val u = args.firstOrNull() ?: return fail(MathExpressionError.ArgumentCount(position, name, "1", 0))
        val angleScale = if (context.angleMode == FunctionAngleMode.DEG) PI / 180.0 else 1.0
        val outputScale = if (context.angleMode == FunctionAngleMode.DEG) 180.0 / PI else 1.0
        fun outputAngle(value: Double) = if (context.angleMode == FunctionAngleMode.DEG) Math.toDegrees(value) else value
        val result = when (name) {
            "sin" -> smoothUnary(u, sin(if (context.angleMode == FunctionAngleMode.DEG) Math.toRadians(u.value) else u.value),
                cos(if (context.angleMode == FunctionAngleMode.DEG) Math.toRadians(u.value) else u.value) * angleScale,
                -sin(if (context.angleMode == FunctionAngleMode.DEG) Math.toRadians(u.value) else u.value) * angleScale * angleScale)
            "cos" -> smoothUnary(u, cos(if (context.angleMode == FunctionAngleMode.DEG) Math.toRadians(u.value) else u.value),
                -sin(if (context.angleMode == FunctionAngleMode.DEG) Math.toRadians(u.value) else u.value) * angleScale,
                -cos(if (context.angleMode == FunctionAngleMode.DEG) Math.toRadians(u.value) else u.value) * angleScale * angleScale)
            "tan" -> {
                val radians = if (context.angleMode == FunctionAngleMode.DEG) Math.toRadians(u.value) else u.value
                val c = cos(radians)
                if (abs(c) <= 1e-15) return nonsmoothValue(tan(radians), "tan", "Pôle de la tangente")
                val sec2 = 1.0 / (c * c)
                smoothUnary(u, tan(radians), sec2 * angleScale,
                    2.0 * tan(radians) * sec2 * angleScale * angleScale)
            }
            "asin" -> {
                if (u.value !in -1.0..1.0) fail(domain(name))
                val denominator = 1.0 - u.value * u.value
                if (denominator <= 0.0) return nonsmoothValue(outputAngle(asin(u.value)), name, "Dérivée singulière à la limite du domaine")
                val root = sqrt(denominator)
                smoothUnary(u, outputAngle(asin(u.value)), outputScale / root,
                    outputScale * u.value / (denominator * root))
            }
            "acos" -> {
                if (u.value !in -1.0..1.0) fail(domain(name))
                val denominator = 1.0 - u.value * u.value
                if (denominator <= 0.0) return nonsmoothValue(outputAngle(acos(u.value)), name, "Dérivée singulière à la limite du domaine")
                val root = sqrt(denominator)
                smoothUnary(u, outputAngle(acos(u.value)), -outputScale / root,
                    -outputScale * u.value / (denominator * root))
            }
            "atan" -> smoothUnary(u, outputAngle(atan(u.value)), outputScale / (1.0 + u.value * u.value),
                -2.0 * outputScale * u.value / ((1.0 + u.value * u.value) * (1.0 + u.value * u.value)))
            "exp" -> {
                val v = exp(u.value)
                if (v == 0.0 && u.value < 0.0) {
                    val issue = JetComponent.Invalid(DerivativeFailure.NumericallyUnstable("Sous-flux de exp"))
                    Jet(v, issue, issue, DerivativeFailure.NumericallyUnstable("Sous-flux de exp"))
                } else smoothUnary(u, v, v, v)
            }
            "ln" -> { if (u.value <= 0.0) fail(domain(name)); smoothUnary(u, ln(u.value), 1.0 / u.value, -1.0 / (u.value * u.value)) }
            "log" -> { if (u.value <= 0.0) fail(domain(name)); smoothUnary(u, log10(u.value), 1.0 / (u.value * ln(10.0)), -1.0 / (u.value * u.value * ln(10.0))) }
            "sqrt" -> {
                if (u.value < 0.0) fail(domain(name))
                val v = sqrt(u.value)
                if (u.value == 0.0) sqrtBoundaryJet(u, v) else smoothUnary(u, v, 0.5 / v, -0.25 / (u.value * v))
            }
            "cbrt" -> rootJet(u, cbrt(u.value), 3.0)
            "abs" -> absJet(u)
            "floor" -> discreteJet(u, floor(u.value), "floor")
            "ceil" -> discreteJet(u, ceil(u.value), "ceil")
            "sign" -> discreteJet(u, sign(u.value), "sign")
            "pow" -> {
                val v = args.getOrNull(1) ?: fail(MathExpressionError.ArgumentCount(position, name, "2", args.size))
                powerJet(u, v, arguments[1].metadata.dependsOnX)
            }
            "root" -> {
                val degree = args.getOrNull(0) ?: fail(MathExpressionError.ArgumentCount(position, name, "2", args.size))
                val radicand = args[1]
                if (degree.value == 0.0 || degree.value % 1.0 != 0.0) fail(domain(name))
                if (arguments[0].metadata.dependsOnX) return unsupportedJet(value, name,
                    "Ordre de racine dépendant de x").copy(value = value,
                    valueDiagnostic = args.firstNotNullOfOrNull { it.valueDiagnostic })
                rootJet(radicand, rootValue(degree.value, radicand.value), degree.value)
            }
            else -> fail(MathExpressionError.UnknownFunction(position, name))
        }
        val argsDiagnostic = args.firstNotNullOfOrNull { it.valueDiagnostic }
        return result.copy(valueDiagnostic = argsDiagnostic ?: result.valueDiagnostic
            ?: functionValueDiagnostic(name, args.map { it.value }, result.value))
    }

    private fun domain(function: String) = MathExpressionError.Domain(position, function, "Argument hors domaine")

    private fun rootValue(degree: Double, radicand: Double): Double {
        val magnitude = abs(radicand).pow(1.0 / abs(degree))
        val positiveRoot = if (radicand < 0.0) -magnitude else magnitude
        return if (degree < 0.0) 1.0 / positiveRoot else positiveRoot
    }

    private fun unsupportedJet(value: Double, operation: String, detail: String) = Jet(
        value, JetComponent.Invalid(DerivativeFailure.Unsupported(operation, detail)),
        JetComponent.Invalid(DerivativeFailure.Unsupported(operation, detail))
    )

    private fun nonsmoothValue(value: Double, operation: String, detail: String) = Jet(
        value, JetComponent.Invalid(DerivativeFailure.Indeterminate("$operation : $detail")),
        JetComponent.Invalid(DerivativeFailure.Indeterminate("$operation : $detail"))
    )
}

internal sealed interface JetComponent {
    data class Finite(val value: Double) : JetComponent
    data class Invalid(val failure: DerivativeFailure) : JetComponent

    fun toPublic(): DerivativeEvaluation = when (this) {
        is Finite -> if (value.isFinite()) DerivativeEvaluation.Value(value)
            else DerivativeEvaluation.Failure(DerivativeFailure.NonFinite("Dérivée non représentable en Double"))
        is Invalid -> DerivativeEvaluation.Failure(failure)
    }

    fun negate() = mapComponent(this) { -it }
    fun add(other: JetComponent) = combineComponents(this, other) { a, b -> a + b }
    fun subtract(other: JetComponent) = combineComponents(this, other) { a, b -> a - b }
}

internal data class Jet(
    val value: Double,
    val first: JetComponent,
    val second: JetComponent,
    val valueDiagnostic: DerivativeFailure? = null
) {
    companion object {
        fun constant(value: Double) = Jet(value, JetComponent.Finite(0.0), JetComponent.Finite(0.0))
        fun variable(value: Double) = Jet(value, JetComponent.Finite(1.0), JetComponent.Finite(0.0))
    }
}

private fun component(value: Double): JetComponent = if (value.isFinite()) JetComponent.Finite(value)
else JetComponent.Invalid(DerivativeFailure.NonFinite("Dérivée intermédiaire non finie"))

private fun mapComponent(value: JetComponent, operation: (Double) -> Double): JetComponent = when (value) {
    is JetComponent.Finite -> component(operation(value.value))
    is JetComponent.Invalid -> value
}

private fun combineComponents(a: JetComponent, b: JetComponent, operation: (Double, Double) -> Double): JetComponent = when {
    a is JetComponent.Invalid -> a.asComposedFailure()
    b is JetComponent.Invalid -> b.asComposedFailure()
    a is JetComponent.Finite && !a.value.isFinite() -> JetComponent.Invalid(DerivativeFailure.NonFinite("Dérivée intermédiaire non finie"))
    b is JetComponent.Finite && !b.value.isFinite() -> JetComponent.Invalid(DerivativeFailure.NonFinite("Dérivée intermédiaire non finie"))
    else -> component(operation((a as JetComponent.Finite).value, (b as JetComponent.Finite).value))
}

private fun productComponent(a: JetComponent, b: JetComponent): JetComponent = when {
    a is JetComponent.Finite && a.value.isFinite() && a.value == 0.0 && b is JetComponent.Finite && b.value.isFinite() -> JetComponent.Finite(0.0)
    b is JetComponent.Finite && b.value.isFinite() && b.value == 0.0 && a is JetComponent.Finite && a.value.isFinite() -> JetComponent.Finite(0.0)
    a is JetComponent.Finite && a.value == 0.0 && b is JetComponent.Invalid -> b.asComposedFailure()
    b is JetComponent.Finite && b.value == 0.0 && a is JetComponent.Invalid -> a.asComposedFailure()
    a is JetComponent.Invalid -> a.asComposedFailure()
    b is JetComponent.Invalid -> b.asComposedFailure()
    else -> {
        val av = (a as JetComponent.Finite).value
        val bv = (b as JetComponent.Finite).value
        if (!av.isFinite() || !bv.isFinite()) returnComponentNonFinite()
        else {
        val product = av * bv
        if (product == 0.0 && av != 0.0 && bv != 0.0)
            JetComponent.Invalid(DerivativeFailure.NumericallyUnstable("Sous-flux dans une dérivée"))
        else component(product)
        }
    }
}

private fun returnComponentNonFinite() = JetComponent.Invalid(DerivativeFailure.NonFinite("Dérivée intermédiaire non finie"))

private fun sumComponents(vararg terms: JetComponent): JetComponent {
    var sum = JetComponent.Finite(0.0) as JetComponent
    terms.forEach { sum = combineComponents(sum, it) { a, b -> a + b } }
    return sum
}

private fun JetComponent.asComposedFailure(): JetComponent = when (this) {
    is JetComponent.Invalid -> if (failure is DerivativeFailure.NotDefined)
        JetComponent.Invalid(DerivativeFailure.Indeterminate("Une sous-expression non dérivable est composée avec une autre opération"))
    else this
    is JetComponent.Finite -> this
}

private fun productJet(a: Jet, b: Jet): Jet = Jet(
    a.value * b.value,
    sumComponents(productComponent(a.first, JetComponent.Finite(b.value)), productComponent(JetComponent.Finite(a.value), b.first)),
    sumComponents(
        productComponent(a.second, JetComponent.Finite(b.value)),
        productComponent(productComponent(JetComponent.Finite(2.0), a.first), b.first),
        productComponent(JetComponent.Finite(a.value), b.second)
    )
)

private fun quotientJet(a: Jet, b: Jet): Jet {
    val value = a.value / b.value
    if (!value.isFinite()) {
        val failure = DerivativeFailure.NonFinite("Division non représentable en Double")
        return Jet(value, JetComponent.Invalid(failure), JetComponent.Invalid(failure))
    }
    val firstNumerator = a.first.subtract(productComponent(JetComponent.Finite(value), b.first))
    val first = combineComponents(firstNumerator, JetComponent.Finite(b.value)) { n, d -> n / d }
    val secondNumerator = a.second.subtract(
        sumComponents(
            productComponent(JetComponent.Finite(2.0), productComponent(first, b.first)),
            productComponent(JetComponent.Finite(value), b.second)
        )
    )
    val second = combineComponents(secondNumerator, JetComponent.Finite(b.value)) { n, d -> n / d }
    return Jet(value, first, second)
}

private fun powerJet(base: Jet, exponent: Jet, exponentDependsOnX: Boolean): Jet {
    val value = base.value.pow(exponent.value)
    if (!exponentDependsOnX) {
        val p = exponent.value
        if (p == 0.0) return Jet(value, JetComponent.Finite(0.0), JetComponent.Finite(0.0))
        if (p == 1.0) return Jet(base.value, base.first, base.second)
        if (base.value == 0.0 && p.isFinite() && p % 1.0 == 0.0) {
            val baseJetsKnown = base.first is JetComponent.Finite && base.second is JetComponent.Finite &&
                (base.first as JetComponent.Finite).value.isFinite() && (base.second as JetComponent.Finite).value.isFinite()
            if (p >= 2.0 && !baseJetsKnown) {
                val issue = JetComponent.Invalid(DerivativeFailure.Indeterminate("Puissance en base nulle composée avec une base insuffisamment différentiable"))
                return Jet(value, issue, issue)
            }
            val first: JetComponent = if (p >= 2.0) JetComponent.Finite(0.0)
                else JetComponent.Invalid(DerivativeFailure.NotDefined("Puissance non dérivable en base nulle"))
            val second: JetComponent = when {
                p == 2.0 -> productComponent(JetComponent.Finite(2.0), productComponent(base.first, base.first))
                p > 2.0 -> JetComponent.Finite(0.0)
                else -> JetComponent.Invalid(DerivativeFailure.NotDefined("Dérivée seconde non définie en base nulle"))
            }
            return Jet(value, first, second)
        }
        if (base.value == 0.0) return Jet(value,
            JetComponent.Invalid(DerivativeFailure.Indeterminate("Puissance fractionnaire en base nulle")),
            JetComponent.Invalid(DerivativeFailure.Indeterminate("Puissance fractionnaire en base nulle")))
        val firstFactor = p * base.value.pow(p - 1.0)
        val secondFactor = p * (p - 1.0) * base.value.pow(p - 2.0)
        val first = productComponent(JetComponent.Finite(firstFactor), base.first)
        val second = sumComponents(
            productComponent(JetComponent.Finite(secondFactor), productComponent(base.first, base.first)),
            productComponent(JetComponent.Finite(firstFactor), base.second)
        )
        return Jet(value, first, second)
    }
    if (base.value <= 0.0 || !base.value.isFinite()) {
        val issue = JetComponent.Invalid(DerivativeFailure.Indeterminate(
            "Puissance à exposant dépendant de x hors du domaine positif régulier"))
        return Jet(value, issue, issue)
    }
    val logBase = ln(base.value)
    val logFirst = sumComponents(
        productComponent(exponent.first, JetComponent.Finite(logBase)),
        combineComponents(exponent.value.let { JetComponent.Finite(it) }, base.first) { v, d -> v * d / base.value }
    )
    val logSecond = sumComponents(
        productComponent(exponent.second, JetComponent.Finite(logBase)),
        combineComponents(exponent.first, base.first) { ev, bv -> 2.0 * ev * bv / base.value },
        combineComponents(exponent.value.let { JetComponent.Finite(it) }, base.second) { v, d -> v * d / base.value },
        combineComponents(exponent.value.let { JetComponent.Finite(it) }, base.first) { v, d -> -v * d * d / (base.value * base.value) }
    )
    val first = productComponent(JetComponent.Finite(value), logFirst)
    val second = productComponent(JetComponent.Finite(value), sumComponents(
        productComponent(logFirst, logFirst), logSecond
    ))
    return Jet(value, first, second)
}

private fun smoothUnary(input: Jet, value: Double, firstRule: Double, secondRule: Double): Jet {
    val first = productComponent(JetComponent.Finite(firstRule), input.first)
    val second = sumComponents(
        productComponent(JetComponent.Finite(secondRule), productComponent(input.first, input.first)),
        productComponent(JetComponent.Finite(firstRule), input.second)
    )
    return Jet(value, first, second)
}

private fun sqrtBoundaryJet(input: Jet, value: Double): Jet {
    val inputFirst = (input.first as? JetComponent.Finite)?.value
    val inputSecond = (input.second as? JetComponent.Finite)?.value
    val cuspFromQuadratic = inputFirst == 0.0 && inputSecond != null && inputSecond > 0.0
    val firstIssue = if (inputFirst?.let { it != 0.0 } == true || cuspFromQuadratic)
        DerivativeFailure.NotDefined("sqrt a une pente non finie à la frontière du domaine")
    else DerivativeFailure.Indeterminate("sqrt à la frontière du domaine")
    val first = JetComponent.Invalid(firstIssue)
    val second = JetComponent.Invalid(if (cuspFromQuadratic)
        DerivativeFailure.NotDefined("sqrt(x) présente un point anguleux à la frontière")
    else DerivativeFailure.Indeterminate("Dérivée seconde de sqrt indéterminée à la frontière du domaine"))
    return Jet(value, first, second)
}

private fun rootJet(input: Jet, value: Double, degree: Double): Jet {
    if (degree == 1.0) return Jet(value, input.first, input.second)
    if (value == 0.0) {
        val issue = JetComponent.Invalid(DerivativeFailure.Indeterminate("Racine singulière en zéro"))
        return Jet(value, issue, issue)
    }
    val firstDenominator = degree * value.pow(degree - 1.0)
    val first = combineComponents(input.first, JetComponent.Finite(firstDenominator)) { y1, d -> y1 / d }
    val second = when {
        first is JetComponent.Invalid -> first.asComposedFailure()
        input.second is JetComponent.Invalid -> input.second.asComposedFailure()
        else -> component(
            ((input.second as JetComponent.Finite).value - degree * (degree - 1.0) *
                value.pow(degree - 2.0) * (first as JetComponent.Finite).value * (first as JetComponent.Finite).value) /
                firstDenominator
        )
    }
    return Jet(value, first, second)
}

private fun absJet(input: Jet): Jet = when {
    input.value > 0.0 -> input
    input.value < 0.0 -> Jet(-input.value, input.first.negate(), input.second.negate())
    else -> {
        val first = when (val d1 = input.first) {
            is JetComponent.Finite -> if (d1.value == 0.0) JetComponent.Finite(0.0)
                else JetComponent.Invalid(DerivativeFailure.NotDefined("abs non dérivable en zéro"))
            is JetComponent.Invalid -> d1
        }
        val second = when {
            first is JetComponent.Invalid -> first
            input.second is JetComponent.Finite -> JetComponent.Finite(kotlin.math.abs((input.second as JetComponent.Finite).value))
            else -> input.second
        }
        Jet(0.0, first, second)
    }
}

private fun discreteJet(input: Jet, value: Double, operation: String): Jet {
    if (operation == "sign" && input.value != 0.0) return Jet(value, JetComponent.Finite(0.0), JetComponent.Finite(0.0))
    if (operation != "sign" && input.value % 1.0 != 0.0) return Jet(value, JetComponent.Finite(0.0), JetComponent.Finite(0.0))
    val d1 = input.first as? JetComponent.Finite
    val d2 = input.second as? JetComponent.Finite
    if (d1?.value == 0.0 && d2 != null) {
        val stableSide = when (operation) {
            "floor" -> d2.value > 0.0
            "ceil" -> d2.value < 0.0
            else -> false
        }
        if (stableSide) return Jet(value, JetComponent.Finite(0.0), JetComponent.Finite(0.0))
        val oppositeSideIsDiscontinuous = when (operation) {
            "floor" -> d2.value < 0.0
            "ceil" -> d2.value > 0.0
            "sign" -> d2.value != 0.0
            else -> false
        }
        if (oppositeSideIsDiscontinuous) {
            val failure = JetComponent.Invalid(DerivativeFailure.NotDefined("$operation est discontinu au seuil"))
            return Jet(value, failure, failure)
        }
    }
    val failure = if (d1 != null && d1.value != 0.0) {
        DerivativeFailure.NotDefined("$operation au seuil")
    } else DerivativeFailure.Indeterminate("$operation au seuil : le jet d'ordre deux ne suffit pas")
    return Jet(value, JetComponent.Invalid(failure), JetComponent.Invalid(failure))
}

private fun minMaxJet(operation: String, args: List<Jet>): Jet {
    val target = if (operation == "min") args.minOf { it.value } else args.maxOf { it.value }
    val winners = args.filter { it.value == target }
    if (winners.size == 1) return winners.single()
    val firstValues = winners.map { it.first as? JetComponent.Finite }
    val first = when {
        firstValues.any { it == null } -> JetComponent.Invalid(DerivativeFailure.Indeterminate("Dérivée de branche inconnue pour $operation"))
        firstValues.all { it!!.value == firstValues.first()!!.value } -> firstValues.first()!!
        else -> JetComponent.Invalid(DerivativeFailure.NotDefined("$operation change de branche au point"))
    }
    val secondValues = winners.map { it.second as? JetComponent.Finite }
    val second = when {
        first is JetComponent.Invalid -> first
        secondValues.any { it == null || !it.value.isFinite() } -> JetComponent.Invalid(DerivativeFailure.Indeterminate("Dérivée seconde de branche inconnue pour $operation"))
        else -> JetComponent.Finite(if (operation == "min") secondValues.minOf { it!!.value } else secondValues.maxOf { it!!.value })
    }
    return Jet(target, first, second)
}

private fun combineMetadata(a: AstMetadata, b: AstMetadata) = combineMetadata(listOf(a, b))
private fun combineMetadata(values: List<AstMetadata>): AstMetadata {
    val params = LinkedHashSet<Char>()
    values.forEach { params.addAll(it.parameterDependencies) }
    return AstMetadata(
        dependsOnX = values.any { it.dependsOnX },
        parameterDependencies = Collections.unmodifiableSet(params),
        dependsOnAngleMode = values.any { it.dependsOnAngleMode }
    )
}

private fun functionValueDiagnostic(name: String, arguments: List<Double>, result: Double): DerivativeFailure? = when {
    name == "exp" && arguments.firstOrNull()?.let { it < 0.0 } == true && result == 0.0 ->
        DerivativeFailure.NumericallyUnstable("Sous-flux de exp : valeur mathématique positive mais non représentable")
    name in setOf("root", "cbrt") && arguments.any { it != 0.0 } && result == 0.0 ->
        DerivativeFailure.NumericallyUnstable("Sous-flux possible dans la racine $name")
    name == "pow" && arguments.size == 2 && arguments[0] != 0.0 && result == 0.0 ->
        DerivativeFailure.NumericallyUnstable("Sous-flux possible dans pow")
    else -> null
}

private val ANGLE_FUNCTIONS = setOf("sin", "cos", "tan", "asin", "acos", "atan")

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
