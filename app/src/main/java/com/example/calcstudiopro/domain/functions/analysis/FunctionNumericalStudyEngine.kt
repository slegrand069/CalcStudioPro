package com.example.calcstudiopro.domain.functions.analysis

import com.example.calcstudiopro.domain.functions.CompiledMathExpression
import com.example.calcstudiopro.domain.functions.DerivativeEvaluation
import com.example.calcstudiopro.domain.functions.DerivativeFailure
import com.example.calcstudiopro.domain.functions.DifferentiatedExpressionEvaluation
import com.example.calcstudiopro.domain.functions.ExpressionEvaluation
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionError
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ulp

/** Numerical evidence is deliberately distinguished from a mathematical proof. */
enum class StudyConfidence { SAMPLED_EVIDENCE, INDETERMINATE, STRUCTURALLY_ESTABLISHED }

enum class StudyValueStatus {
    FINITE, OUT_OF_DOMAIN, NON_FINITE, EVALUATION_ERROR,
    DERIVATIVE_NOT_DEFINED, DERIVATIVE_INDETERMINATE, DERIVATIVE_UNSUPPORTED,
    DERIVATIVE_NUMERICALLY_UNSTABLE, DERIVATIVE_NON_FINITE, UNAVAILABLE
}

data class StudyValue(
    val value: Double?,
    val status: StudyValueStatus,
    val derivativeFailure: DerivativeFailure? = null,
    val expressionError: MathExpressionError? = null
) {
    init { require((status == StudyValueStatus.FINITE) == (value != null && value.isFinite())) }
}

enum class StudyValueProvenance {
    EXPLORATION_SAMPLE, DERIVATIVE_SEARCH, INTERVAL_BOUNDARY, CRITICAL_POINT,
    DOMAIN_BOUNDARY, INFLECTION_CANDIDATE, NON_SMOOTH_CANDIDATE, OTHER_REMARKABLE_POINT
}

data class ObservedFunctionValue(
    val x: Double,
    val value: Double?,
    val status: StudyValueStatus,
    val provenance: Set<StudyValueProvenance>,
    val confidence: StudyConfidence,
    val diagnostic: String? = null
)

enum class DomainBoundaryKind { INCLUDED, EXCLUDED, DISCONTINUITY, UNCERTAIN }

data class DomainBoundaryFinding(
    val x: Double,
    val kind: DomainBoundaryKind,
    val enclosure: SearchInterval,
    val functionValue: StudyValue,
    val confidence: StudyConfidence,
    val diagnostic: String
)

enum class DifferentiatedOrder { FIRST, SECOND }

data class DerivativeZeroRegion(
    val order: DifferentiatedOrder,
    val interval: SearchInterval,
    val confidence: StudyConfidence,
    val coverageSamples: Int,
    val diagnostic: String
)

enum class StudyPointKind {
    LOCAL_MINIMUM, LOCAL_MAXIMUM, STATIONARY_NO_EXTREMUM, NON_DIFFERENTIABLE_CANDIDATE,
    DOMAIN_BOUNDARY, INTERVAL_ENDPOINT, INDETERMINATE
}

enum class ExtremaPosition { INTERIOR, INTERVAL_BOUNDARY, DOMAIN_BOUNDARY }

data class StudyPoint(
    val x: Double,
    val functionValue: StudyValue,
    val firstDerivative: StudyValue,
    val secondDerivative: StudyValue,
    val kind: StudyPointKind,
    val position: ExtremaPosition,
    val confidence: StudyConfidence,
    val diagnostic: String? = null
)

enum class VariationKind {
    STRICTLY_INCREASING, STRICTLY_DECREASING, CONSTANT, NON_DECREASING, NON_INCREASING,
    UNDEFINED, INDETERMINATE
}

enum class CurvatureKind { CONVEX, CONCAVE, AFFINE, UNDEFINED, INDETERMINATE }

data class StudyInterval(
    val interval: SearchInterval,
    val variation: VariationKind,
    val curvature: CurvatureKind,
    val confidence: StudyConfidence,
    val diagnostic: String? = null
)

enum class GlobalConclusionKind { ESTABLISHED_GLOBAL, BEST_OBSERVED, INDETERMINATE }

data class GlobalExtremum(
    val isMinimum: Boolean,
    val value: Double?,
    val x: Double?,
    val kind: GlobalConclusionKind,
    val diagnostic: String,
    val provenance: Set<StudyValueProvenance> = emptySet(),
    val confidence: StudyConfidence = StudyConfidence.INDETERMINATE
)

enum class InflectionKind { HORIZONTAL_TANGENT, NON_HORIZONTAL_TANGENT, FIRST_DERIVATIVE_UNDEFINED, INDETERMINATE }

data class InflectionCandidate(
    val x: Double,
    val functionValue: StudyValue,
    val firstDerivative: StudyValue,
    val confidence: StudyConfidence,
    val kind: InflectionKind,
    val diagnostic: String? = null
)

enum class StudyStopReason { COMPLETED_NUMERICALLY, PARTIAL, BUDGET_EXHAUSTED, CANCELLED }

data class StudyWorkStatistics(
    val totalWorkUnits: Long,
    val budget: Long,
    val initialExplorationWorkUnits: Long,
    val classificationEvaluationWorkUnits: Long,
    val firstDerivativeSearchWorkUnits: Long,
    val secondDerivativeSearchWorkUnits: Long,
    val directEvaluations: Int,
    val firstDerivativeWorkBudget: Long,
    val secondDerivativeWorkBudget: Long
) {
    val accountedWorkUnits: Long
        get() = initialExplorationWorkUnits + classificationEvaluationWorkUnits +
            firstDerivativeSearchWorkUnits + secondDerivativeSearchWorkUnits
}

data class FunctionNumericalStudyResult(
    val interval: SearchInterval,
    val domainComponentsObserved: List<SearchInterval>,
    val uncertainDomainZones: List<UnresolvedSearchZone>,
    val unresolvedAnalysisZones: List<UnresolvedSearchZone>,
    val points: List<StudyPoint>,
    val intervals: List<StudyInterval>,
    val domainBoundaries: List<DomainBoundaryFinding>,
    val derivativeZeroRegions: List<DerivativeZeroRegion>,
    val observedFunctionValues: List<ObservedFunctionValue>,
    val inflections: List<InflectionCandidate>,
    val globalMinimum: GlobalExtremum,
    val globalMaximum: GlobalExtremum,
    val firstDerivativeSearch: RootSearchResult?,
    val secondDerivativeSearch: RootSearchResult?,
    val stopReason: StudyStopReason,
    val statistics: StudyWorkStatistics,
    val diagnostics: List<String>
)

