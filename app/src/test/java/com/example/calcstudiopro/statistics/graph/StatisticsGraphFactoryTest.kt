package com.example.calcstudiopro.statistics.graph

import com.example.calcstudiopro.statistics.Statistics2DEngine
import com.example.calcstudiopro.statistics.graph.domain.GraphPaths
import com.example.calcstudiopro.statistics.graph.domain.GraphRange
import com.example.calcstudiopro.statistics.graph.model.GraphSeriesKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class StatisticsGraphFactoryTest {
    private val models = listOf(
        Statistics2DEngine.Model(Statistics2DEngine.Family.LINEAR),
        Statistics2DEngine.Model(Statistics2DEngine.Family.POLYNOMIAL, 2),
        Statistics2DEngine.Model(Statistics2DEngine.Family.POLYNOMIAL, 3),
        Statistics2DEngine.Model(Statistics2DEngine.Family.POLYNOMIAL, 4),
        Statistics2DEngine.Model(Statistics2DEngine.Family.POLYNOMIAL, 5),
        Statistics2DEngine.Model(Statistics2DEngine.Family.EXPONENTIAL),
        Statistics2DEngine.Model(Statistics2DEngine.Family.LOGARITHMIC),
        Statistics2DEngine.Model(Statistics2DEngine.Family.POWER)
    )

    @Test
    fun allEightExistingModelsProduceCurvesOnlyWithinObservedXRange() {
        val points = (1..24).map { x ->
            val value = x.toDouble()
            Statistics2DEngine.PairValue(value, 2.0 * value.pow(1.4) + 1.0)
        }
        models.forEach { model ->
            val fit = Statistics2DEngine.fit(points, model).fit
            assertNotNull("Expected an admissible fit for $model", fit)
            val result = StatisticsGraphFactory.regressionScatter(points, fit, null)
            val observations = result.snapshot.series.first()
            val curve = result.snapshot.series.last()
            assertEquals(points.size, observations.points.size)
            assertEquals(GraphSeriesKind.LINE, curve.kind)
            assertTrue(curve.points.size > 2)
            assertTrue(curve.points.all { it.x >= 1.0 && it.x <= 24.0 })
            assertNull(result.unavailableMessage)
        }
    }

    @Test
    fun unavailableModelLeavesDuplicateObservationsVisibleWithReasonAndMultiplicity() {
        val points = listOf(
            Statistics2DEngine.PairValue(-1.0, 2.0),
            Statistics2DEngine.PairValue(0.0, 3.0),
            Statistics2DEngine.PairValue(0.0, 3.0),
            Statistics2DEngine.PairValue(2.0, 5.0)
        )
        val result = StatisticsGraphFactory.regressionScatter(points, null, "Modèle logarithmique : X > 0.")
        val observationSeries = result.snapshot.series.single()
        assertEquals(points.size, observationSeries.points.size)
        assertEquals(2.0, observationSeries.points[1].details.last().value, 0.0)
        assertEquals("Modèle logarithmique : X > 0.", result.unavailableMessage)
        assertTrue(GraphRange.autoFit(result.snapshot).x.minimum < -1.0)
    }

    @Test
    fun fullFitResidualsPreserveDuplicatesIncludeZeroReferenceAndExactDetails() {
        val points = listOf(
            Statistics2DEngine.PairValue(-2.0, 5.0),
            Statistics2DEngine.PairValue(0.0, 1.0),
            Statistics2DEngine.PairValue(0.0, 1.0),
            Statistics2DEngine.PairValue(3.0, -5.0)
        )
        val fit = Statistics2DEngine.fit(points, Statistics2DEngine.Model(Statistics2DEngine.Family.LINEAR)).fit!!
        val result = StatisticsGraphFactory.residuals(points, fit.copy(residuals = List(points.size) { 0.0 }), null)
        val residualSeries = result.snapshot.series.first()
        val zeroLine = result.snapshot.series.last()
        assertEquals(points.size, residualSeries.points.size)
        assertEquals(0.0, residualSeries.points[0].y, 0.0)
        assertTrue(residualSeries.points[2].details.any { it.label == "Y observé" && it.value == 1.0 })
        assertTrue(residualSeries.points[2].details.any { it.label == "Multiplicité" && it.value == 2.0 })
        assertEquals(listOf(0.0, 0.0), zeroLine.points.map { it.y })
        assertNull(result.unavailableMessage)
        assertEquals(0, result.excludedResidualCount)
    }

    @Test
    fun invalidResidualEntriesAreExcludedAndReported() {
        val points = (1..4).map { Statistics2DEngine.PairValue(it.toDouble(), (it * 2).toDouble()) }
        val fit = Statistics2DEngine.fit(points, Statistics2DEngine.Model(Statistics2DEngine.Family.LINEAR)).fit!!
        val result = StatisticsGraphFactory.residuals(points, fit.copy(residuals = listOf(0.0, Double.NaN, 0.0, 0.0)), null)
        assertEquals(1, result.excludedResidualCount)
        assertEquals(3, result.snapshot.series.first().points.size)
    }

    @Test
    fun invalidCurvePredictionsBreakPathsInsteadOfBridgingGaps() {
        val points = listOf(Statistics2DEngine.PairValue(-1.0, 1.0), Statistics2DEngine.PairValue(1.0, 1.0))
        val logarithmicFit = Statistics2DEngine.Fit(
            model = Statistics2DEngine.Model(Statistics2DEngine.Family.LOGARITHMIC), coefficients = listOf(0.0, 1.0),
            xCenter = 0.0, xScale = 1.0, predictions = emptyList(), residuals = emptyList(), rSquared = null,
            adjustedRSquared = null, rmse = 0.0, parameterCount = 2
        )
        val curve = StatisticsGraphFactory.regressionScatter(points, logarithmicFit, null).snapshot.series.last()
        assertTrue(curve.points.any { !it.y.isFinite() })
        assertEquals(1, GraphPaths.connectedSegments(curve.points).size)
        assertFalse(GraphPaths.connectedSegments(curve.points).single().first().x < 0.0)
    }

    @Test
    fun tenThousandCouplesRemainDistinctAndCurveSamplingStaysBounded() {
        val points = (0 until 10_000).map { index ->
            val x = index / 100.0 - 50.0
            Statistics2DEngine.PairValue(x, 3.0 * x + 2.0)
        }
        val fit = Statistics2DEngine.fit(points, Statistics2DEngine.Model(Statistics2DEngine.Family.LINEAR)).fit!!
        val result = StatisticsGraphFactory.regressionScatter(points, fit, null)
        assertEquals(10_000, result.snapshot.series.first().points.size)
        assertEquals(385, result.snapshot.series.last().points.size)
        assertNull(result.unavailableMessage)
    }

    @Test
    fun constantAxesAndExtremeFiniteValuesKeepEveryObservationPlottable() {
        val constantX = listOf(
            Statistics2DEngine.PairValue(2.0, -3.0),
            Statistics2DEngine.PairValue(2.0, 0.0),
            Statistics2DEngine.PairValue(2.0, 7.0)
        )
        val unavailable = Statistics2DEngine.fit(constantX, Statistics2DEngine.Model(Statistics2DEngine.Family.LINEAR))
        val constantXGraph = StatisticsGraphFactory.regressionScatter(constantX, unavailable.fit, unavailable.unavailableReason)
        val xRange = GraphRange.autoFit(constantXGraph.snapshot).x
        assertEquals(3, constantXGraph.snapshot.series.first().points.size)
        assertTrue(xRange.minimum < 2.0 && xRange.maximum > 2.0)

        val extreme = listOf(
            Statistics2DEngine.PairValue(-Double.MAX_VALUE, Double.MIN_VALUE),
            Statistics2DEngine.PairValue(Double.MAX_VALUE, -Double.MIN_VALUE)
        )
        val extremeGraph = StatisticsGraphFactory.regressionScatter(extreme, null, "Ajustement indisponible.")
        val viewport = GraphRange.autoFit(extremeGraph.snapshot)
        assertEquals(2, extremeGraph.snapshot.series.first().points.size)
        assertTrue(viewport.x.minimum.isFinite() && viewport.x.maximum.isFinite())
        assertTrue(viewport.y.minimum.isFinite() && viewport.y.maximum.isFinite())
    }
}
