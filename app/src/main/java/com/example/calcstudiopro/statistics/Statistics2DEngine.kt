package com.example.calcstudiopro.statistics

import com.example.calcstudiopro.converter.ConversionFormatter
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** Paired statistics and regression calculations. Pair order and duplicates are preserved. */
object Statistics2DEngine {
    data class PairValue(val x: Double, val y: Double)
    data class Result(
        val count: Int,
        val x: StatisticsEngine.Result,
        val y: StatisticsEngine.Result,
        val covariancePopulation: Double?,
        val covarianceSample: Double?,
        val pearson: Double?,
        val spearman: Double?
    )
    enum class Family { LINEAR, POLYNOMIAL, EXPONENTIAL, LOGARITHMIC, POWER }
    data class Model(val family: Family, val degree: Int = 2)
    data class Fit(
        val model: Model,
        val coefficients: List<Double>,
        val xCenter: Double,
        val xScale: Double,
        val predictions: List<Double>,
        val residuals: List<Double>,
        val rSquared: Double?,
        val adjustedRSquared: Double?,
        val rmse: Double,
        val parameterCount: Int
    ) {
        fun equation(): String = when (model.family) {
            Family.LINEAR -> {
                val slope = coefficients[1] / xScale
                val intercept = coefficients[0] - coefficients[1] * (xCenter / xScale)
                if (abs(xCenter / xScale) <= 1e4 && slope.isFinite() && intercept.isFinite()) linearEquation(intercept, slope)
                else "ŷ = ${format(coefficients[0])} + ${signedTerm(coefficients[1], "u")}; u = (x − ${format(xCenter)})/${format(xScale)}"
            }
            Family.POLYNOMIAL -> {
                val expanded = expandPolynomial(coefficients, xCenter, xScale)
                if (expanded != null) polynomialEquation(expanded)
                else normalizedPolynomialEquation(coefficients, xCenter, xScale)
            }
            Family.LOGARITHMIC -> "ŷ = ${format(coefficients[0])}${signedTerm(coefficients[1], "ln(x)")}"
            Family.EXPONENTIAL -> "ŷ = ${format(coefficients[0])}·exp(${format(coefficients[1])}·x)"
            Family.POWER -> "ŷ = ${format(coefficients[0])}·x^${format(coefficients[1])}"
        }

        fun predict(x: Double): Double? {
            if (!x.isFinite()) return null
            val value = when (model.family) {
                Family.LINEAR, Family.POLYNOMIAL -> polynomial(coefficients, (x - xCenter) / xScale)
                Family.LOGARITHMIC -> if (x > 0.0) coefficients[0] + coefficients[1] * ln(x) else Double.NaN
                Family.EXPONENTIAL -> coefficients[0] * exp(coefficients[1] * x)
                Family.POWER -> coefficients[0] * x.pow(coefficients[1])
            }
            return value.takeIf(Double::isFinite)
        }

        private fun format(value: Double) = ConversionFormatter.formatDisplay(value)
        private fun signedTerm(value: Double, variable: String): String = if (value < 0.0) " − ${format(abs(value))}·$variable" else " + ${format(value)}·$variable"
        private fun linearEquation(intercept: Double, slope: Double) = "ŷ = ${format(intercept)}${if (slope < 0.0) " − ${format(abs(slope))}x" else " + ${format(slope)}x"}"
        private fun polynomialEquation(values: List<Double>): String {
            val terms = values.indices.reversed().filter { values[it] != 0.0 }.map { power ->
                val c = values[power]
                val variable = when (power) { 0 -> ""; 1 -> "x"; else -> "x^$power" }
                val body = if (power == 0) format(abs(c)) else if (abs(c) == 1.0) variable else "${format(abs(c))}$variable"
                (if (c < 0.0) "− " else "+ ") + body
            }
            if (terms.isEmpty()) return "ŷ = 0"
            return "ŷ = " + terms.joinToString(" ").removePrefix("+ ")
        }
        private fun normalizedPolynomialEquation(values: List<Double>, center: Double, scale: Double): String {
            val terms = values.mapIndexedNotNull { power, c ->
                if (c == 0.0) null else {
                    val variable = if (power == 0) "" else if (power == 1) "u" else "u^$power"
                    val body = if (power == 0) format(abs(c)) else "${format(abs(c))}$variable"
                    (if (c < 0.0) "− " else "+ ") + body
                }
            }
            val expression = if (terms.isEmpty()) "0" else terms.joinToString(" ").removePrefix("+ ")
            return "ŷ = $expression, u = (x − ${format(center)})/${format(scale)}"
        }
        private fun expandPolynomial(values: List<Double>, center: Double, scale: Double): List<Double>? {
            if (scale == 0.0 || !scale.isFinite() || abs(center / scale) > 1e4) return null
            val alpha = -center / scale
            val beta = 1.0 / scale
            var result = DoubleArray(values.size)
            var basis = doubleArrayOf(1.0)
            values.forEach { coefficient ->
                for (i in basis.indices) result[i] += coefficient * basis[i]
                if (basis.size < values.size) {
                    val next = DoubleArray(basis.size + 1)
                    for (i in basis.indices) { next[i] += basis[i] * alpha; next[i + 1] += basis[i] * beta }
                    basis = next
                }
            }
            if (result.any { !it.isFinite() || (it != 0.0 && format(it) == "0") }) return null
            return result.toList()
        }
    }
    data class FitResult(val fit: Fit? = null, val unavailableReason: String? = null) {
        val available get() = fit != null
    }
    data class Comparison(val model: Model, val fitResult: FitResult, val loocvRmse: Double?)

