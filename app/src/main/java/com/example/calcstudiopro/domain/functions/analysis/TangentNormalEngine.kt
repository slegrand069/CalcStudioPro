package com.example.calcstudiopro.domain.functions.analysis

import com.example.calcstudiopro.domain.functions.CompiledMathExpression
import com.example.calcstudiopro.domain.functions.DerivativeEvaluation
import com.example.calcstudiopro.domain.functions.DerivativeFailure
import com.example.calcstudiopro.domain.functions.ExpressionEvaluation
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import java.util.Collections

/** Immutable input snapshot for a pointwise tangent/normal calculation. */
class TangentNormalRequest(
    val expression: CompiledMathExpression,
    val x: Double,
    parameters: Map<Char, Double> = emptyMap(),
    val angleMode: FunctionAngleMode = FunctionAngleMode.RAD
) {
    val parameters: Map<Char, Double> = Collections.unmodifiableMap(
        parameters.entries.associate { it.key.lowercaseChar() to it.value }
    )
}

fun interface TangentNormalCancellation {
    fun isCancelled(): Boolean
}

data class ContactPoint(val x: Double, val y: Double)

enum class TangentDerivativeStatus {
    VALID, FUNCTION_OUT_OF_DOMAIN, FUNCTION_NON_FINITE,
    NOT_DEFINED, INDETERMINATE, UNSUPPORTED, NUMERICALLY_UNSTABLE, NON_FINITE, UNAVAILABLE
}

enum class LineAvailability { AVAILABLE, NOT_DEFINED, INDETERMINATE, UNSUPPORTED, NUMERICALLY_UNSTABLE, NON_FINITE }

/** Stable geometric line forms. A point-direction line does not require a finite intercept. */
sealed interface LineEquation {
    data class PointDirection(val point: ContactPoint, val dx: Double, val dy: Double) : LineEquation
    data class SlopeIntercept(val slope: Double, val intercept: Double) : LineEquation
    data class Horizontal(val y: Double) : LineEquation
    data class Vertical(val x: Double) : LineEquation
}

data class TangentResult(
    val availability: LineAvailability,
    val equation: LineEquation?,
    val slope: Double?,
    /** Optional y=mx+p form; omitted if computing p would overflow or lose finiteness. */
    val slopeIntercept: LineEquation.SlopeIntercept?,
    val diagnostic: String? = null
)

data class NormalResult(
    val availability: LineAvailability,
    val equation: LineEquation?,
    val slope: Double?,
    val slopeIntercept: LineEquation.SlopeIntercept?,
    val diagnostic: String? = null
)

data class TangentNormalResult(
    val contactPoint: ContactPoint?,
    val functionStatus: StudyValueStatus,
    val derivativeStatus: TangentDerivativeStatus,
    val tangent: TangentResult,
    val normal: NormalResult,
    val warnings: List<String>,
    val cancelled: Boolean = false
)

