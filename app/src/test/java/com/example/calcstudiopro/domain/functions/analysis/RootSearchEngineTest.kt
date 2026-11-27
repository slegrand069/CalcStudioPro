package com.example.calcstudiopro.domain.functions.analysis

import com.example.calcstudiopro.domain.functions.ExpressionCompilation
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RootSearchEngineTest {
    private val engine = RootSearchEngine()
    private val compiler = MathExpressionEngine()

    private fun expression(source: String, mode: FunctionAngleMode = FunctionAngleMode.RAD): RootSearchFunction {
        val compiled = (compiler.compile(source) as ExpressionCompilation.Success).expression
        return CompiledExpressionSearchFunction(compiled, angleMode = mode)
    }

    private fun result(function: RootSearchFunction, min: Double, max: Double, level: RootSearchLevel = RootSearchLevel.STANDARD) =
        (engine.search(function, min, max, level) as RootSearchRequestResult.Completed).result

    @Test fun validatesSimpleRootsAndClosedIntervalEndpoints() {
        val r = result(expression("x^2-4"), -3.0, 3.0)
        assertEquals(listOf(-2.0, 2.0), r.solutions.map { it.x }.sorted().map { kotlin.math.round(it * 1e8) / 1e8 })
        assertTrue(r.solutions.all { it.validation == CandidateValidation.VALIDATED_NUMERICALLY && abs(it.residual) < 1e-10 })
        val endpoint = result(expression("x"), 0.0, 4.0)
        assertTrue(endpoint.solutions.any { it.x == 0.0 && it.method == RootDiscoveryMethod.ENDPOINT })
    }

    @Test fun detectsTangentialRootButDoesNotAcceptSmallPositiveFunctionAsRoot() {
        val tangent = result(expression("(x-2)^2"), 0.0, 4.0)
        assertTrue("solutions=${tangent.solutions} unresolved=${tangent.unresolvedZones}", tangent.solutions.any { abs(it.x - 2.0) < 1e-6 })
        val positive = result(expression("10^(-15)*(x^2+1)"), -2.0, 2.0)
        assertTrue(positive.solutions.isEmpty())
        val fourthPower = result(expression("(x-1.41421356237)^4"), 0.0, 3.0)
        assertTrue("${fourthPower.solutions} / ${fourthPower.uncertainCandidates}",
            fourthPower.solutions.any { abs(it.x - 1.41421356237) < 1e-5 } ||
                fourthPower.uncertainCandidates.any { abs(it.x - 1.41421356237) < 1e-5 })
        for (source in listOf("(x-2)^4", "(x-2)^6")) {
            val flat = result(expression(source), 0.0, 4.0)
            assertTrue("$source: ${flat.solutions}", flat.solutions.any { abs(it.x - 2.0) < 1e-6 })
        }
    }

    @Test fun discontinuityCrossingIsNotReportedAsRoot() {
        val pole = result(expression("1/(x-2)"), 1.0, 3.0)
        assertTrue(pole.solutions.none { abs(it.x - 2.0) < 1e-4 })
        assertTrue(pole.unresolvedZones.isNotEmpty() || pole.stopReason == SearchStopReason.DOMAIN_UNEXPLOITABLE)
        val reciprocal = result(expression("1/x"), -1.0, 1.0)
        assertTrue(reciprocal.solutions.none { abs(it.x) < 1e-5 })
    }

    @Test fun handlesDegenerateAndInvalidIntervals() {
        assertEquals(RootSearchRequestResult.InvalidInterval, engine.search(expression("x"), Double.NaN, 1.0))
        val zero = result(expression("x"), 0.0, 0.0)
        assertEquals(listOf(0.0), zero.solutions.map { it.x })
        val notRoot = result(expression("x+1"), 0.0, 0.0)
        assertTrue(notRoot.solutions.isEmpty())
    }

    @Test fun provesOnlyStructurallyIndependentZeroConstant() {
        val zero = result(expression("0"), -1e100, 1e100)
        assertEquals(NonIsolatedSolutions.IDENTICALLY_ZERO_PROVEN, zero.nonIsolatedSolutions)
        val five = result(expression("5"), -10.0, 10.0)
        assertTrue(five.solutions.isEmpty())
        assertEquals("Aucune solution détectée", five.message)
        val parameterZero = (compiler.compile("a") as ExpressionCompilation.Success).expression
        val parameterResult = result(CompiledExpressionSearchFunction(parameterZero, mapOf('a' to 0.0)), -5.0, 5.0)
        assertEquals(NonIsolatedSolutions.IDENTICALLY_ZERO_PROVEN, parameterResult.nonIsolatedSolutions)
        val sampledZero = result(expression("x-x"), -1.0, 1.0)
        assertEquals(NonIsolatedSolutions.SUSPECTED, sampledZero.nonIsolatedSolutions)
        assertTrue(sampledZero.solutions.isEmpty())
    }

    @Test fun budgetIsStrictAndCancellationIsCooperative() {
        val lowBudget = RootSearchEngine(maximumSolutions = 1)
        val low = (lowBudget.search(expression("sin(100*x)"), -10.0, 10.0, RootSearchLevel.RAPIDE)
            as RootSearchRequestResult.Completed).result
        assertTrue(low.statistics.workUnitsConsumed <= low.statistics.workBudget)
        assertTrue(low.solutions.any { it.x == 0.0 })
        var probes = 0
        val cancelled = RootSearchEngine().search(object : RootSearchFunction {
            override fun evaluate(x: Double): FunctionProbe { probes++; return FunctionProbe(SearchValue.Finite(x)) }
        }, -1.0, 1.0, cancellation = SearchCancellation { probes >= 8 }) as RootSearchRequestResult.Completed
        assertEquals(SearchStopReason.CANCELLED, cancelled.result.stopReason)
        assertTrue(probes <= 8)
    }

    @Test fun adaptersSupportDifferencesAndFirstAndSecondDerivativeSearches() {
        val xSquared = (compiler.compile("x^2") as ExpressionCompilation.Success).expression
        val xPlusTwo = (compiler.compile("x+2") as ExpressionCompilation.Success).expression
        val intersection = result(ExpressionDifferenceSearchFunction(xSquared, xPlusTwo), -3.0, 3.0)
        assertTrue(intersection.solutions.any { abs(it.x - 2.0) < 1e-6 })
        assertTrue(intersection.solutions.any { abs(it.x + 1.0) < 1e-6 })

        val cubic = (compiler.compile("x^3-3*x") as ExpressionCompilation.Success).expression
        val firstDerivativeSearch = ExpressionDerivativeSearchFunction(cubic, SearchedDerivativeOrder.FIRST)
        val probe = firstDerivativeSearch.evaluate(0.25)
        assertNotNull(probe.derivative)
        assertNull("F4.1 provides no third derivative", probe.secondDerivative)
        val first = result(firstDerivativeSearch, -2.0, 2.0)
        assertTrue(first.solutions.any { abs(it.x - 1.0) < 1e-6 })
        assertTrue(first.solutions.any { abs(it.x + 1.0) < 1e-6 })
        val second = result(ExpressionDerivativeSearchFunction(cubic, SearchedDerivativeOrder.SECOND), -2.0, 2.0)
        assertTrue(second.solutions.any { abs(it.x) < 1e-6 })
    }

    @Test fun respectsDegreeModeInCompiledExpressionAdapter() {
        val rad = result(expression("sin(x)"), -10.0, 10.0)
        val deg = result(expression("sin(x)", FunctionAngleMode.DEG), -360.0, 360.0)
        assertTrue(rad.solutions.any { abs(it.x) < 1e-10 })
        assertTrue(deg.solutions.any { abs(it.x - 180.0) < 1e-6 })
    }

    @Test fun reportsPartialDomainAndEnforcesConfiguredSolutionLimit() {
        val partial = result(expression("sqrt(x)"), -2.0, 2.0)
        assertTrue(partial.solutions.any { it.x == 0.0 })
        assertTrue(partial.isPartial)
        val noDomain = result(object : RootSearchFunction {
            override fun evaluate(x: Double) = FunctionProbe(SearchValue.OutOfDomain)
        }, -1.0, 1.0)
        assertEquals(SearchStopReason.DOMAIN_UNEXPLOITABLE, noDomain.stopReason)

        val limited = RootSearchEngine().search(object : RootSearchFunction {
            override fun evaluate(x: Double): FunctionProbe {
                val gridIndex = ((x + 1.0) * 512.0).toInt()
                val isExactGridPoint = kotlin.math.abs((x + 1.0) * 512.0 - gridIndex) < 1e-10
                return FunctionProbe(SearchValue.Finite(if (isExactGridPoint && gridIndex != 512) 0.0 else 1.0))
            }
        }, -1.0, 1.0, RootSearchLevel.APPROFONDI) as RootSearchRequestResult.Completed
        assertEquals(1_000, limited.result.solutions.size)
        assertEquals(SearchStopReason.RESULT_LIMIT_REACHED, limited.result.stopReason)
        assertTrue(limited.result.isPartial)
    }

    @Test fun workAccountingIncludesCompositeEvaluatorsAndNeverExceedsBudget() {
        val function = object : RootSearchFunction {
            override val scalarEvaluationsPerProbe = 2
            override val workUnitsPerProbe = 6
            override fun evaluate(x: Double) = FunctionProbe(SearchValue.Finite(x - 0.25))
        }
        val r = result(function, -1.0, 1.0, RootSearchLevel.RAPIDE)
        assertEquals(r.statistics.scalarEvaluations.toLong() * 3L, r.statistics.workUnitsConsumed)
        assertTrue(r.statistics.workUnitsConsumed <= RootSearchLevel.RAPIDE.workBudget)
        val exhausted = result(object : RootSearchFunction {
            override val workUnitsPerProbe = 1_000
            override fun evaluate(x: Double) = FunctionProbe(SearchValue.Finite(x - 0.25))
        }, -1.0, 1.0, RootSearchLevel.RAPIDE)
        assertEquals(SearchStopReason.BUDGET_EXHAUSTED, exhausted.stopReason)
    }

    @Test fun coversPartialDomainsOscillationsAndDiscontinuityCandidates() {
        val logarithm = result(expression("ln(x)"), -1.0, 3.0)
        assertTrue(logarithm.solutions.any { abs(it.x - 1.0) < 1e-8 })
        assertTrue(logarithm.unresolvedZones.isNotEmpty())

        val tangent = result(expression("tan(x)"), 1.0, 2.0)
        assertTrue(tangent.solutions.none { it.x > 1.4 && it.x < 1.8 })
        assertTrue(tangent.unresolvedZones.isNotEmpty())

        val rational = result(expression("(x^2-1)/(x-1)"), -2.0, 2.0)
        assertTrue(rational.solutions.any { abs(it.x + 1.0) < 1e-8 })
        assertTrue(rational.solutions.none { abs(it.x - 1.0) < 1e-8 })

        val oscillating = result(expression("sin(100*x)"), -1.0, 1.0, RootSearchLevel.APPROFONDI)
        assertTrue(oscillating.solutions.size > 50)
        assertTrue(oscillating.solutions.zipWithNext().all { (a, b) -> a.x < b.x })

        val reciprocalSine = result(expression("sin(1/x)"), 0.1, 1.0)
        assertTrue(reciprocalSine.solutions.size >= 2)
    }

    @Test fun handlesRootlessFunctionsAndNearbyEvenMultiplicityRoots() {
        assertTrue(result(expression("x^2+1"), -3.0, 3.0).solutions.isEmpty())
        assertTrue(result(expression("exp(x)"), -3.0, 3.0).solutions.isEmpty())
        val close = result(expression("(x-1)^2*(x-1.05)^2"), 0.0, 2.0, RootSearchLevel.APPROFONDI)
        assertTrue("${close.solutions} ${close.uncertainCandidates}", close.solutions.any { abs(it.x - 1.0) < 1e-5 })
        assertTrue("${close.solutions} ${close.uncertainCandidates}", close.solutions.any { abs(it.x - 1.05) < 1e-5 })
    }

    @Test fun adaptersSnapshotParametersAndHandleWideIntervals() {
        val mutable = mutableMapOf('a' to 2.0)
        val compiled = (compiler.compile("x-a") as ExpressionCompilation.Success).expression
        val function = CompiledExpressionSearchFunction(compiled, mutable)
        mutable['a'] = 0.0
        val parameterRoot = result(function, 0.0, 3.0)
        assertTrue(parameterRoot.solutions.any { abs(it.x - 2.0) < 1e-8 })

        val wide = result(expression("x"), -1e308, 1e308)
        assertTrue(wide.solutions.any { it.x == 0.0 })
        assertTrue(wide.solutions.all { it.x.isFinite() && it.x in -1e308..1e308 })
    }

    @Test fun recordsExplorationAndRefinementCostWithoutPerformanceThresholds() {
        val oscillating = expression("sin(100*x)")
        for (level in RootSearchLevel.entries) {
            val started = System.nanoTime()
            val searched = (engine.search(oscillating, -10.0, 10.0, level) as RootSearchRequestResult.Completed).result
            val elapsed = System.nanoTime() - started
            assertTrue(searched.statistics.workUnitsConsumed <= level.workBudget)
            assertEquals(searched.statistics.workUnitsConsumed, searched.statistics.accountedWorkUnits)
            assertEquals(searched.statistics.workUnitsConsumed,
                searched.statistics.explorationWorkUnits + searched.statistics.signRefinementWorkUnits +
                    searched.statistics.tangentialWorkUnits + searched.statistics.validationWorkUnits)
            println("F4.2_SEARCH level=$level elapsedNanos=$elapsed scalarEvaluations=${searched.statistics.scalarEvaluations} " +
                "workUnits=${searched.statistics.workUnitsConsumed}/${searched.statistics.workBudget} " +
                "phaseUnits=${searched.statistics.explorationWorkUnits}/${searched.statistics.signRefinementWorkUnits}/" +
                "${searched.statistics.tangentialWorkUnits}/${searched.statistics.validationWorkUnits} " +
                "explorationSamples=${searched.statistics.explorationSamples} candidates=${searched.statistics.candidatesDetected} " +
                "solutions=${searched.statistics.solutionsConserved} uncertain=${searched.uncertainCandidates.size} " +
                "rejected=${searched.rejectedCandidates.size} unresolved=${searched.unresolvedZones.size} stop=${searched.stopReason}")
        }
    }

    @Test fun tangentialValidationNeverPromotesSmallPositiveMinimaToRoots() {
        val cases = listOf(
            "x^2+0.000000000000001" to "[-1,1]",
            "10^(-15)*(x^2+1)" to "[-1,1]",
            "(x-2)^2+0.000000000000001" to "[0,4]",
            "x^2+0.000000000000000000000000000001" to "[-1,1]"
        )
        val intervals = listOf(-1.0 to 1.0, -1.0 to 1.0, 0.0 to 4.0, -1.0 to 1.0)
        cases.zip(intervals).forEach { (case, bounds) ->
            for (level in RootSearchLevel.entries) {
                val r = result(expression(case.first), bounds.first, bounds.second, level)
                assertTrue("${case.first} $level: ${r.solutions}", r.solutions.isEmpty())
                assertTrue("${case.first} $level: budget accounting", r.statistics.accountedWorkUnits == r.statistics.workUnitsConsumed)
                assertTrue(r.statistics.workUnitsConsumed <= r.statistics.workBudget)
                println("F4.2_MINIMUM expression=${case.first} level=$level solutions=${r.solutions.size} uncertain=${r.uncertainCandidates.size} " +
                    "rejected=${r.rejectedCandidates.size} work=${r.statistics.workUnitsConsumed}/${r.statistics.workBudget} " +
                    "phases=${r.statistics.explorationWorkUnits}/${r.statistics.signRefinementWorkUnits}/" +
                    "${r.statistics.tangentialWorkUnits}/${r.statistics.validationWorkUnits} stop=${r.stopReason} " +
                    "uncertainExamples=${r.uncertainCandidates.take(2)}")
                if (case.first != "10^(-15)*(x^2+1)") {
                    assertTrue("${case.first} $level: expected rejected or uncertain minimum", r.rejectedCandidates.isNotEmpty() || r.uncertainCandidates.isNotEmpty())
                }
            }
        }
    }

    @Test fun exactTangentialRootsAreRetainedAndConsolidated() {
        for (source in listOf("(x-2)^2", "(x-2)^4", "(x-2)^6")) {
            val r = result(expression(source), 0.0, 4.0)
            assertEquals("$source solutions=${r.solutions}", 1, r.solutions.count { abs(it.x - 2.0) < 1e-12 })
            assertTrue(r.solutions.single { abs(it.x - 2.0) < 1e-12 }.residual == 0.0)
        }
        val square = result(expression("x^2"), -1.0, 1.0)
        assertEquals("${square.solutions}", 1, square.solutions.count { abs(it.x) < 1e-12 })
        val absolute = result(expression("abs(x)"), -1.0, 1.0)
        assertEquals("${absolute.solutions}", 1, absolute.solutions.count { it.x == 0.0 })
        assertEquals(0.0, absolute.solutions.single { it.x == 0.0 }.residual, 0.0)
    }

    @Test fun nonGridTangentRemainsUncertainUnlessNumericallyConfirmed() {
        val root = 0.12345
        val r = result(expression("(x-0.12345)^2"), 0.0, 1.0)
        val near = r.solutions.filter { abs(it.x - root) < 1e-6 } + r.uncertainCandidates.filter { abs(it.x - root) < 1e-6 }
        assertTrue("${r.solutions} ${r.uncertainCandidates}", near.isNotEmpty())
        assertTrue("duplicate tangent records: $near", near.size <= 1)
        assertTrue("tangential phase should be accounted", r.statistics.tangentialWorkUnits > 0L)
        println("F4.2_NON_GRID_TANGENT solutions=${r.solutions.size} uncertain=${r.uncertainCandidates.size} " +
            "rejected=${r.rejectedCandidates.size} work=${r.statistics.workUnitsConsumed}/${r.statistics.workBudget} " +
            "phases=${r.statistics.explorationWorkUnits}/${r.statistics.signRefinementWorkUnits}/" +
            "${r.statistics.tangentialWorkUnits}/${r.statistics.validationWorkUnits} stop=${r.stopReason}")

        for (level in RootSearchLevel.entries) {
            val positive = result(expression("(x-0.12345)^2+0.000000000000001"), 0.0, 1.0, level)
            assertTrue("$level ${positive.solutions}", positive.solutions.none { abs(it.x - root) < 1e-6 })
        }
    }

    @Test fun nearbyDistinctExactSampleRootsAreNotMerged() {
        val r = result(expression("(x-0.25)*(x-0.2509765625)"), 0.0, 1.0)
        val roots = r.solutions.filter { abs(it.x - 0.25) < 1e-12 || abs(it.x - 0.2509765625) < 1e-12 }
        assertEquals("${r.solutions}", 2, roots.size)
    }

    @Test fun sineRootsAtZeroAreNotDuplicatedAndEndpointsRemainIncluded() {
        val sine = result(expression("sin(x)"), -10.0, 10.0)
        val distinct = sine.solutions.map { it.x }.distinct()
        assertEquals("${sine.solutions}", 7, distinct.size)
        assertEquals(7, sine.solutions.size)
        val endpoint = result(expression("x"), 0.0, 1.0)
        assertEquals(1, endpoint.solutions.count { it.x == 0.0 })
    }

    @Test fun comparesOscillatorySearchProfilesAcrossLevelsAndIntervals() {
        val profiles = listOf(
            Triple("sin(x)", -10.0, 10.0),
            Triple("sin(10*x)", -10.0, 10.0),
            Triple("sin(100*x)", -10.0, 10.0),
            Triple("sin(100*x)", -1.0, 1.0)
        )
        for ((source, minimum, maximum) in profiles) for (level in RootSearchLevel.entries) {
            val r = result(expression(source), minimum, maximum, level)
            assertTrue("$source $level work=${r.statistics.workUnitsConsumed}",
                r.statistics.workUnitsConsumed <= r.statistics.workBudget)
            assertEquals("$source $level phase accounting", r.statistics.workUnitsConsumed,
                r.statistics.accountedWorkUnits)
            assertEquals("$source $level duplicate validated roots", r.solutions.size, r.solutions.map { it.x }.distinct().size)
            println("F4.2_PROFILE expression=$source interval=[$minimum,$maximum] level=$level " +
                "solutions=${r.solutions.size} uncertain=${r.uncertainCandidates.size} rejected=${r.rejectedCandidates.size} " +
                "unresolved=${r.unresolvedZones.size} work=${r.statistics.workUnitsConsumed}/${r.statistics.workBudget} " +
                "phases=${r.statistics.explorationWorkUnits}/${r.statistics.signRefinementWorkUnits}/" +
                "${r.statistics.tangentialWorkUnits}/${r.statistics.validationWorkUnits} stop=${r.stopReason}")
        }
    }

    @Test fun advancedSineSearchesMatchAnalyticRootFamiliesWithoutDuplicates() {
        val cases = listOf(
            Triple(-10.0 to 10.0, -318..318, 522),
            Triple(-1.0 to 1.0, -31..31, 63)
        )
        for ((bounds, indices, expectedCount) in cases) {
            val r = result(expression("sin(100*x)"), bounds.first, bounds.second, RootSearchLevel.APPROFONDI)
            val associated = r.solutions.map { solution ->
                val nearestK = kotlin.math.round(solution.x * 100.0 / Math.PI).toInt()
                val analyticX = nearestK * Math.PI / 100.0
                assertTrue("false positive x=${solution.x} residual=${solution.residual}", abs(solution.x - analyticX) < Math.PI / 200.0)
                assertTrue("residual=${solution.residual}", abs(solution.residual) < 1e-6)
                nearestK
            }
            assertTrue("false positive indices=${associated.distinct().filter { it !in indices }}",
                associated.distinct().all { it in indices })
            assertEquals(expectedCount, associated.distinct().size)
            assertEquals("duplicate solutions", associated.size, associated.distinct().size)
            println("F4.2_ANALYTIC interval=$bounds validDistinct=${associated.distinct().size} " +
                "missed=${indices.count { it !in associated.toSet() }} falsePositives=0 duplicates=${associated.size - associated.distinct().size}")
        }
    }
}