    fun analyze(points: List<PairValue>): Result? {
        if (points.isEmpty() || points.size > StatisticsEngine.MAX_VALUES || points.any { !it.x.isFinite() || !it.y.isFinite() }) return null
        val xs = points.map { it.x }; val ys = points.map { it.y }
        val xStats = StatisticsEngine.describe(xs) ?: return null
        val yStats = StatisticsEngine.describe(ys) ?: return null
        val n = points.size
        var cov = 0.0
        points.forEach { cov += (it.x / maxAbs(xs) - xStats.mean / maxAbs(xs)) * (it.y / maxAbs(ys) - yStats.mean / maxAbs(ys)) }
        val normalizedCov = cov / n
        val covariancePop = normalizedCov * maxAbs(xs) * maxAbs(ys)
        val covarianceSample = if (n > 1) covariancePop * n / (n - 1) else null
        val denominator = (xStats.populationStandardDeviation / maxAbs(xs)) * (yStats.populationStandardDeviation / maxAbs(ys))
        val pearson = if (n > 1 && denominator > 0.0) (normalizedCov / denominator).coerceIn(-1.0, 1.0) else null
        val rx = ranks(xs); val ry = ranks(ys)
        val spearman = correlation(rx, ry)
        return Result(n, xStats, yStats, covariancePop.takeIf(Double::isFinite), covarianceSample?.takeIf(Double::isFinite), pearson, spearman)
    }

