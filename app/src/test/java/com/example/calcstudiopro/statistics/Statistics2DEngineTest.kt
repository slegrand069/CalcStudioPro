package com.example.calcstudiopro.statistics

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp
import kotlin.math.pow

class Statistics2DEngineTest {
    private fun points(vararg pairs: Pair<Double, Double>) = pairs.map { Statistics2DEngine.PairValue(it.first, it.second) }
    private fun scored(model: Statistics2DEngine.Model, parameters: Int, cv: Double) = Statistics2DEngine.Comparison(
        model,
        Statistics2DEngine.FitResult(Statistics2DEngine.Fit(model, List(parameters) { 0.0 }, 0.0, 1.0, listOf(0.0), listOf(0.0), 1.0, 1.0, 0.0, parameters)),
        cv
    )

    @Test fun correlationsAndCovarianceHandleTiesAndDegeneracy() {
        val result = Statistics2DEngine.analyze(points(1.0 to 1.0, 2.0 to 2.0, 2.0 to 3.0, 4.0 to 4.0))!!
        assertEquals(4, result.count)
        assertEquals(0.9233805169, result.pearson!!, 1e-8)
        assertEquals(0.948683298, result.spearman!!, 1e-8)
        assertEquals(1.125, result.covariancePopulation!!, 1e-12)
        assertEquals(1.5, result.covarianceSample!!, 1e-12)
        val constant = Statistics2DEngine.analyze(points(2.0 to 1.0, 2.0 to 2.0, 2.0 to 3.0))!!
        assertNull(constant.pearson)
        assertNull(constant.spearman)
    }

    @Test fun linearPolynomialAndDomainRules() {
        val linearPoints = (0..4).map { Statistics2DEngine.PairValue(it.toDouble(), 2.0 + 3.0 * it) }
        val linear = Statistics2DEngine.fit(linearPoints, Statistics2DEngine.Model(Statistics2DEngine.Family.LINEAR)).fit!!
        assertEquals(0.0, linear.rmse, 1e-10)
        assertEquals(1.0, linear.rSquared!!, 1e-12)
        assertEquals(14.0, linear.predict(4.0)!!, 1e-10)
        assertEquals("ŷ = 2 + 3x", linear.equation())
        val quadraticPoints = (-2..2).map { val x = it.toDouble(); Statistics2DEngine.PairValue(x, 1.0 + 2.0 * x + 3.0 * x * x) }
        val quadratic = Statistics2DEngine.fit(quadraticPoints, Statistics2DEngine.Model(Statistics2DEngine.Family.POLYNOMIAL, 2)).fit!!
        assertEquals(17.0, quadratic.predict(2.0)!!, 1e-9)
        assertEquals("ŷ = 3x^2 + 2x + 1", quadratic.equation())
        val noLog = Statistics2DEngine.fit(points(-1.0 to 2.0, 1.0 to 3.0), Statistics2DEngine.Model(Statistics2DEngine.Family.LOGARITHMIC))
        assertFalse(noLog.available)
    }

    @Test fun nonlinearModelsFitOnOriginalYAndLoocvIsConditional() {
        val expPoints = (0..8).map { Statistics2DEngine.PairValue(it / 4.0, 2.0 * exp(0.7 * it / 4.0)) }
        val fit = Statistics2DEngine.fit(expPoints, Statistics2DEngine.Model(Statistics2DEngine.Family.EXPONENTIAL)).fit!!
        assertTrue(fit.rmse < 1e-4)
        assertEquals(2.0 * exp(0.7), fit.predict(1.0)!!, 1e-3)
        val comparisons = Statistics2DEngine.compare(expPoints)
        val linear = comparisons.first().loocvRmse
        assertNotNull(linear)
        assertNull(Statistics2DEngine.compare(expPoints.take(2)).first().loocvRmse)
    }

    @Test fun allPolynomialDegreesAndLogPowerFamiliesHaveReferenceFits() {
        for (degree in 2..5) {
            val data = (-4..4).map { x ->
                val value = x.toDouble()
                val y = (0..degree).sumOf { power -> (power + 1.0) * value.pow(power) }
                Statistics2DEngine.PairValue(value, y)
            }
            val fit = Statistics2DEngine.fit(data, Statistics2DEngine.Model(Statistics2DEngine.Family.POLYNOMIAL, degree)).fit!!
            val x = 1.5
            val expected = (0..degree).sumOf { power -> (power + 1.0) * x.pow(power) }
            assertEquals("degree $degree", expected, fit.predict(x)!!, 1e-7)
        }
        val logData = (1..8).map { x -> Statistics2DEngine.PairValue(x.toDouble(), 4.0 + 2.0 * kotlin.math.ln(x.toDouble())) }
        val logFit = Statistics2DEngine.fit(logData, Statistics2DEngine.Model(Statistics2DEngine.Family.LOGARITHMIC)).fit!!
        assertEquals(4.0 + 2.0 * kotlin.math.ln(6.0), logFit.predict(6.0)!!, 1e-10)
        val powerData = (1..8).map { x -> Statistics2DEngine.PairValue(x.toDouble(), 3.0 * x.toDouble().pow(1.5)) }
        val powerFit = Statistics2DEngine.fit(powerData, Statistics2DEngine.Model(Statistics2DEngine.Family.POWER)).fit!!
        assertEquals(3.0 * 6.0.pow(1.5), powerFit.predict(6.0)!!, 1e-4)
    }

