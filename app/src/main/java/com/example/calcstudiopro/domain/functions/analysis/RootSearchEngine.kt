package com.example.calcstudiopro.domain.functions.analysis

import com.example.calcstudiopro.domain.functions.CompiledMathExpression
import com.example.calcstudiopro.domain.functions.DerivativeEvaluation
import com.example.calcstudiopro.domain.functions.ExpressionEvaluation
import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionError
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ulp

/** Closed finite interval. Construction does not silently reorder or clamp endpoints. */
data class SearchInterval(val minimum: Double, val maximum: Double) {
    init { require(minimum.isFinite() && maximum.isFinite() && minimum <= maximum) }
    val isPoint: Boolean get() = minimum == maximum

    companion object {
        fun create(minimum: Double, maximum: Double): SearchInterval? =
            if (minimum.isFinite() && maximum.isFinite() && minimum <= maximum) SearchInterval(minimum, maximum) else null
    }
}

enum class RootSearchLevel(val workBudget: Long) {
    RAPIDE(4_096), STANDARD(16_384), APPROFONDI(65_536)
}

/** Each probe reports the actual scalar evaluations and its charged work units. */
sealed interface SearchValue {
    data class Finite(val value: Double) : SearchValue { init { require(value.isFinite()) } }
    data object OutOfDomain : SearchValue
    data object NonFinite : SearchValue
    data class Unavailable(val reason: String) : SearchValue
}

data class FunctionProbe(
    val value: SearchValue,
    val derivative: SearchValue? = null,
    val secondDerivative: SearchValue? = null
)

fun interface SearchCancellation { fun isCancelled(): Boolean }

private enum class SearchWorkPhase { EXPLORATION, SIGN, TANGENTIAL, VALIDATION }

interface RootSearchFunction {
    /** Scalar evaluations consumed by one probe. */
    val scalarEvaluationsPerProbe: Int get() = 1
    /** Work units include AD or multi-expression work; must be positive. */
    val workUnitsPerProbe: Int get() = scalarEvaluationsPerProbe
    /** True only when AST metadata proves independence from x. */
    val structurallyIndependentOfX: Boolean? get() = null
    fun evaluate(x: Double): FunctionProbe
}

enum class RootDiscoveryMethod { ENDPOINT, EXACT_SAMPLE, SIGN_BRACKET_BISECTION, TANGENTIAL_MINIMUM }
enum class CandidateValidation { VALIDATED_NUMERICALLY, UNCERTAIN }

data class RootSolution(
    val x: Double,
    val residual: Double,
    val validation: CandidateValidation,
    val method: RootDiscoveryMethod,
    /** A sign-changing finite bracket only; null for unbracketed/tangential candidates. */
    val bracket: SearchInterval? = null
)

data class UnresolvedSearchZone(val interval: SearchInterval, val reason: String)
data class RejectedRootCandidate(val interval: SearchInterval, val reason: String)

enum class SearchStopReason { COMPLETED_NUMERICALLY, BUDGET_EXHAUSTED, ITERATION_LIMIT_REACHED, CANCELLED, RESULT_LIMIT_REACHED, DOMAIN_UNEXPLOITABLE }
enum class NonIsolatedSolutions { NOT_SUSPECTED, SUSPECTED, IDENTICALLY_ZERO_PROVEN }

data class RootSearchStatistics(
    val scalarEvaluations: Int,
    val workUnitsConsumed: Long,
    val workBudget: Long,
    val candidatesDetected: Int,
    val solutionsConserved: Int,
    val explorationSamples: Int,
    val explorationWorkUnits: Long = 0,
    val signRefinementWorkUnits: Long = 0,
    val tangentialWorkUnits: Long = 0,
    val validationWorkUnits: Long = 0
) {
    val accountedWorkUnits: Long get() = explorationWorkUnits + signRefinementWorkUnits + tangentialWorkUnits + validationWorkUnits
}

data class RootSearchResult(
    val interval: SearchInterval,
    val solutions: List<RootSolution>,
    val uncertainCandidates: List<RootSolution>,
    val unresolvedZones: List<UnresolvedSearchZone>,
    val stopReason: SearchStopReason,
    val nonIsolatedSolutions: NonIsolatedSolutions,
    val statistics: RootSearchStatistics,
    val message: String,
    val rejectedCandidates: List<RejectedRootCandidate> = emptyList()
) {
    val isPartial: Boolean get() = stopReason != SearchStopReason.COMPLETED_NUMERICALLY || unresolvedZones.isNotEmpty()
}

sealed interface RootSearchRequestResult {
    data class Completed(val result: RootSearchResult) : RootSearchRequestResult
    data object InvalidInterval : RootSearchRequestResult
}