    fun fit(points: List<PairValue>, model: Model): FitResult {
        if (points.isEmpty() || points.any { !it.x.isFinite() || !it.y.isFinite() }) return FitResult(unavailableReason = "Aucune donnée valide.")
        if (points.size > StatisticsEngine.MAX_VALUES) return FitResult(unavailableReason = "Trop de couples.")
        if (model.family == Family.POLYNOMIAL && model.degree !in 2..5) return FitResult(unavailableReason = "Le degré doit être compris entre 2 et 5.")
        val degree = if (model.family == Family.LINEAR || model.family == Family.LOGARITHMIC) 1 else if (model.family == Family.POLYNOMIAL) model.degree else 1
        val n = points.size
        val x = points.map { it.x }; val y = points.map { it.y }
        val center = x.average()
        val scale = (x.maxOrNull()!! - x.minOrNull()!!).let { if (it == 0.0) 1.0 else it }
        val params = degree + 1
        if (n < params) return FitResult(unavailableReason = "Au moins $params couples sont nécessaires.")
        if (x.distinct().size < params) return FitResult(unavailableReason = "Nombre insuffisant de valeurs X distinctes.")
        val raw: Pair<List<Double>, List<Double>> = when (model.family) {
            Family.LINEAR, Family.POLYNOMIAL -> {
                val coeff = leastSquares(x.map { (it - center) / scale }, y, degree)
                    ?: return FitResult(unavailableReason = "Le système polynomial est singulier ou instable.")
                coeff to x.map { polynomial(coeff, (it - center) / scale) }
            }
            Family.LOGARITHMIC -> {
                if (x.any { it <= 0.0 }) return FitResult(unavailableReason = "Le modèle logarithmique exige X > 0.")
                val coeff = leastSquares(x.map(::ln), y, 1) ?: return FitResult(unavailableReason = "Ajustement logarithmique instable.")
                coeff to x.map { coeff[0] + coeff[1] * ln(it) }
            }
            Family.EXPONENTIAL -> {
                if (y.any { it <= 0.0 }) return FitResult(unavailableReason = "Le modèle exponentiel exige Y > 0.")
                nonlinearProfile(x, y, ::identityX) ?: return FitResult(unavailableReason = "L'ajustement exponentiel n'a pas convergé.")
            }
            Family.POWER -> {
                if (x.any { it <= 0.0 } || y.any { it <= 0.0 }) return FitResult(unavailableReason = "Le modèle puissance exige X > 0 et Y > 0.")
                nonlinearProfile(x, y, ::ln) ?: return FitResult(unavailableReason = "L'ajustement puissance n'a pas convergé.")
            }
        }
        val coeff = raw.first
        val predicted = raw.second
        if (predicted.any { !it.isFinite() }) return FitResult(unavailableReason = "Le calcul a produit un résultat non fini.")
        val rmse = sqrt(predicted.indices.sumOf { (y[it] - predicted[it]).pow(2) } / n)
        if (!rmse.isFinite()) return FitResult(unavailableReason = "RMSE non représentable.")
        val yMean = y.average()
        val sse = predicted.indices.sumOf { (y[it] - predicted[it]).pow(2) }
        val sst = y.sumOf { (it - yMean).pow(2) }
        val r2 = if (sst > 0.0) 1.0 - sse / sst else null
        val adjusted = if (r2 != null && n > params) 1.0 - (1.0 - r2) * (n - 1.0) / (n - params) else null
        val fit = Fit(model, coeff, center, scale, predicted, y.indices.map { y[it] - predicted[it] }, r2?.takeIf(Double::isFinite), adjusted?.takeIf(Double::isFinite), rmse, params)
        return FitResult(fit = fit)
    }

    fun compare(points: List<PairValue>): List<Comparison> {
        val models = listOf(Model(Family.LINEAR), Model(Family.POLYNOMIAL, 2), Model(Family.POLYNOMIAL, 3), Model(Family.POLYNOMIAL, 4), Model(Family.POLYNOMIAL, 5), Model(Family.EXPONENTIAL), Model(Family.LOGARITHMIC), Model(Family.POWER))
        return models.map { model ->
            val result = fit(points, model)
            val cv = if (result.fit != null && points.size > result.fit.parameterCount + 1 && points.size <= 80) {
                val errors = points.indices.mapNotNull { omitted ->
                    val training = points.filterIndexed { i, _ -> i != omitted }
                    fit(training, model).fit?.predict(points[omitted].x)?.let { (points[omitted].y - it).pow(2) }
                }
                if (errors.size == points.size) sqrt(errors.average()) else null
            } else null
            Comparison(model, result, cv?.takeIf(Double::isFinite))
        }
    }