/**
 * Bounded synchronous analysis engine. It reuses F4.2 for zero discovery, while direct
 * classifications are based on a global, stratified sample. Such classifications are evidence,
 * not proofs: no interval arithmetic is available in the expression engine.
 */
class FunctionNumericalStudyEngine(
    private val rootSearchEngine: RootSearchEngine = RootSearchEngine()
) {
    fun analyze(
        expression: CompiledMathExpression,
        interval: SearchInterval,
        parameters: Map<Char, Double> = emptyMap(),
        angleMode: FunctionAngleMode = FunctionAngleMode.RAD,
        level: RootSearchLevel = RootSearchLevel.STANDARD,
        cancellation: SearchCancellation = SearchCancellation { false }
    ): FunctionNumericalStudyResult {
        val budget = level.workBudget
        val parametersSnapshot = parameters.entries.associate { it.key.lowercaseChar() to it.value }
        var work = 0L
        var directWork = 0L
        var firstWork = 0L
        var secondWork = 0L
        var directEvaluations = 0
        var stoppedForBudget = false
        var stoppedForCancellation = false
        val cache = HashMap<Long, FunctionProbe>()
        val rawEvaluations = HashMap<Long, DifferentiatedExpressionEvaluation>()
        val provenance = HashMap<Long, MutableSet<StudyValueProvenance>>()
        val postSearchReserve = budget / 10L

        fun cost(): Long = 3L // F4.1 jet evaluation: value, first derivative and second derivative.
        fun directProbe(x: Double): FunctionProbe? {
            if (cancellation.isCancelled()) { stoppedForCancellation = true; return null }
            val key = x.toBits()
            cache[key]?.let { return it }
            if (work + cost() > budget) { stoppedForBudget = true; return null }
            val evaluation = expression.evaluateDifferentiated(x, parametersSnapshot, angleMode)
            fun scalar(value: ExpressionEvaluation): SearchValue = when (value) {
                is ExpressionEvaluation.Success -> SearchValue.Finite(value.value)
                is ExpressionEvaluation.Failure -> when (value.error) {
                    is com.example.calcstudiopro.domain.functions.MathExpressionError.Domain -> SearchValue.OutOfDomain
                    is com.example.calcstudiopro.domain.functions.MathExpressionError.NonFiniteResult -> SearchValue.NonFinite
                    else -> SearchValue.Unavailable(value.error.toString())
                }
            }
            fun derivative(value: DerivativeEvaluation): SearchValue = when (value) {
                is DerivativeEvaluation.Value -> SearchValue.Finite(value.value)
                is DerivativeEvaluation.Failure -> when (value.reason) {
                    is DerivativeFailure.FunctionOutOfDomain -> SearchValue.OutOfDomain
                    is DerivativeFailure.NonFinite -> SearchValue.NonFinite
                    else -> SearchValue.Unavailable(value.reason.toString())
                }
            }
            val probe = FunctionProbe(
                scalar(evaluation.value), derivative(evaluation.firstDerivative), derivative(evaluation.secondDerivative)
            )
            work += cost(); directWork += cost(); directEvaluations++
            cache[key] = probe
            rawEvaluations[key] = evaluation
            return probe
        }

        fun evaluateAt(x: Double, source: StudyValueProvenance): DifferentiatedExpressionEvaluation? {
            provenance.getOrPut(x.toBits()) { linkedSetOf() }.add(source)
            directProbe(x) ?: return null
            return rawEvaluations[x.toBits()]
        }

        val explorationCap = budget / 5L
        val sampleCount = min(512L, (explorationCap / cost()).coerceAtLeast(2L)).toInt()
        val xs = ArrayList<Double>(sampleCount)
        val samples = ArrayList<FunctionProbe?>(sampleCount)
        for (index in 0 until sampleCount) {
            if (cancellation.isCancelled()) { stoppedForCancellation = true; break }
            val fraction = if (sampleCount == 1) 0.0 else index.toDouble() / (sampleCount - 1).toDouble()
            val x = if (index == 0) interval.minimum else if (index == sampleCount - 1) interval.maximum
            else interpolate(interval.minimum, interval.maximum, fraction)
            xs += x
            provenance.getOrPut(x.toBits()) { linkedSetOf() }.add(StudyValueProvenance.EXPLORATION_SAMPLE)
            samples += directProbe(x)
            if (stoppedForBudget || stoppedForCancellation) break
        }
        val initialExplorationWork = directWork

        fun runDerivativeSearch(order: SearchedDerivativeOrder, cap: Long): RootSearchResult? {
            if (cap <= 0L || stoppedForCancellation || stoppedForBudget) return null
            var phaseUsed = 0L
            val bounded = object : RootSearchFunction {
                override val scalarEvaluationsPerProbe = 1
                override val workUnitsPerProbe = 3
                override val structurallyIndependentOfX = !expression.metadata.dependsOnX
                override fun evaluate(x: Double): FunctionProbe {
                    provenance.getOrPut(x.toBits()) { linkedSetOf() }.add(StudyValueProvenance.DERIVATIVE_SEARCH)
                    val differentiated = expression.evaluateDifferentiated(x, parametersSnapshot, angleMode)
                    rawEvaluations[x.toBits()] = differentiated
                    fun scalar(result: ExpressionEvaluation): SearchValue = when (result) {
                        is ExpressionEvaluation.Success -> SearchValue.Finite(result.value)
                        is ExpressionEvaluation.Failure -> when (result.error) {
                            is MathExpressionError.Domain -> SearchValue.OutOfDomain
                            is MathExpressionError.NonFiniteResult -> SearchValue.NonFinite
                            else -> SearchValue.Unavailable(result.error.toString())
                        }
                    }
                    fun derivative(result: DerivativeEvaluation): SearchValue = when (result) {
                        is DerivativeEvaluation.Value -> SearchValue.Finite(result.value)
                        is DerivativeEvaluation.Failure -> when (val failure = result.reason) {
                            is DerivativeFailure.FunctionOutOfDomain -> SearchValue.OutOfDomain
                            is DerivativeFailure.NonFinite -> SearchValue.NonFinite
                            else -> SearchValue.Unavailable(failure.toString())
                        }
                    }
                    val searched = if (order == SearchedDerivativeOrder.FIRST)
                        differentiated.firstDerivative else differentiated.secondDerivative
                    val auxiliary = if (order == SearchedDerivativeOrder.FIRST)
                        differentiated.secondDerivative else null
                    val value = FunctionProbe(derivative(searched), auxiliary?.let(::derivative))
                    phaseUsed += workUnitsPerProbe
                    work += workUnitsPerProbe
                    if (order == SearchedDerivativeOrder.FIRST) firstWork += workUnitsPerProbe else secondWork += workUnitsPerProbe
                    return value
                }
            }
            val phaseCancellation = SearchCancellation {
                val userCancelled = cancellation.isCancelled()
                if (userCancelled) stoppedForCancellation = true
                val phaseLimit = phaseUsed + bounded.workUnitsPerProbe > cap
                val globalLimit = work + bounded.workUnitsPerProbe > budget
                if (globalLimit) stoppedForBudget = true
                userCancelled || phaseLimit || globalLimit
            }
            return (rootSearchEngine.search(bounded, interval.minimum, interval.maximum, RootSearchLevel.APPROFONDI, phaseCancellation)
                as? RootSearchRequestResult.Completed)?.result
        }

        // The sample is reserved first. Derivative searches have equal maximum shares; unused work
        // remains unused rather than allowing either search to silently exceed the analysis budget.
        val derivativeAllowance = ((budget - work - postSearchReserve).coerceAtLeast(0L)) / 2L
        val rawFirstSearch = runDerivativeSearch(SearchedDerivativeOrder.FIRST, derivativeAllowance)
        val rawSecondSearch = runDerivativeSearch(SearchedDerivativeOrder.SECOND, derivativeAllowance)

        val validSamples = xs.indices.mapNotNull { i ->
            val value = samples.getOrNull(i)?.value as? SearchValue.Finite
            if (value == null) null else Sample(xs[i], samples[i]!!, value.value)
        }
        val uncertainZones = buildList {
            rawFirstSearch?.unresolvedZones?.let(::addAll)
            rawSecondSearch?.unresolvedZones?.let(::addAll)
            if (samples.size < sampleCount) add(UnresolvedSearchZone(interval, "Exploration incomplète"))
        }.distinct()
        val mergedUncertainZones = mergeZones(uncertainZones)
        val domainEvidence = inspectDomainBoundaries(xs, samples, rawFirstSearch, rawSecondSearch, interval, ::evaluateAt)
        val domainZones = domainEvidence.first
        val domainBoundaries = domainEvidence.second
        val zeroRegions = detectDerivativeZeroRegions(xs, samples, rawFirstSearch, rawSecondSearch, mergedUncertainZones)
        val firstSearch = consolidateSearch(rawFirstSearch, zeroRegions, DifferentiatedOrder.FIRST)
        val secondSearch = consolidateSearch(rawSecondSearch, zeroRegions, DifferentiatedOrder.SECOND)
        val components = observedComponents(xs, samples, domainBoundaries)
        val points = buildPoints(interval, validSamples, firstSearch, mergedUncertainZones, domainBoundaries, ::evaluateAt)
        val intervals = buildIntervals(xs, samples, firstSearch, secondSearch, domainZones, mergedUncertainZones, zeroRegions)
        val inflections = buildInflections(validSamples, secondSearch, zeroRegions) { x ->
            evaluateAt(x, StudyValueProvenance.INFLECTION_CANDIDATE)?.let {
                FunctionProbe(it.value.toSearchValueForStudy(), it.firstDerivative.toSearchValueForStudy(), it.secondDerivative.toSearchValueForStudy())
            }
        }
        val observedValues = buildObservedValues(rawEvaluations, provenance)
        val globalMinimum = globalResult(true, observedValues, expression.metadata.dependsOnX, components, mergedUncertainZones)
        val globalMaximum = globalResult(false, observedValues, expression.metadata.dependsOnX, components, mergedUncertainZones)
        val stop = when {
            stoppedForCancellation || cancellation.isCancelled() -> StudyStopReason.CANCELLED
            stoppedForBudget -> StudyStopReason.BUDGET_EXHAUSTED
            mergedUncertainZones.isNotEmpty() || firstSearch?.isPartial == true || secondSearch?.isPartial == true ||
                firstSearch?.uncertainCandidates.orEmpty().isNotEmpty() || secondSearch?.uncertainCandidates.orEmpty().isNotEmpty() -> StudyStopReason.PARTIAL
            else -> StudyStopReason.COMPLETED_NUMERICALLY
        }
        val diagnostics = buildList {
            if (stop == StudyStopReason.BUDGET_EXHAUSTED) add("Budget global de l'étude atteint; les résultats conservés restent partiels.")
            if (stop == StudyStopReason.CANCELLED) add("Analyse annulée.")
            if (mergedUncertainZones.isNotEmpty() || firstSearch?.uncertainCandidates.orEmpty().isNotEmpty() || secondSearch?.uncertainCandidates.orEmpty().isNotEmpty())
                add("Certaines zones ou certains candidats des recherches restent indéterminés.")
            if (expression.metadata.dependsOnX && validSamples.isNotEmpty()) add("Les extrema globaux sont des meilleures valeurs observées, sans preuve d'exhaustivité.")
        }
        return FunctionNumericalStudyResult(
            interval, components, domainZones, mergedUncertainZones, points, intervals, domainBoundaries,
            zeroRegions, observedValues, inflections, globalMinimum, globalMaximum,
            firstSearch, secondSearch, stop,
            StudyWorkStatistics(work, budget, initialExplorationWork, directWork - initialExplorationWork,
                firstWork, secondWork, directEvaluations, derivativeAllowance, derivativeAllowance), diagnostics
        )
    }

    private data class Sample(val x: Double, val probe: FunctionProbe, val y: Double)

    private fun inspectDomainBoundaries(
        xs: List<Double>,
        probes: List<FunctionProbe?>,
        first: RootSearchResult?,
        second: RootSearchResult?,
        interval: SearchInterval,
        evaluateAt: (Double, StudyValueProvenance) -> DifferentiatedExpressionEvaluation?
    ): Pair<List<UnresolvedSearchZone>, List<DomainBoundaryFinding>> {
        val mergedSearchZones = mergeZones(first?.unresolvedZones.orEmpty() + second?.unresolvedZones.orEmpty())
        val evidenceXs = (xs + mergedSearchZones.flatMap { zone ->
            listOf(zone.interval.minimum, zone.interval.minimum / 2.0 + zone.interval.maximum / 2.0, zone.interval.maximum)
        }).filter { it in interval.minimum..interval.maximum }.distinct().sorted()
        val evidence: List<Pair<Double, SearchValue>> = evidenceXs.mapNotNull { x ->
            val evaluation = evaluateAt(x, StudyValueProvenance.OTHER_REMARKABLE_POINT) ?: return@mapNotNull null
            x to evaluation.value.toSearchValueForStudy()
        }
        val outRuns = mutableListOf<UnresolvedSearchZone>()
        var outStart: Double? = null
        var outEnd = 0.0
        evidence.forEach { (x, status) ->
            if (status == SearchValue.OutOfDomain) {
                if (outStart == null) outStart = x
                outEnd = x
            } else if (outStart != null) {
                outRuns += UnresolvedSearchZone(SearchInterval(outStart, outEnd), "Fonction observée hors domaine")
                outStart = null
            }
        }
        if (outStart != null) outRuns += UnresolvedSearchZone(SearchInterval(outStart, outEnd), "Fonction observée hors domaine")

        val boundaries = mutableListOf<DomainBoundaryFinding>()
        for (index in 0 until evidence.lastIndex.coerceAtLeast(0)) {
            val (leftX, leftStatus) = evidence[index]
            val (rightX, rightStatus) = evidence[index + 1]
            if (leftX >= rightX || leftStatus == rightStatus) continue
            val leftFinite = leftStatus is SearchValue.Finite
            val rightFinite = rightStatus is SearchValue.Finite
            val leftBoundaryStatus = leftStatus == SearchValue.OutOfDomain || leftStatus == SearchValue.NonFinite
            val rightBoundaryStatus = rightStatus == SearchValue.OutOfDomain || rightStatus == SearchValue.NonFinite
            if ((!leftFinite && !leftBoundaryStatus) || (!rightFinite && !rightBoundaryStatus) || leftFinite == rightFinite) continue

            var insideX = if (leftFinite) leftX else rightX
            var outsideX = if (leftFinite) rightX else leftX
            var uncertain = false
            var outsideStatus: SearchValue = if (leftFinite) rightStatus else leftStatus
            for (iteration in 0 until 64) {
                val midpoint = insideX / 2.0 + outsideX / 2.0
                if (midpoint == insideX || midpoint == outsideX) break
                val midValue = evaluateAt(midpoint, StudyValueProvenance.DOMAIN_BOUNDARY)?.value?.toSearchValueForStudy()
                if (midValue == null) { uncertain = true; break }
                when (midValue) {
                    is SearchValue.Finite -> insideX = midpoint
                    SearchValue.OutOfDomain, SearchValue.NonFinite -> { outsideX = midpoint; outsideStatus = midValue }
                    else -> { uncertain = true; break }
                }
            }
            val inside = evaluateAt(insideX, StudyValueProvenance.DOMAIN_BOUNDARY)
            val outside = evaluateAt(outsideX, StudyValueProvenance.DOMAIN_BOUNDARY)
            // Zero is exactly representable and often the boundary of an even-domain expression;
            // prefer probing it when the refined enclosure straddles zero.
            val centerX = if (min(insideX, outsideX) <= 0.0 && max(insideX, outsideX) >= 0.0) 0.0 else insideX / 2.0 + outsideX / 2.0
            val center = evaluateAt(centerX, StudyValueProvenance.DOMAIN_BOUNDARY)
            val centerStatus = center?.value?.toSearchValueForStudy()
            val enclosureScale = max(1.0, max(abs(insideX), abs(outsideX)))
            val enclosureSmall = abs(outsideX - insideX) <= enclosureScale * 1e-12
            val included = centerStatus is SearchValue.Finite && enclosureSmall
            val divisionHole = (center?.value as? ExpressionEvaluation.Failure)?.error.let {
                it is MathExpressionError.Domain && it.operation == "division"
            }
            val exactExcluded = centerStatus == SearchValue.OutOfDomain && !divisionHole && enclosureSmall
            val discontinuity = (centerStatus == SearchValue.NonFinite || divisionHole) && enclosureSmall
            val x = when {
                included || exactExcluded || discontinuity -> centerX
                else -> centerX
            }
            val kind = when {
                uncertain -> DomainBoundaryKind.UNCERTAIN
                included -> DomainBoundaryKind.INCLUDED
                exactExcluded -> DomainBoundaryKind.EXCLUDED
                discontinuity -> DomainBoundaryKind.DISCONTINUITY
                else -> DomainBoundaryKind.UNCERTAIN
            }
            val functionValue = when {
                included -> center?.value?.toStudyValue() ?: inside?.value?.toStudyValue() ?: unavailableValue()
                exactExcluded -> StudyValue(null, StudyValueStatus.OUT_OF_DOMAIN)
                discontinuity -> center?.value?.toStudyValue() ?: StudyValue(null, StudyValueStatus.NON_FINITE)
                else -> unavailableValue("La frontière est encadrée mais non évaluée exactement.")
            }
            val enclosure = SearchInterval(min(insideX, outsideX), max(insideX, outsideX))
            val boundary = DomainBoundaryFinding(x, kind, enclosure, functionValue,
                if (kind == DomainBoundaryKind.UNCERTAIN) StudyConfidence.INDETERMINATE else StudyConfidence.SAMPLED_EVIDENCE,
                if (kind == DomainBoundaryKind.INCLUDED) "Frontière incluse observée numériquement."
                else if (kind == DomainBoundaryKind.EXCLUDED) "Point frontière évalué hors domaine; aucune valeur f(x) n'est publiée."
                else "Frontière de domaine seulement encadrée numériquement.")
            if (boundaries.none { abs(it.x - boundary.x) <= max(it.x.ulp, boundary.x.ulp) * 4.0 }) boundaries += boundary
        }
        val consolidated = mutableListOf<DomainBoundaryFinding>()
        for (finding in boundaries.sortedBy { it.enclosure.minimum }) {
            val previous = consolidated.lastOrNull()
            val mergeTolerance = previous?.let { max(1.0, max(abs(it.enclosure.maximum), abs(finding.enclosure.minimum))) * 1e-12 } ?: 0.0
            if (previous != null && finding.enclosure.minimum <= previous.enclosure.maximum + mergeTolerance) {
                val preferred = when {
                    previous.kind == DomainBoundaryKind.INCLUDED -> previous
                    finding.kind == DomainBoundaryKind.INCLUDED -> finding
                    previous.kind == DomainBoundaryKind.DISCONTINUITY -> previous
                    finding.kind == DomainBoundaryKind.DISCONTINUITY -> finding
                    else -> previous
                }
                consolidated[consolidated.lastIndex] = preferred.copy(
                    enclosure = SearchInterval(min(previous.enclosure.minimum, finding.enclosure.minimum), max(previous.enclosure.maximum, finding.enclosure.maximum)),
                    diagnostic = "Transitions de domaine voisins consolidés; ${preferred.diagnostic}"
                )
            } else consolidated += finding
        }
        return mergeZones(outRuns + consolidated.map { UnresolvedSearchZone(it.enclosure, "Transition de domaine: ${it.kind}") }) to consolidated.sortedBy { it.x }
    }

    private fun observedComponents(
        xs: List<Double>, probes: List<FunctionProbe?>, boundaries: List<DomainBoundaryFinding>
    ): List<SearchInterval> {
        val finitePoints = xs.indices.mapNotNull { index ->
            if (probes.getOrNull(index)?.value is SearchValue.Finite) xs[index] else null
        }.toMutableList()
        boundaries.filter { it.kind == DomainBoundaryKind.INCLUDED && it.functionValue.status == StudyValueStatus.FINITE }
            .forEach { finitePoints += it.x }
        val sorted = finitePoints.distinct().sorted()
        if (sorted.isEmpty()) return emptyList()
        val breaks = boundaries.filter { it.kind == DomainBoundaryKind.EXCLUDED || it.kind == DomainBoundaryKind.DISCONTINUITY }
        val result = mutableListOf<SearchInterval>()
        var start = sorted.first()
        var previous = start
        for (x in sorted.drop(1)) {
            val breakBetween = breaks.any { it.x in previous..x }
            if (breakBetween) {
                result += SearchInterval(start, previous)
                start = x
            }
            previous = x
        }
        result += SearchInterval(start, previous)
        return result
    }

    private fun mergeZones(zones: List<UnresolvedSearchZone>): List<UnresolvedSearchZone> {
        if (zones.isEmpty()) return emptyList()
        val sorted = zones.sortedWith(compareBy<UnresolvedSearchZone> { it.interval.minimum }.thenBy { it.interval.maximum })
        val merged = mutableListOf<UnresolvedSearchZone>()
        var current = sorted.first()
        for (next in sorted.drop(1)) {
            val tolerance = max(current.interval.maximum.ulp, next.interval.minimum.ulp) * 8.0
            if (next.interval.minimum <= current.interval.maximum + tolerance) {
                current = UnresolvedSearchZone(
                    SearchInterval(current.interval.minimum, max(current.interval.maximum, next.interval.maximum)),
                    listOf(current.reason, next.reason).distinct().joinToString("; ")
                )
            } else {
                merged += current
                current = next
            }
        }
        merged += current
        return merged
    }

    private fun detectDerivativeZeroRegions(
        xs: List<Double>,
        samples: List<FunctionProbe?>,
        first: RootSearchResult?,
        second: RootSearchResult?,
        allUncertainZones: List<UnresolvedSearchZone>
    ): List<DerivativeZeroRegion> {
        fun regions(order: DifferentiatedOrder, search: RootSearchResult?): List<DerivativeZeroRegion> {
            val zeroRuns = mutableListOf<Pair<Int, Int>>()
            var runStart: Int? = null
            var previousZero = false
            for (index in xs.indices) {
                val component = if (order == DifferentiatedOrder.FIRST) samples.getOrNull(index)?.derivative
                    else samples.getOrNull(index)?.secondDerivative
                val zero = (component as? SearchValue.Finite)?.value == 0.0
                val blocked = index > 0 && (search?.unresolvedZones.orEmpty().any { overlaps(it.interval, xs[index - 1], xs[index]) } ||
                    allUncertainZones.any { overlaps(it.interval, xs[index - 1], xs[index]) && it.reason.contains("domaine", true) })
                if (zero && (!previousZero || blocked)) {
                    runStart?.let { if (index - it >= 3) zeroRuns += it to index - 1 }
                    runStart = index
                } else if (!zero && runStart != null) {
                    val start = runStart!!
                    if (index - start >= 3) zeroRuns += start to index - 1
                    runStart = null
                }
                previousZero = zero
                if (blocked && zero && runStart == null) runStart = index
            }
            runStart?.let { if (xs.lastIndex - it + 1 >= 3) zeroRuns += it to xs.lastIndex }
            return zeroRuns.map { (startIndex, endIndex) ->
                val orderLabel = if (order == DifferentiatedOrder.FIRST) "f'" else "f''"
                DerivativeZeroRegion(order, SearchInterval(xs[startIndex], xs[endIndex]), StudyConfidence.SAMPLED_EVIDENCE,
                    endIndex - startIndex + 1,
                    "$orderLabel nul sur les échantillons de cette région; cela ne démontre pas une identité sur tout l'intervalle.")
            }
        }
        return (regions(DifferentiatedOrder.FIRST, first) + regions(DifferentiatedOrder.SECOND, second))
            .sortedWith(compareBy<DerivativeZeroRegion> { it.order.ordinal }.thenBy { it.interval.minimum })
    }

    private fun consolidateSearch(
        search: RootSearchResult?, regions: List<DerivativeZeroRegion>, order: DifferentiatedOrder
    ): RootSearchResult? {
        if (search == null) return null
        val plateaus = regions.filter { it.order == order }
        if (plateaus.isEmpty()) return search
        fun inPlateau(x: Double) = plateaus.any { x in it.interval.minimum..it.interval.maximum }
        val solutions = search.solutions.filterNot { inPlateau(it.x) }
        val uncertain = search.uncertainCandidates.filterNot { inPlateau(it.x) }
        val removed = search.solutions.size - solutions.size + search.uncertainCandidates.size - uncertain.size
        return if (removed == 0) search else search.copy(
            solutions = solutions,
            uncertainCandidates = uncertain,
            nonIsolatedSolutions = if (search.nonIsolatedSolutions == NonIsolatedSolutions.NOT_SUSPECTED)
                NonIsolatedSolutions.SUSPECTED else search.nonIsolatedSolutions,
            statistics = search.statistics.copy(solutionsConserved = solutions.size),
            message = search.message + "; $removed zéros ponctuels consolidés en ${plateaus.size} région(s) de dérivée nulle"
        )
    }

    private fun buildObservedValues(
        evaluations: Map<Long, DifferentiatedExpressionEvaluation>,
        provenance: Map<Long, Set<StudyValueProvenance>>
    ): List<ObservedFunctionValue> = evaluations.entries.mapNotNull { (bits, evaluation) ->
        val value = evaluation.value.toStudyValue()
        ObservedFunctionValue(
            Double.fromBits(bits), value.value, value.status,
            provenance[bits]?.toSet().orEmpty(),
            if (value.status == StudyValueStatus.FINITE) StudyConfidence.SAMPLED_EVIDENCE else StudyConfidence.INDETERMINATE,
            value.expressionError?.toString()
        )
    }.sortedBy { it.x }

    private fun buildPoints(
        interval: SearchInterval,
        samples: List<Sample>,
        search: RootSearchResult?,
        uncertainZones: List<UnresolvedSearchZone>,
        domainBoundaries: List<DomainBoundaryFinding>,
        evaluateAt: (Double, StudyValueProvenance) -> DifferentiatedExpressionEvaluation?
    ): List<StudyPoint> {
        val result = mutableListOf<StudyPoint>()
        val candidates = search?.solutions.orEmpty().map { it.x to (it.validation == CandidateValidation.VALIDATED_NUMERICALLY) } +
            search?.uncertainCandidates.orEmpty().map { it.x to false }
        for ((x, validated) in candidates.distinctBy { it.first.toBits() }) {
            val evaluation = evaluateAt(x, StudyValueProvenance.CRITICAL_POINT)
            val left = samples.filter { it.x < x }.maxByOrNull { it.x }
            val right = samples.filter { it.x > x }.minByOrNull { it.x }
            val atBoundary = x == interval.minimum || x == interval.maximum
            val kind = when {
                !validated -> StudyPointKind.INDETERMINATE
                atBoundary -> {
                    val inward = if (x == interval.minimum) right else left
                    val value = (evaluation?.value as? ExpressionEvaluation.Success)?.value
                    when {
                        value == null || inward == null -> StudyPointKind.INDETERMINATE
                        value < inward.y -> StudyPointKind.LOCAL_MINIMUM
                        value > inward.y -> StudyPointKind.LOCAL_MAXIMUM
                        else -> StudyPointKind.INDETERMINATE
                    }
                }
                left == null || right == null -> StudyPointKind.INDETERMINATE
                left.probe.derivative is SearchValue.Finite && right.probe.derivative is SearchValue.Finite -> {
                    val l = (left.probe.derivative as SearchValue.Finite).value
                    val r = (right.probe.derivative as SearchValue.Finite).value
                    when { l < 0.0 && r > 0.0 -> StudyPointKind.LOCAL_MINIMUM; l > 0.0 && r < 0.0 -> StudyPointKind.LOCAL_MAXIMUM
                        else -> StudyPointKind.STATIONARY_NO_EXTREMUM }
                }
                else -> StudyPointKind.INDETERMINATE
            }
            result += StudyPoint(x, evaluation?.value?.toStudyValue() ?: unavailableValue(),
                evaluation?.firstDerivative?.toStudyValue() ?: unavailableValue(),
                evaluation?.secondDerivative?.toStudyValue() ?: unavailableValue(),
                if (atBoundary && kind == StudyPointKind.INDETERMINATE) StudyPointKind.INTERVAL_ENDPOINT else kind,
                if (atBoundary) ExtremaPosition.INTERVAL_BOUNDARY else ExtremaPosition.INTERIOR,
                if (validated && kind != StudyPointKind.INDETERMINATE) StudyConfidence.SAMPLED_EVIDENCE else StudyConfidence.INDETERMINATE,
                if (!validated) "Candidat incertain issu de F4.2" else if (uncertainZones.any { x in it.interval.minimum..it.interval.maximum }) "Zone voisine non résolue" else null)
        }
        for (sample in samples) {
            if (sample.probe.derivative !is SearchValue.Finite && result.none { it.x.toBits() == sample.x.toBits() }) {
                val evaluation = evaluateAt(sample.x, StudyValueProvenance.NON_SMOOTH_CANDIDATE)
                result += StudyPoint(sample.x, evaluation?.value?.toStudyValue() ?: sample.probe.value.toStudyValue(),
                    evaluation?.firstDerivative?.toStudyValue() ?: sample.probe.derivative.toStudyValue(),
                    evaluation?.secondDerivative?.toStudyValue() ?: sample.probe.secondDerivative.toStudyValue(),
                    StudyPointKind.NON_DIFFERENTIABLE_CANDIDATE, ExtremaPosition.INTERIOR, StudyConfidence.INDETERMINATE,
                    "La dérivée n'est pas exploitable à ce point; cela ne prouve pas une non-dérivabilité.")
            }
        }
        search?.unresolvedZones.orEmpty().forEach { zone ->
            val midpoint = zone.interval.minimum / 2.0 + zone.interval.maximum / 2.0
            listOf(zone.interval.minimum, midpoint, zone.interval.maximum).forEach { x ->
              if (x.isFinite() && result.none { abs(it.x - x) <= max(it.x.ulp, x.ulp) * 4.0 }) {
                val evaluation = evaluateAt(x, StudyValueProvenance.NON_SMOOTH_CANDIDATE)
                val probe = evaluation?.let { FunctionProbe(it.value.toSearchValueForStudy(), it.firstDerivative.toSearchValueForStudy(), it.secondDerivative.toSearchValueForStudy()) }
                if (probe?.value is SearchValue.Finite && probe.derivative !is SearchValue.Finite) {
                    val left = samples.filter { it.x < x }.maxByOrNull { it.x }
                    val right = samples.filter { it.x > x }.minByOrNull { it.x }
                    val y = (probe.value as SearchValue.Finite).value
                    val domainBoundary = (left == null) xor (right == null)
                    val inward = left ?: right
                    val kind = when {
                        left != null && right != null && y <= left.y && y <= right.y -> StudyPointKind.LOCAL_MINIMUM
                        left != null && right != null && y >= left.y && y >= right.y -> StudyPointKind.LOCAL_MAXIMUM
                        domainBoundary && inward != null && y < inward.y -> StudyPointKind.LOCAL_MINIMUM
                        domainBoundary && inward != null && y > inward.y -> StudyPointKind.LOCAL_MAXIMUM
                        else -> StudyPointKind.NON_DIFFERENTIABLE_CANDIDATE
                    }
                    result += StudyPoint(x, evaluation.value.toStudyValue(), evaluation.firstDerivative.toStudyValue(), evaluation.secondDerivative.toStudyValue(),
                        kind, if (domainBoundary) ExtremaPosition.DOMAIN_BOUNDARY else ExtremaPosition.INTERIOR,
                        if (domainBoundary && kind != StudyPointKind.NON_DIFFERENTIABLE_CANDIDATE) StudyConfidence.SAMPLED_EVIDENCE else StudyConfidence.INDETERMINATE,
                        "Point issu d'une zone F4.2 non résolue; la position est indicative, pas une frontière démontrée.")
                }
              }
            }
        }
        for (boundary in domainBoundaries) {
            if (result.any { abs(it.x - boundary.x) <= max(it.x.ulp, boundary.x.ulp) * 4.0 }) continue
            val evaluation = evaluateAt(boundary.x, StudyValueProvenance.DOMAIN_BOUNDARY)
            val y = evaluation?.value?.let { (it as? ExpressionEvaluation.Success)?.value }
            val left = samples.filter { it.x < boundary.x }.maxByOrNull { it.x }
            val right = samples.filter { it.x > boundary.x }.minByOrNull { it.x }
            val inward = if (left == null) right else if (right == null) left else null
            val kind = when {
                boundary.kind != DomainBoundaryKind.INCLUDED -> StudyPointKind.DOMAIN_BOUNDARY
                y == null || inward == null -> StudyPointKind.INDETERMINATE
                y < inward.y -> StudyPointKind.LOCAL_MINIMUM
                y > inward.y -> StudyPointKind.LOCAL_MAXIMUM
                else -> StudyPointKind.INDETERMINATE
            }
            result += StudyPoint(boundary.x, evaluation?.value?.toStudyValue() ?: boundary.functionValue,
                evaluation?.firstDerivative?.toStudyValue() ?: unavailableValue(),
                evaluation?.secondDerivative?.toStudyValue() ?: unavailableValue(),
                kind, ExtremaPosition.DOMAIN_BOUNDARY,
                if (boundary.confidence == StudyConfidence.INDETERMINATE || kind == StudyPointKind.INDETERMINATE)
                    StudyConfidence.INDETERMINATE else StudyConfidence.SAMPLED_EVIDENCE,
                boundary.diagnostic)
        }
        for (x in listOf(interval.minimum, interval.maximum)) {
            if (result.none { abs(it.x - x) <= max(it.x.ulp, x.ulp) * 4.0 }) {
                val evaluated = evaluateAt(x, StudyValueProvenance.INTERVAL_BOUNDARY)
                val inward = if (x == interval.minimum) samples.firstOrNull { it.x > x } else samples.lastOrNull { it.x < x }
                val endpointValue = (evaluated?.value as? ExpressionEvaluation.Success)?.value
                val endpointKind = if (endpointValue != null && inward != null) when {
                    endpointValue < inward.y -> StudyPointKind.LOCAL_MINIMUM
                    endpointValue > inward.y -> StudyPointKind.LOCAL_MAXIMUM
                    else -> StudyPointKind.INDETERMINATE
                } else StudyPointKind.INDETERMINATE
                result += StudyPoint(x, evaluated?.value?.toStudyValue() ?: unavailableValue(),
                    evaluated?.firstDerivative?.toStudyValue() ?: unavailableValue(),
                    evaluated?.secondDerivative?.toStudyValue() ?: unavailableValue(),
                    if (endpointKind == StudyPointKind.INDETERMINATE) StudyPointKind.INTERVAL_ENDPOINT else endpointKind,
                    ExtremaPosition.INTERVAL_BOUNDARY,
                    if (endpointKind == StudyPointKind.INDETERMINATE) StudyConfidence.INDETERMINATE else StudyConfidence.SAMPLED_EVIDENCE,
                    "Classification unilatérale relative à l'intervalle, fondée sur l'échantillon intérieur voisin.")
            }
        }
        return result.sortedBy { it.x }
    }

    private fun buildIntervals(
        xs: List<Double>, samples: List<FunctionProbe?>, first: RootSearchResult?, second: RootSearchResult?,
        domainZones: List<UnresolvedSearchZone>, allZones: List<UnresolvedSearchZone>, zeroRegions: List<DerivativeZeroRegion>
    ): List<StudyInterval> {
        if (xs.size < 2) return emptyList()
        val plateaus = zeroRegions.map { it.interval }
        fun plateauInterior(x: Double) = plateaus.any { x > it.minimum && x < it.maximum }
        val cuts = (first?.solutions.orEmpty().map { it.x } + first?.uncertainCandidates.orEmpty().map { it.x } +
            second?.solutions.orEmpty().map { it.x } + second?.uncertainCandidates.orEmpty().map { it.x } +
            domainZones.flatMap { listOf(it.interval.minimum, it.interval.maximum) } +
            allZones.flatMap { listOf(it.interval.minimum, it.interval.maximum) } +
            plateaus.flatMap { listOf(it.minimum, it.maximum) })
            .filterNot(::plateauInterior)
            .filter { it > xs.first() && it < xs.last() }.distinct().sorted()
        val bounds = listOf(xs.first()) + cuts + xs.last()
        return bounds.zipWithNext().mapNotNull { (a, b) ->
            if (!(a < b)) return@mapNotNull null
            val indices = xs.indices.filter { xs[it] > a && xs[it] < b }
            val derivatives = indices.mapNotNull { (samples.getOrNull(it)?.derivative as? SearchValue.Finite)?.value }
            val secondDerivatives = indices.mapNotNull { (samples.getOrNull(it)?.secondDerivative as? SearchValue.Finite)?.value }
            val values = indices.mapNotNull { (samples.getOrNull(it)?.value as? SearchValue.Finite)?.value }
            val domainUnknown = indices.any { samples.getOrNull(it)?.value !is SearchValue.Finite } ||
                allZones.any { overlaps(it.interval, a, b) }
            val knownDomainHole = domainZones.any { (a + (b - a) / 2.0) in it.interval.minimum..it.interval.maximum }
            val variation = when {
                knownDomainHole -> VariationKind.UNDEFINED
                domainUnknown || derivatives.isEmpty() -> VariationKind.INDETERMINATE
                derivatives.all { it == 0.0 } && values.distinct().size <= 1 -> VariationKind.CONSTANT
                derivatives.all { it >= 0.0 } && derivatives.any { it > 0.0 } -> VariationKind.STRICTLY_INCREASING
                derivatives.all { it <= 0.0 } && derivatives.any { it < 0.0 } -> VariationKind.STRICTLY_DECREASING
                derivatives.all { it >= 0.0 } -> VariationKind.NON_DECREASING
                derivatives.all { it <= 0.0 } -> VariationKind.NON_INCREASING
                else -> VariationKind.INDETERMINATE
            }
            val curvature = when {
                knownDomainHole -> CurvatureKind.UNDEFINED
                domainUnknown || secondDerivatives.isEmpty() -> CurvatureKind.INDETERMINATE
                secondDerivatives.all { it == 0.0 } -> CurvatureKind.AFFINE
                secondDerivatives.all { it >= 0.0 } -> CurvatureKind.CONVEX
                secondDerivatives.all { it <= 0.0 } -> CurvatureKind.CONCAVE
                else -> CurvatureKind.INDETERMINATE
            }
            val uncertain = domainUnknown || first?.uncertainCandidates.orEmpty().any { it.x in a..b } || second?.uncertainCandidates.orEmpty().any { it.x in a..b }
            StudyInterval(SearchInterval(a, b), if (uncertain && variation != VariationKind.INDETERMINATE) VariationKind.INDETERMINATE else variation,
                if (uncertain && curvature != CurvatureKind.INDETERMINATE) CurvatureKind.INDETERMINATE else curvature,
                if (uncertain || variation == VariationKind.INDETERMINATE || curvature == CurvatureKind.INDETERMINATE) StudyConfidence.INDETERMINATE else StudyConfidence.SAMPLED_EVIDENCE,
                "Classification fondée sur des signes échantillonnés; elle n'est pas une preuve sur tout l'intervalle.")
        }
    }

    private fun buildInflections(
        samples: List<Sample>, search: RootSearchResult?, zeroRegions: List<DerivativeZeroRegion>, probeAt: (Double) -> FunctionProbe?
    ): List<InflectionCandidate> = (search?.solutions.orEmpty().map { it.x to true } + search?.uncertainCandidates.orEmpty().map { it.x to false })
        .distinctBy { it.first.toBits() }.filterNot { (x, _) -> zeroRegions.any { it.order == DifferentiatedOrder.SECOND && x in it.interval.minimum..it.interval.maximum } }.map { (x, validated) ->
            val left = samples.filter { it.x < x }.maxByOrNull { it.x }
            val right = samples.filter { it.x > x }.minByOrNull { it.x }
            val l = left?.probe?.secondDerivative as? SearchValue.Finite
            val r = right?.probe?.secondDerivative as? SearchValue.Finite
            val changes = l != null && r != null && ((l.value < 0.0 && r.value > 0.0) || (l.value > 0.0 && r.value < 0.0))
            val evaluated = probeAt(x)
            val first = evaluated?.derivative.toStudyValue() ?: StudyValue(null, StudyValueStatus.UNAVAILABLE)
            val kind = when {
                !validated || !changes -> InflectionKind.INDETERMINATE
                first.status != StudyValueStatus.FINITE -> InflectionKind.FIRST_DERIVATIVE_UNDEFINED
                first.value == 0.0 -> InflectionKind.HORIZONTAL_TANGENT
                else -> InflectionKind.NON_HORIZONTAL_TANGENT
            }
            InflectionCandidate(x, evaluated?.value.toStudyValue() ?: StudyValue(null, StudyValueStatus.UNAVAILABLE), first,
                if (validated && changes) StudyConfidence.SAMPLED_EVIDENCE else StudyConfidence.INDETERMINATE, kind,
                if (!changes) "Un changement de convexité n'est pas établi de part et d'autre." else "Changement de signe de f'' observé; classification numérique, non preuve globale.")
        }

    private fun globalResult(minimum: Boolean, samples: List<ObservedFunctionValue>, dependsOnX: Boolean, components: List<SearchInterval>, uncertain: List<UnresolvedSearchZone>): GlobalExtremum {
        val finite = samples.filter { it.status == StudyValueStatus.FINITE && it.value != null }
        val best = if (minimum) finite.minByOrNull { it.value!! } else finite.maxByOrNull { it.value!! }
        if (best == null) return GlobalExtremum(minimum, null, null, GlobalConclusionKind.INDETERMINATE, "Aucune valeur finie exploitable.")
        if (!dependsOnX && components.size == 1 && uncertain.isEmpty())
            return GlobalExtremum(minimum, best.value, best.x, GlobalConclusionKind.ESTABLISHED_GLOBAL, "Expression structurellement indépendante de x et évaluée finie sur le domaine échantillonné.", best.provenance, StudyConfidence.STRUCTURALLY_ESTABLISHED)
        return GlobalExtremum(minimum, best.value, best.x, GlobalConclusionKind.BEST_OBSERVED,
            "Meilleure valeur finie observée; cela ne démontre pas un extremum global.", best.provenance, best.confidence)
    }

    private fun overlaps(interval: SearchInterval, a: Double, b: Double) = interval.minimum < b && interval.maximum > a
    private fun interpolate(a: Double, b: Double, t: Double) = a * (1.0 - t) + b * t
}