/** Pointwise analytic geometry derived only from the compiled F4.1 evaluation jet. */
class TangentNormalEngine {
    fun calculate(
        request: TangentNormalRequest,
        cancellation: TangentNormalCancellation = TangentNormalCancellation { false }
    ): TangentNormalResult {
        if (!request.x.isFinite()) return failure(
            StudyValueStatus.UNAVAILABLE, TangentDerivativeStatus.UNAVAILABLE,
            LineAvailability.INDETERMINATE, "L'abscisse doit être finie."
        )
        if (cancellation.isCancelled()) return failure(
            StudyValueStatus.UNAVAILABLE, TangentDerivativeStatus.UNAVAILABLE,
            LineAvailability.INDETERMINATE, "Calcul annulé.", cancelled = true
        )

        val evaluated = request.expression.evaluateDifferentiated(request.x, request.parameters, request.angleMode)
        if (cancellation.isCancelled()) return failure(
            StudyValueStatus.UNAVAILABLE, TangentDerivativeStatus.UNAVAILABLE,
            LineAvailability.INDETERMINATE, "Calcul annulé.", cancelled = true
        )

        val functionValue = evaluated.value.toStudyValue()
        if (functionValue.status != StudyValueStatus.FINITE) {
            val lineAvailability = if (functionValue.status == StudyValueStatus.OUT_OF_DOMAIN) LineAvailability.NOT_DEFINED
                else if (functionValue.status == StudyValueStatus.NON_FINITE) LineAvailability.NON_FINITE
                else LineAvailability.INDETERMINATE
            val derivativeStatus = derivativeStatus(evaluated.firstDerivative)
            val message = when (functionValue.status) {
                StudyValueStatus.OUT_OF_DOMAIN -> "La fonction n'est pas définie à cette abscisse."
                StudyValueStatus.NON_FINITE -> "La valeur de la fonction n'est pas finie."
                else -> "L'évaluation de la fonction n'a pas abouti."
            }
            return TangentNormalResult(null, functionValue.status, derivativeStatus,
                TangentResult(lineAvailability, null, null, null, message),
                NormalResult(lineAvailability, null, null, null, message), listOf(message))
        }

        val point = ContactPoint(request.x, functionValue.value!!)
        val derivative = evaluated.firstDerivative
        if (derivative !is DerivativeEvaluation.Value) {
            val status = derivativeStatus(derivative)
            val availability = when (status) {
                TangentDerivativeStatus.NOT_DEFINED -> LineAvailability.NOT_DEFINED
                TangentDerivativeStatus.UNSUPPORTED -> LineAvailability.UNSUPPORTED
                TangentDerivativeStatus.NUMERICALLY_UNSTABLE -> LineAvailability.NUMERICALLY_UNSTABLE
                TangentDerivativeStatus.NON_FINITE -> LineAvailability.NON_FINITE
                else -> LineAvailability.INDETERMINATE
            }
            val diagnostic = (derivative as DerivativeEvaluation.Failure).reason.toString()
            return TangentNormalResult(point, StudyValueStatus.FINITE, status,
                TangentResult(availability, null, null, null, diagnostic),
                NormalResult(availability, null, null, null, diagnostic), listOf(diagnostic))
        }

        val slope = derivative.value
        val tangent = if (slope == 0.0) {
            TangentResult(LineAvailability.AVAILABLE, LineEquation.Horizontal(point.y), 0.0,
                LineEquation.SlopeIntercept(0.0, point.y))
        } else {
            TangentResult(LineAvailability.AVAILABLE, LineEquation.PointDirection(point, 1.0, slope), slope,
                stableIntercept(slope, point))
        }
        val normal = if (slope == 0.0) {
            NormalResult(LineAvailability.AVAILABLE, LineEquation.Vertical(point.x), null, null)
        } else {
            val rawNormalSlope = -1.0 / slope
            val normalSlope = rawNormalSlope.takeIf(Double::isFinite)
            val direction = -slope to 1.0
            NormalResult(LineAvailability.AVAILABLE, LineEquation.PointDirection(point, direction.first, direction.second), normalSlope,
                normalSlope?.let { stableIntercept(it, point) },
                if (normalSlope != null) null else "La pente de la normale ne tient pas dans un Double fini; la forme point-direction est conservée.")
        }
        val warnings = buildList {
            if (tangent.slopeIntercept == null && slope != 0.0) add("L'ordonnée à l'origine de la tangente n'est pas représentable de façon finie; la forme point-direction est conservée.")
            normal.diagnostic?.let(::add)
            if (normal.slope != null && normal.slopeIntercept == null) add("L'ordonnée à l'origine de la normale n'est pas représentable de façon finie; la forme point-direction est conservée.")
        }
        return TangentNormalResult(point, StudyValueStatus.FINITE, TangentDerivativeStatus.VALID, tangent, normal, warnings)
    }

    private fun stableIntercept(slope: Double, point: ContactPoint): LineEquation.SlopeIntercept? {
        val product = slope * point.x
        if (!product.isFinite()) return null
        val intercept = point.y - product
        return intercept.takeIf(Double::isFinite)?.let { LineEquation.SlopeIntercept(slope, it) }
    }

    private fun failure(functionStatus: StudyValueStatus, derivativeStatus: TangentDerivativeStatus,
                        lineAvailability: LineAvailability, message: String, cancelled: Boolean = false) =
        TangentNormalResult(null, functionStatus, derivativeStatus,
            TangentResult(lineAvailability, null, null, null, message),
            NormalResult(lineAvailability, null, null, null, message), listOf(message), cancelled)
}

private fun derivativeStatus(value: DerivativeEvaluation): TangentDerivativeStatus = when (value) {
    is DerivativeEvaluation.Value -> TangentDerivativeStatus.VALID
    is DerivativeEvaluation.Failure -> when (value.reason) {
        is DerivativeFailure.FunctionOutOfDomain -> TangentDerivativeStatus.FUNCTION_OUT_OF_DOMAIN
        is DerivativeFailure.NotDefined -> TangentDerivativeStatus.NOT_DEFINED
        is DerivativeFailure.Indeterminate -> TangentDerivativeStatus.INDETERMINATE
        is DerivativeFailure.Unsupported -> TangentDerivativeStatus.UNSUPPORTED
        is DerivativeFailure.NumericallyUnstable -> TangentDerivativeStatus.NUMERICALLY_UNSTABLE
        is DerivativeFailure.NonFinite -> TangentDerivativeStatus.NON_FINITE
    }
}

private fun ExpressionEvaluation.toStudyValue(): StudyValue = when (this) {
    is ExpressionEvaluation.Success -> StudyValue(value, StudyValueStatus.FINITE)
    is ExpressionEvaluation.Failure -> when (error) {
        is com.example.calcstudiopro.domain.functions.MathExpressionError.Domain -> StudyValue(null, StudyValueStatus.OUT_OF_DOMAIN, expressionError = error)
        is com.example.calcstudiopro.domain.functions.MathExpressionError.NonFiniteResult -> StudyValue(null, StudyValueStatus.NON_FINITE, expressionError = error)
        else -> StudyValue(null, StudyValueStatus.EVALUATION_ERROR, expressionError = error)
    }
}