/** Deterministic, bounded numerical search. It discovers candidates; it is not a proof of completeness. */
class RootSearchEngine(
    private val absoluteXTolerance: Double = 0.0,
    private val relativeXTolerance: Double = 1e-10,
    private val relativeResidualTolerance: Double = 1e-10,
    private val maximumSolutions: Int = 1_000
) {
    init {
        require(absoluteXTolerance >= 0.0 && absoluteXTolerance.isFinite())
        require(relativeXTolerance > 0.0 && relativeXTolerance.isFinite())
        require(relativeResidualTolerance > 0.0 && relativeResidualTolerance.isFinite())
        require(maximumSolutions in 1..1_000)
    }

    fun search(
        function: RootSearchFunction,
        minimum: Double,
        maximum: Double,
        level: RootSearchLevel = RootSearchLevel.STANDARD,
        cancellation: SearchCancellation = SearchCancellation { false }
    ): RootSearchRequestResult {
        val interval = SearchInterval.create(minimum, maximum) ?: return RootSearchRequestResult.InvalidInterval
        require(function.scalarEvaluationsPerProbe > 0 && function.workUnitsPerProbe > 0)
        val budget = level.workBudget
        var evaluations = 0
        var work = 0L
        var candidates = 0
        var explorationSamples = 0
        var explorationWork = 0L
        var signWork = 0L
        var tangentWork = 0L
        var validationWork = 0L
        var phaseLimitReached = false
        val explorationTarget = (budget / 4L).coerceAtLeast(function.workUnitsPerProbe.toLong())
        var postExploration = (budget - explorationTarget).coerceAtLeast(0L)
        var signAllowance = postExploration * 3L / 5L
        var tangentAllowance = postExploration - signAllowance
        var stop = SearchStopReason.COMPLETED_NUMERICALLY
        val unresolved = mutableListOf<UnresolvedSearchZone>()
        val rejected = mutableListOf<RejectedRootCandidate>()
        val solutions = mutableListOf<RootSolution>()
        val uncertainCandidates = mutableListOf<RootSolution>()
        val cache = HashMap<Long, FunctionProbe>()

        fun probe(x: Double, phase: SearchWorkPhase = SearchWorkPhase.EXPLORATION): FunctionProbe? {
            if (cancellation.isCancelled()) { stop = SearchStopReason.CANCELLED; return null }
            val key = x.toBits()
            cache[key]?.let { return it }
            if ((phase == SearchWorkPhase.SIGN && signWork + function.workUnitsPerProbe > signAllowance) ||
                (phase == SearchWorkPhase.VALIDATION && signWork + validationWork + function.workUnitsPerProbe > signAllowance) ||
                (phase == SearchWorkPhase.TANGENTIAL && tangentWork + function.workUnitsPerProbe > tangentAllowance)) {
                phaseLimitReached = true
                return null
            }
            if (work + function.workUnitsPerProbe > budget) { stop = SearchStopReason.BUDGET_EXHAUSTED; return null }
            val p = try { function.evaluate(x) } catch (_: ArithmeticException) {
                FunctionProbe(SearchValue.NonFinite)
            } catch (_: RuntimeException) {
                FunctionProbe(SearchValue.Unavailable("Échec de l'évaluateur"))
            }
            evaluations += function.scalarEvaluationsPerProbe
            work += function.workUnitsPerProbe
            when (phase) {
                SearchWorkPhase.EXPLORATION -> explorationWork += function.workUnitsPerProbe
                SearchWorkPhase.SIGN -> signWork += function.workUnitsPerProbe
                SearchWorkPhase.TANGENTIAL -> tangentWork += function.workUnitsPerProbe
                SearchWorkPhase.VALIDATION -> validationWork += function.workUnitsPerProbe
            }
            cache[key] = p
            return p
        }

        fun addSolution(candidate: RootSolution) {
            val existingIndex = solutions.indexOfFirst { sameRoot(it, candidate) }
            if (existingIndex >= 0) {
                val old = solutions[existingIndex]
                if (candidate.validation == CandidateValidation.VALIDATED_NUMERICALLY &&
                    (old.validation != CandidateValidation.VALIDATED_NUMERICALLY || abs(candidate.residual) < abs(old.residual))) {
                    solutions[existingIndex] = candidate
                }
                return
            }
            if (solutions.size >= maximumSolutions) { stop = SearchStopReason.RESULT_LIMIT_REACHED; return }
            solutions += candidate
        }

        fun recordUncertain(candidate: RootSolution) {
            if (solutions.any { sameRoot(it, candidate) }) return
            val existingIndex = uncertainCandidates.indexOfFirst { sameRoot(it, candidate) }
            if (existingIndex < 0) uncertainCandidates += candidate
            else if (abs(candidate.residual) < abs(uncertainCandidates[existingIndex].residual)) uncertainCandidates[existingIndex] = candidate
        }

        if (interval.isPoint) {
            val p = probe(minimum)
            if (p == null) return result(interval, solutions, uncertainCandidates, unresolved, stop, NonIsolatedSolutions.NOT_SUSPECTED, evaluations, work, budget, candidates, explorationSamples, cancellation = cancellation,
                explorationWorkUnits = explorationWork, signRefinementWorkUnits = signWork, tangentialWorkUnits = tangentWork, validationWorkUnits = validationWork)
            explorationSamples++
            when (val v = p.value) {
                is SearchValue.Finite -> if (v.value == 0.0) {
                    candidates++
                    addSolution(RootSolution(minimum, 0.0, CandidateValidation.VALIDATED_NUMERICALLY, RootDiscoveryMethod.ENDPOINT))
                }
                SearchValue.OutOfDomain -> unresolved += zone(interval, "Domaine non défini au point")
                SearchValue.NonFinite -> unresolved += zone(interval, "Résultat non fini au point")
                is SearchValue.Unavailable -> unresolved += zone(interval, v.reason)
            }
            if (solutions.isEmpty()) stop = SearchStopReason.DOMAIN_UNEXPLOITABLE.takeIf { unresolved.isNotEmpty() } ?: stop
            return result(interval, solutions, uncertainCandidates, unresolved, stop, NonIsolatedSolutions.NOT_SUSPECTED, evaluations, work, budget, candidates, explorationSamples, cancellation = cancellation,
                explorationWorkUnits = explorationWork, signRefinementWorkUnits = signWork, tangentialWorkUnits = tangentWork, validationWorkUnits = validationWork)
        }

        // Only structural metadata can prove a constant-in-x function. Probe the closed interval's endpoints.
        if (function.structurallyIndependentOfX == true) {
            val left = probe(minimum)
            val right = if (left != null) probe(maximum) else null
            if (left != null && right != null && left.value is SearchValue.Finite && right.value is SearchValue.Finite) {
                val lv = (left.value as SearchValue.Finite).value
                val rv = (right.value as SearchValue.Finite).value
                if (lv == 0.0 && rv == 0.0) {
                    return result(interval, emptyList(), emptyList(), emptyList(), stop, NonIsolatedSolutions.IDENTICALLY_ZERO_PROVEN,
                        evaluations, work, budget, 1, 2, cancellation = cancellation,
                        explorationWorkUnits = explorationWork, signRefinementWorkUnits = signWork, tangentialWorkUnits = tangentWork, validationWorkUnits = validationWork)
                }
                if (lv == rv && lv != 0.0) {
                    return result(interval, emptyList(), emptyList(), emptyList(), stop, NonIsolatedSolutions.NOT_SUSPECTED,
                        evaluations, work, budget, 0, 2, "Aucune solution détectée", cancellation,
                        explorationWorkUnits = explorationWork, signRefinementWorkUnits = signWork, tangentialWorkUnits = tangentWork, validationWorkUnits = validationWork)
                }
            } else if (stop != SearchStopReason.COMPLETED_NUMERICALLY) {
                return result(interval, solutions, uncertainCandidates, unresolved, stop, NonIsolatedSolutions.NOT_SUSPECTED, evaluations, work, budget, candidates, explorationSamples, cancellation = cancellation,
                    explorationWorkUnits = explorationWork, signRefinementWorkUnits = signWork, tangentialWorkUnits = tangentWork, validationWorkUnits = validationWork)
            }
        }

        // Stratified global exploration reserves budget for subsequent refinement and avoids left-first starvation.
        val desiredIntervals = min(1_024, max(32, (budget / (function.workUnitsPerProbe * 4L)).toInt()))
        val sampleCount = min(desiredIntervals + 1, ((budget - work) / function.workUnitsPerProbe).toInt())
        val explorationBudgetLimited = sampleCount < desiredIntervals + 1
        if (sampleCount < 2) stop = SearchStopReason.BUDGET_EXHAUSTED
        val xs = ArrayList<Double>(max(0, sampleCount))
        val probes = ArrayList<FunctionProbe?>(max(0, sampleCount))
        if (sampleCount >= 2) {
            for (i in 0 until sampleCount) {
                if (stop != SearchStopReason.COMPLETED_NUMERICALLY) break
                val fraction = i.toDouble() / (sampleCount - 1).toDouble()
                val x = if (i == 0) minimum else if (i == sampleCount - 1) maximum else interpolate(minimum, maximum, fraction)
                xs += x
                probes += probe(x)
                explorationSamples++
            }
        }

        if (probes.size == sampleCount && probes.isNotEmpty() && probes.all { (it?.value as? SearchValue.Finite)?.value == 0.0 }) {
            val status = if (function.structurallyIndependentOfX == true)
                NonIsolatedSolutions.IDENTICALLY_ZERO_PROVEN else NonIsolatedSolutions.SUSPECTED
            return result(interval, emptyList(), emptyList(), emptyList(), stop, status,
                evaluations, work, budget, 1, explorationSamples,
                if (status == NonIsolatedSolutions.IDENTICALLY_ZERO_PROVEN) "Solutions non isolées : fonction identiquement nulle"
                else "Solutions potentiellement non isolées", cancellation,
                explorationWorkUnits = explorationWork, signRefinementWorkUnits = signWork, tangentialWorkUnits = tangentWork, validationWorkUnits = validationWork)
        }

        val brackets = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until probes.lastIndex.coerceAtLeast(0)) {
            val a = probes[i]?.value
            val b = probes[i + 1]?.value
            when {
                a is SearchValue.Finite && b is SearchValue.Finite -> {
                    if (a.value == 0.0) {
                        candidates++
                        addSolution(RootSolution(xs[i], 0.0, CandidateValidation.VALIDATED_NUMERICALLY, if (i == 0) RootDiscoveryMethod.ENDPOINT else RootDiscoveryMethod.EXACT_SAMPLE))
                    }
                    if (oppositeSigns(a.value, b.value)) { brackets += xs[i] to xs[i + 1]; candidates++ }
                }
                else -> unresolved += zone(SearchInterval.create(xs[i], xs[i + 1])!!, when {
                    a == SearchValue.OutOfDomain || b == SearchValue.OutOfDomain -> "Domaine partiellement ou entièrement non défini"
                    a == SearchValue.NonFinite || b == SearchValue.NonFinite -> "Résultat non fini dans cette zone"
                    else -> "Évaluation indisponible dans cette zone"
                })
            }
            if (stop == SearchStopReason.RESULT_LIMIT_REACHED) break
        }
        if (xs.lastOrNull() == maximum && probes.lastOrNull()?.value is SearchValue.Finite &&
            (probes.last()!!.value as SearchValue.Finite).value == 0.0) {
            candidates++
            addSolution(RootSolution(maximum, 0.0, CandidateValidation.VALIDATED_NUMERICALLY, RootDiscoveryMethod.ENDPOINT))
        }

        data class TangentCandidate(val left: Double, val right: Double, val x: Double, val value: Double, val localScale: Double, val score: Double)
        val tangentIntervals = mutableListOf<TangentCandidate>()
        for (i in 1 until probes.lastIndex) {
            val prev = probes[i - 1]?.value as? SearchValue.Finite ?: continue
            val here = probes[i]?.value as? SearchValue.Finite ?: continue
            val next = probes[i + 1]?.value as? SearchValue.Finite ?: continue
            val localMin = abs(here.value) <= abs(prev.value) && abs(here.value) <= abs(next.value) &&
                (abs(here.value) < abs(prev.value) || abs(here.value) < abs(next.value))
            val derivative = probes[i]?.derivative as? SearchValue.Finite
            val derivativeSignCandidate = derivative != null && run {
                val dl = probes[i - 1]?.derivative as? SearchValue.Finite
                val dr = probes[i + 1]?.derivative as? SearchValue.Finite
                dl != null && dr != null && (oppositeSigns(dl.value, derivative.value) || oppositeSigns(derivative.value, dr.value) || derivative.value == 0.0)
            }
            val noSignChangeAcrossCandidate = !oppositeSigns(prev.value, next.value)
            if (here.value != 0.0 && noSignChangeAcrossCandidate && (localMin || derivativeSignCandidate)) {
                val neighborhood = max(abs(prev.value), abs(next.value))
                val score = if (neighborhood > 0.0) abs(here.value) / neighborhood else abs(here.value)
                // A tangent-root candidate must be meaningfully closer to zero than its neighbors.
                // This removes ordinary |f| extrema (for example sine peaks), while preserving unresolved flat minima.
                if (score < 0.5) {
                    tangentIntervals += TangentCandidate(xs[i - 1], xs[i + 1], xs[i], here.value, neighborhood, score)
                }
            }
        }
        // Adjacent sampled minima can be two observations of one unresolved minimum between samples.
        // Merge only their overlapping search neighborhoods; validated roots are consolidated separately.
        val consolidatedTangents = mutableListOf<TangentCandidate>()
        for (candidate in tangentIntervals.sortedBy { it.x }) {
            val previous = consolidatedTangents.lastOrNull()
            val halfSpan = previous?.let { halfDistance(it.left, it.right) } ?: 0.0
            if (previous != null && candidate.left <= previous.right && abs(candidate.x - previous.x) <= halfSpan) {
                val best = if (candidate.score < previous.score) candidate else previous
                consolidatedTangents[consolidatedTangents.lastIndex] = TangentCandidate(
                    min(previous.left, candidate.left), max(previous.right, candidate.right),
                    best.x, best.value, max(previous.localScale, candidate.localScale),
                    min(previous.score, candidate.score)
                )
            } else consolidatedTangents += candidate
        }
        tangentIntervals.clear()
        tangentIntervals += consolidatedTangents
        candidates += tangentIntervals.size
        // Lend the unused category its share when exploration proves that category has no candidates.
        postExploration = (budget - explorationWork).coerceAtLeast(0L)
        when {
            brackets.isEmpty() -> { signAllowance = 0L; tangentAllowance = postExploration }
            tangentIntervals.isEmpty() -> { signAllowance = postExploration; tangentAllowance = 0L }
            else -> { signAllowance = postExploration * 3L / 5L; tangentAllowance = postExploration - signAllowance }
        }

        // Refine one bisection step per bracket per round, so a dense region cannot consume the whole refinement budget first.
        data class ActiveBracket(
            var a: Double, var b: Double, var fa: Double, var fb: Double, val scale: Double,
            var bestX: Double, var bestValue: Double
        )
        val active = brackets.mapNotNull { (left, right) ->
            val fa = (probe(left, SearchWorkPhase.SIGN)?.value as? SearchValue.Finite)?.value
            val fb = (probe(right, SearchWorkPhase.SIGN)?.value as? SearchValue.Finite)?.value
            if (fa != null && fb != null && oppositeSigns(fa, fb)) {
                val bestX = if (abs(fa) <= abs(fb)) left else right
                val bestValue = if (abs(fa) <= abs(fb)) fa else fb
                ActiveBracket(left, right, fa, fb, max(abs(fa), abs(fb)), bestX, bestValue)
            } else null
        }.toMutableList()
        repeat(128) {
            if (active.isEmpty() || stop != SearchStopReason.COMPLETED_NUMERICALLY) return@repeat
            for (bracket in active.toList()) {
                if (stop != SearchStopReason.COMPLETED_NUMERICALLY) break
                if (halfDistance(bracket.a, bracket.b) <= xTolerance(bracket.a, bracket.b, interval) / 2.0 ||
                    Math.nextAfter(bracket.a, bracket.b) >= bracket.b) {
                    val x = midpoint(bracket.a, bracket.b)
                    val fx = (probe(x, SearchWorkPhase.VALIDATION)?.value as? SearchValue.Finite)?.value
                    if (fx != null && scaledResidualAcceptable(fx, bracket.scale)) {
                        addSolution(RootSolution(x, fx, CandidateValidation.VALIDATED_NUMERICALLY,
                            RootDiscoveryMethod.SIGN_BRACKET_BISECTION, SearchInterval.create(bracket.a, bracket.b)))
                        active.remove(bracket)
                        continue
                    } else {
                        if (Math.nextAfter(bracket.a, bracket.b) >= bracket.b) {
                            val enclosure = SearchInterval.create(bracket.a, bracket.b)!!
                            val reason = "Encadrement numérique sans résidu validable (discontinuité suspecte)"
                            rejected += RejectedRootCandidate(enclosure, reason)
                            unresolved += zone(enclosure, reason)
                            active.remove(bracket)
                            continue
                        }
                    }
                }
                val magnitudeScale = max(abs(bracket.fa), abs(bracket.fb))
                val scaledA = abs(bracket.fa) / magnitudeScale
                val scaledB = abs(bracket.fb) / magnitudeScale
                val secantFraction = scaledA / (scaledA + scaledB)
                val m = if (secantFraction in 0.2..0.8) interpolate(bracket.a, bracket.b, secantFraction)
                    else midpoint(bracket.a, bracket.b)
                if (m == bracket.a || m == bracket.b) {
                    unresolved += zone(SearchInterval.create(bracket.a, bracket.b)!!, "Stagnation en précision Double")
                    active.remove(bracket)
                    continue
                }
                val pm = probe(m, SearchWorkPhase.SIGN)
                val fm = (pm?.value as? SearchValue.Finite)?.value
                if (fm == null) {
                    if (pm == null && stop == SearchStopReason.COMPLETED_NUMERICALLY && phaseLimitReached) {
                        break
                    }
                    val enclosure = SearchInterval.create(bracket.a, bracket.b)!!
                    val reason = "Domaine interrompu dans l'encadrement"
                    rejected += RejectedRootCandidate(enclosure, reason)
                    unresolved += zone(enclosure, reason)
                    active.remove(bracket)
                } else if (fm == 0.0) {
                    addSolution(RootSolution(m, 0.0, CandidateValidation.VALIDATED_NUMERICALLY,
                        RootDiscoveryMethod.SIGN_BRACKET_BISECTION, SearchInterval.create(bracket.a, bracket.b)))
                    active.remove(bracket)
                } else if (oppositeSigns(bracket.fa, fm)) {
                    if (abs(fm) < abs(bracket.bestValue)) { bracket.bestX = m; bracket.bestValue = fm }
                    bracket.b = m; bracket.fb = fm
                } else {
                    if (abs(fm) < abs(bracket.bestValue)) { bracket.bestX = m; bracket.bestValue = fm }
                    bracket.a = m; bracket.fa = fm
                }
                if (stop == SearchStopReason.RESULT_LIMIT_REACHED) break
            }
        }
        if (active.isNotEmpty() && stop == SearchStopReason.COMPLETED_NUMERICALLY) {
            stop = if (phaseLimitReached) SearchStopReason.BUDGET_EXHAUSTED else SearchStopReason.ITERATION_LIMIT_REACHED
        }
        active.forEach {
            val enclosure = SearchInterval.create(it.a, it.b)!!
            val reason = if (stop == SearchStopReason.COMPLETED_NUMERICALLY) "Raffinement au plafond d'itérations" else "Raffinement interrompu"
            unresolved += zone(enclosure, reason)
            recordUncertain(RootSolution(it.bestX, it.bestValue, CandidateValidation.UNCERTAIN,
                RootDiscoveryMethod.SIGN_BRACKET_BISECTION, enclosure))
        }

        // Spend the reserved tangential share on the most root-like minima first, with bounded per-candidate work.
        val orderedTangents = tangentIntervals.sortedBy { it.score }
        val tangentCandidateLimit = if (orderedTangents.isEmpty()) 0 else
            (tangentAllowance / (function.workUnitsPerProbe * 128L)).toInt().coerceAtLeast(1)
        var tangentialBudgetLimited = false
        for ((candidateIndex, candidate) in orderedTangents.withIndex()) {
            if (stop != SearchStopReason.COMPLETED_NUMERICALLY) break
            val left = candidate.left
            val right = candidate.right
            if (candidateIndex >= tangentCandidateLimit) {
                recordUncertain(RootSolution(candidate.x, candidate.value, CandidateValidation.UNCERTAIN,
                    RootDiscoveryMethod.TANGENTIAL_MINIMUM, SearchInterval.create(left, right)))
                unresolved += zone(SearchInterval.create(left, right)!!, "Candidat tangentiel non raffiné : budget réservé épuisé")
                tangentialBudgetLimited = true
                continue
            }
            var candidateWork = 0L
            val perCandidateAllowance = tangentAllowance / tangentCandidateLimit
            val candidateProbe: (Double) -> FunctionProbe? = { q ->
                if (candidateWork + function.workUnitsPerProbe > perCandidateAllowance) null
                else probe(q, SearchWorkPhase.TANGENTIAL)?.also { candidateWork += function.workUnitsPerProbe }
            }
            val refined = minimizeAbs(left, right, candidateProbe, interval)
            if (refined == null) {
                if (stop == SearchStopReason.COMPLETED_NUMERICALLY) {
                    uncertainCandidates += RootSolution(candidate.x, candidate.value, CandidateValidation.UNCERTAIN,
                        RootDiscoveryMethod.TANGENTIAL_MINIMUM, SearchInterval.create(left, right))
                    unresolved += zone(SearchInterval.create(left, right)!!, "Minimum local non résolu")
                }
                continue
            }
            val (x, value, converged, enclosure) = refined
            if (value == 0.0) {
                addSolution(RootSolution(x, value, CandidateValidation.VALIDATED_NUMERICALLY,
                    RootDiscoveryMethod.TANGENTIAL_MINIMUM, enclosure))
                continue
            }
            if (!converged) {
                recordUncertain(RootSolution(x, value, CandidateValidation.UNCERTAIN, RootDiscoveryMethod.TANGENTIAL_MINIMUM, enclosure))
                unresolved += zone(enclosure, "Minimisation tangentielle non convergée")
                continue
            }
            val probeAtMinimum = candidateProbe(x)
            if (probeAtMinimum == null && stop == SearchStopReason.COMPLETED_NUMERICALLY) {
                recordUncertain(RootSolution(x, value, CandidateValidation.UNCERTAIN, RootDiscoveryMethod.TANGENTIAL_MINIMUM, enclosure))
                unresolved += zone(enclosure, "Budget tangentiel épuisé avant validation")
                break
            }
            val derivative = (probeAtMinimum?.derivative as? SearchValue.Finite)?.value
            val exactZero = value == 0.0
            if (exactZero) {
                addSolution(RootSolution(x, value, CandidateValidation.VALIDATED_NUMERICALLY,
                    RootDiscoveryMethod.TANGENTIAL_MINIMUM, enclosure))
            } else if (derivative == 0.0 &&
                abs(value) <= tangentRoundoffBound(probeAtMinimum.secondDerivative, enclosure, x, value)) {
                addSolution(RootSolution(x, value, CandidateValidation.VALIDATED_NUMERICALLY,
                    RootDiscoveryMethod.TANGENTIAL_MINIMUM, enclosure))
            } else if (abs(value) <= localResidualThreshold(candidate.localScale, value)) {
                recordUncertain(RootSolution(x, value, CandidateValidation.UNCERTAIN, RootDiscoveryMethod.TANGENTIAL_MINIMUM, enclosure))
                unresolved += zone(SearchInterval.create(left, right)!!,
                    "Minimum proche de zéro : racine tangentielle non confirmée")
            } else if (derivative == 0.0) {
                rejected += RejectedRootCandidate(SearchInterval.create(left, right)!!,
                    "Minimum strictement positif observé ; exclu des racines validées")
            } else {
                rejected += RejectedRootCandidate(SearchInterval.create(left, right)!!,
                    "Minimum local non nul, sans stationnarité confirmée")
            }
        }

        val finiteSampleCount = probes.count { it?.value is SearchValue.Finite }
        if (stop == SearchStopReason.COMPLETED_NUMERICALLY && (explorationBudgetLimited || phaseLimitReached || tangentialBudgetLimited))
            stop = SearchStopReason.BUDGET_EXHAUSTED
        if (stop == SearchStopReason.COMPLETED_NUMERICALLY && finiteSampleCount == 0 && unresolved.isNotEmpty())
            stop = SearchStopReason.DOMAIN_UNEXPLOITABLE
        uncertainCandidates.removeAll { uncertain -> solutions.any { validated -> sameRoot(validated, uncertain) } }
        val nonIsolated = NonIsolatedSolutions.NOT_SUSPECTED
        return result(interval, solutions.sortedBy { it.x }, uncertainCandidates.sortedBy { it.x },
            unresolved.distinctBy { it.interval to it.reason }, stop, nonIsolated, evaluations, work, budget, candidates, explorationSamples,
            cancellation = cancellation, rejected = rejected, explorationWorkUnits = explorationWork,
            signRefinementWorkUnits = signWork, tangentialWorkUnits = tangentWork, validationWorkUnits = validationWork)
    }

    private data class MinimumResult(val x: Double, val value: Double, val converged: Boolean, val enclosure: SearchInterval)

    private fun minimizeAbs(left: Double, right: Double, probe: (Double) -> FunctionProbe?, whole: SearchInterval): MinimumResult? {
        val ratio = (kotlin.math.sqrt(5.0) - 1.0) / 2.0
        var a = left; var b = right
        var c = interpolate(a, b, 1.0 - ratio); var d = interpolate(a, b, ratio)
        fun absValue(x: Double) = (probe(x)?.value as? SearchValue.Finite)?.value?.let(::abs)
        var fc = absValue(c) ?: return null; var fd = absValue(d) ?: return null
        var converged = false
        for (iteration in 0 until 128) {
            if (Math.nextAfter(a, b) >= b) { converged = true; break }
            if (fc <= fd) { b = d; d = c; fd = fc; c = interpolate(a, b, 1.0 - ratio); fc = absValue(c) ?: return null }
            else { a = c; c = d; fc = fd; d = interpolate(a, b, ratio); fd = absValue(d) ?: return null }
            if (c == a || c == b || d == a || d == b) break
        }
        val x = if (fc <= fd) c else d
        val value = (probe(x)?.value as? SearchValue.Finite)?.value ?: return null
        return MinimumResult(x, value, converged, SearchInterval.create(a, b)!!)
    }

    private fun localResidualThreshold(scale: Double, value: Double): Double {
        // Residual is a consistency filter only; it never confirms a tangential root.
        return max(4.0 * scale.ulp, 4.0 * abs(value).ulp)
    }

    private fun tangentRoundoffBound(secondDerivative: SearchValue?, enclosure: SearchInterval, x: Double, value: Double): Double {
        val curvature = (secondDerivative as? SearchValue.Finite)?.value?.let(::abs) ?: return 0.0
        val radius = max(halfDistance(enclosure.minimum, enclosure.maximum), max(x.ulp * 2.0, Double.MIN_VALUE))
        val quadraticUncertainty = 0.5 * curvature * radius * radius
        return max(quadraticUncertainty, 8.0 * abs(value).ulp)
    }

    private fun scaledResidualAcceptable(residual: Double, scale: Double): Boolean {
        if (!residual.isFinite() || !scale.isFinite() || scale <= 0.0) return residual == 0.0
        val tolerance = max(64.0 * scale.ulp, relativeResidualTolerance * scale)
        return abs(residual) <= tolerance
    }

    private fun xTolerance(a: Double, b: Double, whole: SearchInterval): Double {
        val scale = max(abs(a), abs(b))
        val absolute = if (absoluteXTolerance > 0.0) absoluteXTolerance else 0.0
        return max(absolute, max(relativeXTolerance * scale, max(a.ulp, b.ulp) * 4.0))
    }

    private fun sameRoot(a: RootSolution, b: RootSolution): Boolean {
        val aBracket = a.bracket
        val bBracket = b.bracket
        if (aBracket != null && bBracket != null) {
            val overlapMinimum = max(aBracket.minimum, bBracket.minimum)
            val overlapMaximum = min(aBracket.maximum, bBracket.maximum)
            val enclosureWidth = overlapMaximum - overlapMinimum
            val ulpWidth = max(a.x.ulp, b.x.ulp) * 8.0
            if (overlapMinimum <= overlapMaximum && enclosureWidth <= ulpWidth &&
                abs(a.x - b.x) <= ulpWidth) return true
        } else if (aBracket != null && b.x in aBracket.minimum..aBracket.maximum &&
            aBracket.maximum - aBracket.minimum <= max(a.x.ulp, b.x.ulp) * 8.0) return true
        else if (bBracket != null && a.x in bBracket.minimum..bBracket.maximum &&
            bBracket.maximum - bBracket.minimum <= max(a.x.ulp, b.x.ulp) * 8.0) return true
        // Without overlapping certified enclosures, proximity alone is not enough to merge distinct roots.
        val tolerance = max(absoluteXTolerance, max(a.x.ulp, b.x.ulp) * 4.0)
        return abs(a.x - b.x) <= tolerance
    }

    private fun result(
        interval: SearchInterval, solutions: List<RootSolution>, uncertain: List<RootSolution>, unresolved: List<UnresolvedSearchZone>, stop: SearchStopReason,
        nonIsolated: NonIsolatedSolutions, evaluations: Int, work: Long, budget: Long, candidates: Int, samples: Int,
        messageOverride: String? = null,
        cancellation: SearchCancellation = SearchCancellation { false },
        rejected: List<RejectedRootCandidate> = emptyList(),
        explorationWorkUnits: Long = 0,
        signRefinementWorkUnits: Long = 0,
        tangentialWorkUnits: Long = 0,
        validationWorkUnits: Long = 0
    ): RootSearchRequestResult.Completed {
        val finalStop = if (cancellation.isCancelled()) SearchStopReason.CANCELLED else stop
        return RootSearchRequestResult.Completed(RootSearchResult(interval, solutions.toList(), uncertain.toList(), unresolved.toList(), finalStop, nonIsolated,
        RootSearchStatistics(evaluations, work, budget, candidates, solutions.size, samples,
            explorationWorkUnits, signRefinementWorkUnits, tangentialWorkUnits, validationWorkUnits), messageOverride ?: when {
            nonIsolated == NonIsolatedSolutions.IDENTICALLY_ZERO_PROVEN -> "Solutions non isolées : fonction identiquement nulle"
            finalStop == SearchStopReason.CANCELLED -> "Recherche annulée"
            finalStop == SearchStopReason.BUDGET_EXHAUSTED -> "Recherche partielle : budget épuisé"
            finalStop == SearchStopReason.RESULT_LIMIT_REACHED -> "Recherche partielle : limite de résultats atteinte"
            finalStop == SearchStopReason.DOMAIN_UNEXPLOITABLE -> "Domaine inexploitable sur l'intervalle"
            solutions.isEmpty() && unresolved.isNotEmpty() -> "Recherche partielle : aucune solution détectée dans les zones exploitables"
            solutions.isEmpty() -> "Aucune solution détectée"
            unresolved.isNotEmpty() -> "Recherche partielle : certaines zones restent indéterminées"
            else -> "Solutions détectées numériquement"
        }, rejected.toList()))
    }

    private fun zone(interval: SearchInterval, reason: String) = UnresolvedSearchZone(interval, reason)
    private fun oppositeSigns(a: Double, b: Double) = (a < 0.0 && b > 0.0) || (a > 0.0 && b < 0.0)
    private fun midpoint(a: Double, b: Double) = a / 2.0 + b / 2.0
    private fun interpolate(a: Double, b: Double, t: Double) = a * (1.0 - t) + b * t
    private fun halfDistance(a: Double, b: Double) = b / 2.0 - a / 2.0
}