private fun ExpressionEvaluation.toStudyValue(): StudyValue = when (this) {
    is ExpressionEvaluation.Success -> StudyValue(value, StudyValueStatus.FINITE)
    is ExpressionEvaluation.Failure -> when (error) {
        is MathExpressionError.Domain -> StudyValue(null, StudyValueStatus.OUT_OF_DOMAIN, expressionError = error)
        is MathExpressionError.NonFiniteResult -> StudyValue(null, StudyValueStatus.NON_FINITE, expressionError = error)
        else -> StudyValue(null, StudyValueStatus.EVALUATION_ERROR, expressionError = error)
    }
}

private fun DerivativeEvaluation.toStudyValue(): StudyValue = when (this) {
    is DerivativeEvaluation.Value -> StudyValue(value, StudyValueStatus.FINITE)
    is DerivativeEvaluation.Failure -> when (reason) {
        is DerivativeFailure.FunctionOutOfDomain -> StudyValue(null, StudyValueStatus.OUT_OF_DOMAIN, derivativeFailure = reason)
        is DerivativeFailure.NotDefined -> StudyValue(null, StudyValueStatus.DERIVATIVE_NOT_DEFINED, derivativeFailure = reason)
        is DerivativeFailure.NonFinite -> StudyValue(null, StudyValueStatus.DERIVATIVE_NON_FINITE, derivativeFailure = reason)
        is DerivativeFailure.Unsupported -> StudyValue(null, StudyValueStatus.DERIVATIVE_UNSUPPORTED, derivativeFailure = reason)
        is DerivativeFailure.Indeterminate -> StudyValue(null, StudyValueStatus.DERIVATIVE_INDETERMINATE, derivativeFailure = reason)
        is DerivativeFailure.NumericallyUnstable -> StudyValue(null, StudyValueStatus.DERIVATIVE_NUMERICALLY_UNSTABLE, derivativeFailure = reason)
    }
}