    @Test fun invalidDomainsInsufficientDistinctXAndNonFiniteValuesAreUnavailable() {
        assertFalse(Statistics2DEngine.fit(points(1.0 to 1.0, 1.0 to 2.0, 2.0 to 3.0), Statistics2DEngine.Model(Statistics2DEngine.Family.POLYNOMIAL, 2)).available)
        assertFalse(Statistics2DEngine.fit(points(1.0 to 1.0, 2.0 to -1.0, 3.0 to 3.0), Statistics2DEngine.Model(Statistics2DEngine.Family.EXPONENTIAL)).available)
        assertFalse(Statistics2DEngine.fit(points(0.0 to 1.0, 1.0 to 2.0, 2.0 to 4.0), Statistics2DEngine.Model(Statistics2DEngine.Family.POWER)).available)
        assertNull(Statistics2DEngine.fit(points(1.0 to 1.0, 2.0 to 1.0, 3.0 to 1.0), Statistics2DEngine.Model(Statistics2DEngine.Family.LINEAR)).fit!!.rSquared)
        assertNull(Statistics2DEngine.analyze(listOf(Statistics2DEngine.PairValue(Double.NaN, 1.0))))
    }

    @Test fun recommendationUsesFivePercentBandThenFewerParametersAndCvTieBreak() {
        val linear = Statistics2DEngine.Model(Statistics2DEngine.Family.LINEAR)
        val quadratic = Statistics2DEngine.Model(Statistics2DEngine.Family.POLYNOMIAL, 2)
        val cubic = Statistics2DEngine.Model(Statistics2DEngine.Family.POLYNOMIAL, 3)
        val data = points(1.0 to 2.0, 2.0 to 4.0, 3.0 to 6.0, 4.0 to 8.0)
        val rec = Statistics2DEngine.recommend(listOf(scored(cubic, 4, 10.49), scored(quadratic, 3, 10.5), scored(linear, 2, 10.5)), data)
        assertEquals(linear, rec.model)
        assertTrue(rec.message.contains("moins de paramètres"))
        val tie = Statistics2DEngine.recommend(listOf(scored(quadratic, 3, 10.2), scored(linear, 2, 10.2)), data)
        assertEquals(linear, tie.model)
    }

    @Test fun recommendationHandlesNearZeroScoresInYScaleAndUnreliableCandidates() {
        val linear = Statistics2DEngine.Model(Statistics2DEngine.Family.LINEAR)
        val quadratic = Statistics2DEngine.Model(Statistics2DEngine.Family.POLYNOMIAL, 2)
        val tinyY = points(1.0 to 1e-12, 2.0 to 2e-12, 3.0 to 3e-12, 4.0 to 4e-12)
        val nearZero = Statistics2DEngine.recommend(listOf(scored(quadratic, 3, 1e-26), scored(linear, 2, 0.0)), tinyY)
        assertEquals(linear, nearZero.model)

        val unavailable = Statistics2DEngine.Comparison(linear, Statistics2DEngine.FitResult(unavailableReason = "X constant"), null, "LOOCV instable")
        val noReliable = Statistics2DEngine.recommend(listOf(unavailable), tinyY)
        assertNull(noReliable.model)
        assertTrue(noReliable.message.contains("Aucune recommandation fiable"))

        val limited = Statistics2DEngine.compare((1..81).map { Statistics2DEngine.PairValue(it.toDouble(), 2.0 * it) })
        assertTrue(limited.all { it.loocvRmse == null && it.loocvUnavailableReason?.contains("80 couples") == true })
        assertNull(Statistics2DEngine.recommend(limited, (1..81).map { Statistics2DEngine.PairValue(it.toDouble(), 2.0 * it) }).model)
    }

    @Test fun exactExponentialAndPowerDatasetsHaveReliableRecommendations() {
        val expData = (0..8).map { x -> Statistics2DEngine.PairValue(x / 4.0, 2.0 * exp(0.7 * x / 4.0)) }
        val expRecommendation = Statistics2DEngine.recommend(Statistics2DEngine.compare(expData), expData)
        assertNotNull(expRecommendation.model)
        assertTrue(expRecommendation.message.startsWith("Modèle "))

        val powerData = (1..8).map { x -> Statistics2DEngine.PairValue(x.toDouble(), 3.0 * x.toDouble().pow(1.5)) }
        assertNotNull(Statistics2DEngine.recommend(Statistics2DEngine.compare(powerData), powerData).model)
    }
}