/** Adapter for a compiled expression. AD work is charged as three scalar units per point. */
class CompiledExpressionSearchFunction(
    private val expression: CompiledMathExpression,
    parameters: Map<Char, Double> = emptyMap(),
    private val angleMode: FunctionAngleMode = FunctionAngleMode.RAD
) : RootSearchFunction {
    private val parameters = parameters.toMap()
    override val workUnitsPerProbe = 3
    override val structurallyIndependentOfX get() = !expression.metadata.dependsOnX
    override fun evaluate(x: Double): FunctionProbe {
        val result = expression.evaluateDifferentiated(x, parameters, angleMode)
        return FunctionProbe(result.value.toSearchValue(), result.firstDerivative.toSearchValue(), result.secondDerivative.toSearchValue())
    }
}

/** f-g adapter. Both scalar evaluations are explicitly charged; derivative is supplied only if both jets are valid. */
class ExpressionDifferenceSearchFunction(
    private val left: CompiledMathExpression,
    private val right: CompiledMathExpression,
    parameters: Map<Char, Double> = emptyMap(),
    private val angleMode: FunctionAngleMode = FunctionAngleMode.RAD
) : RootSearchFunction {
    private val parameters = parameters.toMap()
    override val scalarEvaluationsPerProbe = 2
    override val workUnitsPerProbe = 6
    override val structurallyIndependentOfX get() = !left.metadata.dependsOnX && !right.metadata.dependsOnX
    override fun evaluate(x: Double): FunctionProbe {
        val a = left.evaluateDifferentiated(x, parameters, angleMode)
        val b = right.evaluateDifferentiated(x, parameters, angleMode)
        val av = a.value.toSearchValue(); val bv = b.value.toSearchValue()
        val value = if (av is SearchValue.Finite && bv is SearchValue.Finite) finiteDifference(av.value, bv.value) else combineFailure(av, bv)
        val ad = a.firstDerivative.toSearchValue(); val bd = b.firstDerivative.toSearchValue()
        val derivative = if (ad is SearchValue.Finite && bd is SearchValue.Finite) finiteDifference(ad.value, bd.value) else combineFailure(ad, bd)
        val a2 = a.secondDerivative.toSearchValue(); val b2 = b.secondDerivative.toSearchValue()
        val second = if (a2 is SearchValue.Finite && b2 is SearchValue.Finite) finiteDifference(a2.value, b2.value) else combineFailure(a2, b2)
        return FunctionProbe(value, derivative, second)
    }
}