private fun unavailableValue(detail: String? = null) = StudyValue(null, StudyValueStatus.UNAVAILABLE,
    expressionError = detail?.let { MathExpressionError.Domain(null, "analyse", it) })

private fun ExpressionEvaluation.toSearchValueForStudy(): SearchValue = when (this) {
    is ExpressionEvaluation.Success -> SearchValue.Finite(value)
    is ExpressionEvaluation.Failure -> when (error) {
        is MathExpressionError.Domain -> SearchValue.OutOfDomain
        is MathExpressionError.NonFiniteResult -> SearchValue.NonFinite
        else -> SearchValue.Unavailable(error.toString())
    }
}

private fun DerivativeEvaluation.toSearchValueForStudy(): SearchValue = when (this) {
    is DerivativeEvaluation.Value -> SearchValue.Finite(value)
    is DerivativeEvaluation.Failure -> when (reason) {
        is DerivativeFailure.FunctionOutOfDomain -> SearchValue.OutOfDomain
        is DerivativeFailure.NonFinite -> SearchValue.NonFinite
        else -> SearchValue.Unavailable(reason.toString())
    }
}

private fun SearchValue?.toStudyValue(): StudyValue = when (this) {
    is SearchValue.Finite -> StudyValue(value, StudyValueStatus.FINITE)
    SearchValue.OutOfDomain -> StudyValue(null, StudyValueStatus.OUT_OF_DOMAIN)
    SearchValue.NonFinite -> StudyValue(null, StudyValueStatus.NON_FINITE)
    is SearchValue.Unavailable, null -> StudyValue(null, StudyValueStatus.UNAVAILABLE)
}
