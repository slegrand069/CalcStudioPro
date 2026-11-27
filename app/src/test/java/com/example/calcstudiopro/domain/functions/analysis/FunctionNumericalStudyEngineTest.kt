package com.example.calcstudiopro.domain.functions.analysis

import com.example.calcstudiopro.domain.functions.ExpressionCompilation
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class FunctionNumericalStudyEngineTest {
    private val compiler = MathExpressionEngine()
    private val engine = FunctionNumericalStudyEngine()

    private fun study(source: String, min: Double = -2.0, max: Double = 2.0, level: RootSearchLevel = RootSearchLevel.STANDARD,
                      mode: FunctionAngleMode = FunctionAngleMode.RAD) = engine.analyze(
        (compiler.compile(source) as ExpressionCompilation.Success).expression,
        SearchInterval(min, max), angleMode = mode, level = level
    )

    private fun study(source: String, parameters: Map<Char, Double>, min: Double = -2.0, max: Double = 2.0) = engine.analyze(
        (compiler.compile(source) as ExpressionCompilation.Success).expression,
        SearchInterval(min, max), parameters = parameters
    )

    @Test fun classifiesCanonicalStationaryPointsConservatively() {
        val minimum = study("x^2")
        assertTrue(minimum.points.any { abs(it.x) < 1e-6 && it.kind == StudyPointKind.LOCAL_MINIMUM })
        val maximum = study("-x^2")
        assertTrue(maximum.points.any { abs(it.x) < 1e-6 && it.kind == StudyPointKind.LOCAL_MAXIMUM })
        val stationary = study("x^3")
        assertTrue(stationary.points.any { abs(it.x) < 1e-6 && it.kind == StudyPointKind.STATIONARY_NO_EXTREMUM })
        val fourth = study("x^4")
        assertTrue(fourth.points.any { abs(it.x) < 1e-6 && it.kind == StudyPointKind.LOCAL_MINIMUM })
    }

    @Test fun reportsBestObservedInsteadOfClaimingUnprovenGlobalExtrema() {
        val result = study("x^2")
        assertEquals(GlobalConclusionKind.BEST_OBSERVED, result.globalMinimum.kind)
        assertNotNull(result.globalMinimum.value)
        val constant = study("5")
        assertEquals(GlobalConclusionKind.ESTABLISHED_GLOBAL, constant.globalMinimum.kind)
        assertEquals(5.0, constant.globalMaximum.value!!, 0.0)
    }

    @Test fun detectsSampledVariationAndCurvatureWithoutCallingItProof() {
        val result = study("x^2")
        assertTrue(result.intervals.isNotEmpty())
        assertTrue(result.intervals.all { it.confidence != StudyConfidence.STRUCTURALLY_ESTABLISHED })
        assertTrue(result.intervals.any { it.curvature == CurvatureKind.CONVEX })
        assertTrue(result.intervals.any { it.variation == VariationKind.STRICTLY_DECREASING || it.variation == VariationKind.STRICTLY_INCREASING })
    }

    @Test fun requiresObservedCurvatureChangeForInflection() {
        val cubic = study("x^3")
        assertTrue(cubic.inflections.any { abs(it.x) < 1e-6 && it.kind == InflectionKind.HORIZONTAL_TANGENT })
        val fourth = study("x^4")
        assertTrue(fourth.inflections.none { abs(it.x) < 1e-6 && it.confidence == StudyConfidence.SAMPLED_EVIDENCE })
        val reciprocal = study("1/x", -2.0, 2.0)
        assertTrue(reciprocal.domainComponentsObserved.size >= 2 || reciprocal.uncertainDomainZones.isNotEmpty())
    }

    @Test fun includesIntervalEndpointsAndPreservesDomainStatuses() {
        val bounded = study("x^2", 1.0, 3.0)
        assertTrue(bounded.points.any { it.x == 1.0 && it.position == ExtremaPosition.INTERVAL_BOUNDARY })
        assertTrue(bounded.points.any { it.x == 3.0 && it.position == ExtremaPosition.INTERVAL_BOUNDARY })
        val stationaryEndpoint = study("x^2", 0.0, 1.0)
        assertTrue(stationaryEndpoint.points.any { it.x == 0.0 && it.kind == StudyPointKind.LOCAL_MINIMUM && it.position == ExtremaPosition.INTERVAL_BOUNDARY })
        val logarithm = study("ln(x)", -1.0, 2.0)
        assertTrue(logarithm.domainComponentsObserved.isNotEmpty())
        assertTrue(logarithm.uncertainDomainZones.isNotEmpty() || logarithm.stopReason == StudyStopReason.PARTIAL)
    }

    @Test fun handlesNondifferentiableAndHigherOrderInflectionCandidatesWithoutOverclaiming() {
        val absolute = study("abs(x)")
        assertTrue("points=${absolute.points.filter { abs(it.x) < 0.1 }} zones=${absolute.uncertainDomainZones}", absolute.points.any { abs(it.x) < 1e-5 && it.kind == StudyPointKind.LOCAL_MINIMUM })
        assertTrue(absolute.points.any { abs(it.x) < 1e-5 && it.firstDerivative.status != StudyValueStatus.FINITE })
        val product = study("x*abs(x)")
        assertTrue(product.points.any { abs(it.x) < 1e-5 })
        val fifth = study("x^5")
        assertTrue(fifth.inflections.any { abs(it.x) < 1e-5 && it.confidence == StudyConfidence.SAMPLED_EVIDENCE })
    }

    @Test fun handlesOscillatoryAndDisconnectedDomainStudiesAsPartialEvidence() {
        val sine = study("sin(x)", -10.0, 10.0, RootSearchLevel.RAPIDE)
        assertTrue(sine.points.any { it.kind == StudyPointKind.LOCAL_MINIMUM || it.kind == StudyPointKind.LOCAL_MAXIMUM || it.kind == StudyPointKind.INDETERMINATE })
        val reciprocal = study("1/x", -1.0, 1.0, RootSearchLevel.RAPIDE)
        assertTrue(reciprocal.domainComponentsObserved.size >= 2)
        assertTrue(reciprocal.intervals.any { it.confidence == StudyConfidence.INDETERMINATE })
    }

    @Test fun identifiesDomainBoundaryCandidatesWithoutBridgingTheDomain() {
        val squareRoot = study("sqrt(x)", -1.0, 1.0)
        assertTrue(squareRoot.points.any { it.position == ExtremaPosition.DOMAIN_BOUNDARY && abs(it.x) < 1e-8 })
        val shiftedPole = study("1/(x-2)", 1.0, 3.0)
        assertTrue(shiftedPole.uncertainDomainZones.isNotEmpty())
        assertTrue(shiftedPole.intervals.any { it.variation == VariationKind.UNDEFINED || it.variation == VariationKind.INDETERMINATE })
        val removableHole = study("(x^2-1)/(x-1)", 0.0, 2.0)
        assertTrue(removableHole.uncertainDomainZones.isNotEmpty())
    }

    @Test fun respectsParameterSnapshotsAndAngularContext() {
        val downward = study("a*x^2", mapOf('a' to -2.0))
        assertTrue(downward.points.any { abs(it.x) < 1e-6 && it.kind == StudyPointKind.LOCAL_MAXIMUM })
        val degreeSine = study("sin(x)", -180.0, 180.0, mode = FunctionAngleMode.DEG)
        assertTrue(degreeSine.inflections.isNotEmpty())
        assertTrue(degreeSine.statistics.totalWorkUnits <= degreeSine.statistics.budget)
    }

    @Test fun usesOneSharedBoundedWorkBudgetAndSupportsCooperativeCancellation() {
        val result = study("sin(10*x)", level = RootSearchLevel.RAPIDE)
        assertTrue(result.statistics.totalWorkUnits <= result.statistics.budget)
        assertEquals(result.statistics.totalWorkUnits, result.statistics.accountedWorkUnits)
        var cancel = true
        val compiled = (compiler.compile("x^2") as ExpressionCompilation.Success).expression
        val cancelled = engine.analyze(compiled, SearchInterval(-1.0, 1.0), level = RootSearchLevel.RAPIDE,
            cancellation = SearchCancellation { cancel })
        assertEquals(StudyStopReason.CANCELLED, cancelled.stopReason)
        cancel = false
    }

    @Test fun respectsDegreeModeForPeriodicCurvature() {
        val result = study("sin(x)", -180.0, 180.0, mode = FunctionAngleMode.DEG)
        assertTrue(result.statistics.totalWorkUnits <= result.statistics.budget)
        assertTrue(result.intervals.isNotEmpty())
    }

    @Test fun includesCriticalAndEndpointEvaluationsInBestObservedValues() {
        val square = study("x^2")
        assertEquals(0.0, square.globalMinimum.value!!, 0.0)
        assertEquals(0.0, square.globalMinimum.x!!, 0.0)
        assertTrue(StudyValueProvenance.CRITICAL_POINT in square.globalMinimum.provenance ||
            StudyValueProvenance.DERIVATIVE_SEARCH in square.globalMinimum.provenance)
        val negativeSquare = study("-x^2")
        assertEquals(0.0, negativeSquare.globalMaximum.value!!, 0.0)
        val line = study("x", 1.0, 3.0)
        assertEquals(1.0, line.globalMinimum.value!!, 0.0)
        assertEquals(3.0, line.globalMaximum.value!!, 0.0)
        val constant = study("5")
        assertEquals(5.0, constant.globalMinimum.value!!, 0.0)
        assertEquals(5.0, constant.globalMaximum.value!!, 0.0)
    }

    @Test fun identifiesIncludedAndExcludedDomainBoundariesWithoutPublishingExcludedValues() {
        val sqrt = study("sqrt(x)", -1.0, 4.0)
        val boundary = sqrt.domainBoundaries.minByOrNull { abs(it.x) }
        assertNotNull("${sqrt.domainBoundaries}", boundary)
        assertEquals(DomainBoundaryKind.INCLUDED, boundary!!.kind)
        assertEquals(0.0, boundary.x, 1e-12)
        assertEquals(0.0, boundary.functionValue.value!!, 0.0)
        assertEquals(StudyPointKind.LOCAL_MINIMUM, sqrt.points.first { abs(it.x) < 1e-12 }.kind)
        assertEquals(0.0, sqrt.globalMinimum.value!!, 0.0)
        assertFalse(sqrt.points.any { abs(it.x) < 1e-12 && it.position == ExtremaPosition.INTERVAL_BOUNDARY })

        val log = study("ln(x)", -1.0, 2.0)
        assertTrue(log.domainBoundaries.any { abs(it.x) < 1e-10 && it.kind == DomainBoundaryKind.EXCLUDED && it.functionValue.value == null })
        val reciprocal = study("1/x", -1.0, 1.0)
        assertTrue(reciprocal.domainBoundaries.any { abs(it.x) < 1e-10 && it.kind != DomainBoundaryKind.INCLUDED })
        assertTrue(reciprocal.domainComponentsObserved.size >= 2)
        val shifted = study("sqrt(x-0.37)", -1.0, 2.0)
        assertTrue(shifted.domainBoundaries.any { abs(it.x - 0.37) < 1e-10 && it.kind == DomainBoundaryKind.INCLUDED })
        val rightBoundary = study("sqrt(4-x)", -1.0, 5.0)
        assertTrue(rightBoundary.domainBoundaries.any { abs(it.x - 4.0) < 1e-10 && it.kind == DomainBoundaryKind.INCLUDED })
        val innerPole = study("1/(x-2)", 1.0, 3.0)
        assertTrue(innerPole.domainBoundaries.any { abs(it.x - 2.0) < 1e-10 && it.kind == DomainBoundaryKind.DISCONTINUITY })
        val hole = study("(x^2-1)/(x-1)", 0.0, 2.0)
        assertTrue(hole.domainBoundaries.any { abs(it.x - 1.0) < 1e-10 && it.functionValue.value == null })
    }

    @Test fun preservesTypedDerivativeStatusesAndConsolidatesDerivativePlateaus() {
        val absolute = study("abs(x)")
        val atZero = absolute.points.minByOrNull { abs(it.x) }!!
        assertEquals(StudyValueStatus.FINITE, atZero.functionValue.status)
        assertEquals(StudyValueStatus.DERIVATIVE_NOT_DEFINED, atZero.firstDerivative.status)
        assertTrue("inflections=${absolute.inflections.size}, intervals=${absolute.intervals.size}, regions=${absolute.derivativeZeroRegions}",
            absolute.inflections.size < 10)
        assertTrue(absolute.derivativeZeroRegions.any { it.order == DifferentiatedOrder.SECOND })
        assertTrue(absolute.intervals.size < 30)
        assertEquals(1, absolute.domainComponentsObserved.size)

        val shifted = study("abs(x-0.37)")
        assertEquals(1, shifted.domainComponentsObserved.size)
        val product = study("x*abs(x)")
        val productZero = product.points.minByOrNull { abs(it.x) }!!
        assertEquals(StudyValueStatus.DERIVATIVE_INDETERMINATE, productZero.firstDerivative.status)
        val root = study("sqrt(x)", -1.0, 1.0).points.minByOrNull { abs(it.x) }!!
        assertEquals(StudyValueStatus.DERIVATIVE_NOT_DEFINED, root.firstDerivative.status)
        val cubeRoot = study("cbrt(x)")
        val cubeRootZero = cubeRoot.points.minByOrNull { abs(it.x) }!!
        assertEquals(StudyValueStatus.DERIVATIVE_INDETERMINATE, cubeRootZero.firstDerivative.status)
    }
}