enum class SearchedDerivativeOrder { FIRST, SECOND }

/** Search f' or f''. For f', f'' is available as an auxiliary derivative; f'' has no third-order aid. */
class ExpressionDerivativeSearchFunction(
    private val expression: CompiledMathExpression,
    private val order: SearchedDerivativeOrder,
    parameters: Map<Char, Double> = emptyMap(),
    private val angleMode: FunctionAngleMode = FunctionAngleMode.RAD
) : RootSearchFunction {
    private val parameters = parameters.toMap()
    override val workUnitsPerProbe = 3
    override val structurallyIndependentOfX get() = !expression.metadata.dependsOnX
    override fun evaluate(x: Double): FunctionProbe {
        val differentiated = expression.evaluateDifferentiated(x, parameters, angleMode)
        val searched = if (order == SearchedDerivativeOrder.FIRST) differentiated.firstDerivative else differentiated.secondDerivative
        val auxiliary = if (order == SearchedDerivativeOrder.FIRST) differentiated.secondDerivative else null
        // The jet is only second order for f itself; searching f' has no f''' curvature aid.
        return FunctionProbe(searched.toSearchValue(), auxiliary.toSearchValueOrNull())
    }
}

private fun ExpressionEvaluation.toSearchValue(): SearchValue = when (this) {
    is ExpressionEvaluation.Success -> if (value.isFinite()) SearchValue.Finite(value) else SearchValue.NonFinite
    is ExpressionEvaluation.Failure -> when (error) {
        is MathExpressionError.Domain -> SearchValue.OutOfDomain
        is MathExpressionError.NonFiniteResult -> SearchValue.NonFinite
        else -> SearchValue.Unavailable(error.toString())
    }
}