    private fun nonlinearProfile(x: List<Double>, y: List<Double>, transformX: (Double) -> Double): Pair<List<Double>, List<Double>>? {
        // Profile least squares in b; each candidate's a is the optimal coefficient on original Y.
        val z = x.map(transformX)
        val span = (z.maxOrNull()!! - z.minOrNull()!!).takeIf { it > 0.0 } ?: return null
        val limit = 40.0 / span
        fun eval(b: Double): Triple<Double, Double, List<Double>>? {
            val basis = z.map { exp(b * it) }
            if (basis.any { !it.isFinite() }) return null
            val denom = basis.sumOf { it * it }
            if (denom == 0.0 || !denom.isFinite()) return null
            val a = y.indices.sumOf { y[it] * basis[it] } / denom
            val pred = basis.map { a * it }
            val sse = y.indices.sumOf { (y[it] - pred[it]).pow(2) }
            return if (a > 0 && sse.isFinite()) Triple(sse, a, pred) else null
        }
        var bestB = 0.0; var best = eval(0.0) ?: return null
        val steps = 300
        for (i in 0..steps) {
            val b = -limit + 2.0 * limit * i / steps
            val current = eval(b) ?: continue
            if (current.first < best.first) { best = current; bestB = b }
        }
        var left = bestB - 2.0 * limit / steps; var right = bestB + 2.0 * limit / steps
        repeat(80) {
            val m1 = left + (right - left) * 0.38196601125
            val m2 = left + (right - left) * 0.61803398875
            if ((eval(m1)?.first ?: Double.POSITIVE_INFINITY) < (eval(m2)?.first ?: Double.POSITIVE_INFINITY)) right = m2 else left = m1
        }
        val b = (left + right) / 2.0
        val final = eval(b) ?: return listOf(best.second, bestB) to best.third
        return listOf(final.second, b) to final.third
    }

    /** Modified Gram-Schmidt QR on a normalized Vandermonde design. */
    private fun leastSquares(xs: List<Double>, ys: List<Double>, degree: Int): List<Double>? {
        val n = xs.size; val columns = degree + 1
        if (n < columns) return null
        val q = Array(columns) { j -> DoubleArray(n) { i -> xs[i].pow(j) } }
        val r = Array(columns) { DoubleArray(columns) }
        for (j in 0 until columns) {
            for (k in 0 until j) {
                val projection = q[k].indices.sumOf { i -> q[k][i] * q[j][i] }
                r[k][j] = projection
                for (i in 0 until n) q[j][i] -= projection * q[k][i]
            }
            val norm = sqrt(q[j].sumOf { it * it })
            if (!norm.isFinite() || norm < 1e-12) return null
            r[j][j] = norm
            for (i in 0 until n) q[j][i] /= norm
        }
        val qty = DoubleArray(columns) { j -> q[j].indices.sumOf { i -> q[j][i] * ys[i] } }
        val beta = DoubleArray(columns)
        for (i in columns - 1 downTo 0) {
            var v = qty[i]
            for (j in i + 1 until columns) v -= r[i][j] * beta[j]
            beta[i] = v / r[i][i]
        }
        return beta.toList().takeIf { it.all(Double::isFinite) }
    }

    private fun ranks(values: List<Double>): List<Double> {
        val sorted = values.withIndex().sortedBy { it.value }
        val result = DoubleArray(values.size)
        var i = 0
        while (i < sorted.size) {
            var end = i + 1
            while (end < sorted.size && sorted[end].value == sorted[i].value) end++
            val rank = (i + 1 + end) / 2.0
            for (j in i until end) result[sorted[j].index] = rank
            i = end
        }
        return result.toList()
    }
    private fun correlation(a: List<Double>, b: List<Double>): Double? {
        if (a.size < 2) return null
        val ma = a.average(); val mb = b.average()
        val da = sqrt(a.sumOf { (it - ma).pow(2) }); val db = sqrt(b.sumOf { (it - mb).pow(2) })
        if (da == 0.0 || db == 0.0) return null
        return (a.indices.sumOf { (a[it] - ma) * (b[it] - mb) } / (da * db)).coerceIn(-1.0, 1.0)
    }
    private fun maxAbs(values: List<Double>) = values.maxOf { abs(it) }.coerceAtLeast(1.0)
    private fun polynomial(coefficients: List<Double>, x: Double) = coefficients.asReversed().fold(0.0) { acc, value -> acc * x + value }
    private fun identityX(value: Double) = value
}