private fun DerivativeEvaluation.toSearchValue(): SearchValue = when (this) {
    is DerivativeEvaluation.Value -> SearchValue.Finite(value)
    is DerivativeEvaluation.Failure -> when (reason) {
        is com.example.calcstudiopro.domain.functions.DerivativeFailure.FunctionOutOfDomain -> SearchValue.OutOfDomain
        is com.example.calcstudiopro.domain.functions.DerivativeFailure.NonFinite -> SearchValue.NonFinite
        else -> SearchValue.Unavailable(reason.toString())
    }
}

private fun DerivativeEvaluation?.toSearchValueOrNull(): SearchValue? = this?.toSearchValue()

private fun combineFailure(a: SearchValue, b: SearchValue): SearchValue = when {
    a == SearchValue.OutOfDomain || b == SearchValue.OutOfDomain -> SearchValue.OutOfDomain
    a == SearchValue.NonFinite || b == SearchValue.NonFinite -> SearchValue.NonFinite
    a is SearchValue.Unavailable -> a
    b is SearchValue.Unavailable -> b
    else -> SearchValue.Unavailable("Évaluation indisponible")
}

private fun finiteDifference(a: Double, b: Double): SearchValue {
    val difference = a - b
    return if (difference.isFinite()) SearchValue.Finite(difference) else SearchValue.NonFinite
}
