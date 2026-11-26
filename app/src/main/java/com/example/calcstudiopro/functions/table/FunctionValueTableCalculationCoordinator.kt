package com.example.calcstudiopro.functions.table

import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.LinkedHashMap

data class FunctionValueTableFunctionIdentity(val slotId: FunctionSlotId, val expression: String)

/** Only math-affecting inputs participate; presentation settings and graph visibility are excluded. */
class FunctionValueTableMathIdentity(
    abscissas: List<Double>,
    functions: List<FunctionValueTableFunctionIdentity>,
    parameters: Map<Char, Double>,
    val angleMode: FunctionAngleMode?
) {
    val abscissas: List<Double> = Collections.unmodifiableList(ArrayList(abscissas))
    val functions: List<FunctionValueTableFunctionIdentity> = Collections.unmodifiableList(ArrayList(functions))
    val parameters: Map<Char, Double> = Collections.unmodifiableMap(LinkedHashMap(parameters))

    override fun equals(other: Any?): Boolean = other is FunctionValueTableMathIdentity &&
        abscissas == other.abscissas && functions == other.functions &&
        parameters == other.parameters && angleMode == other.angleMode

    override fun hashCode(): Int = listOf(abscissas, functions, parameters, angleMode).hashCode()
}

enum class FunctionValueTableCalculationPhase { IDLE, WAITING, CALCULATING, COMPLETED, VALIDATION_ERROR, CALCULATION_ERROR }

data class FunctionValueTableCalculationState(
    val generation: Long = 0L,
    val phase: FunctionValueTableCalculationPhase = FunctionValueTableCalculationPhase.IDLE,
    val currentIdentity: FunctionValueTableMathIdentity? = null,
    val resultIdentity: FunctionValueTableMathIdentity? = null,
    val result: FunctionValueTableResult? = null,
    val validationError: FunctionValueTableValidationError? = null,
    val calculationError: String? = null,
    val presentationSettings: FunctionValueTableSettings? = null
)

/**
 * Coalescing calculation owner: one active job plus one replaceable pending request. A result is
 * published only while both its monotone generation and its math identity remain current.
 */
class FunctionValueTableCalculationCoordinator(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val calculate: (FunctionStudy, FunctionValueTableSettings, List<Double>, () -> Unit) -> FunctionValueTableCalculation =
        { study, settings, xs, check -> FunctionValueTableCalculator.calculate(study, settings, xs, check) }
) {
    private data class Request(
        val generation: Long,
        val study: FunctionStudy,
        val settings: FunctionValueTableSettings,
        val abscissas: List<Double>,
        val identity: FunctionValueTableMathIdentity
    )

    private val lock = Any()
    private val mutableState = MutableStateFlow(FunctionValueTableCalculationState())
    val state = mutableState.asStateFlow()
    private var nextGeneration = 0L
    private var activeJob: Job? = null
    private var pendingRequest: Request? = null
    private var latestIdentity: FunctionValueTableMathIdentity? = null
    private var acceptedIdentity: FunctionValueTableMathIdentity? = null
    private var acceptedResult: FunctionValueTableResult? = null

    /** Submits current study settings. Invalid inputs cancel stale work and publish a typed error. */
    fun submit(study: FunctionStudy): Long {
        val settings = study.valueTableSettings
        if (settings.decimalPlaces !in 0..12) {
            return publishValidationError(settings, FunctionValueTableValidationError.InvalidDecimalPlaces)
        }
        val abscissas = when (val generated = FunctionValueTableAbscissaGenerator.generate(settings.generationMode)) {
            is FunctionValueTableAbscissas.Invalid -> return publishValidationError(settings, generated.error)
            is FunctionValueTableAbscissas.Valid -> generated.values
        }
        val selectedIds = FunctionSlotId.ordered.filter { it in settings.selectedSlots }
        val empty = selectedIds.firstOrNull { study.slot(it).validatedExpression == null }
        if (empty != null) return publishValidationError(settings, FunctionValueTableValidationError.EmptySelectedSlot(empty))

        val functions = selectedIds.map { id ->
            FunctionValueTableFunctionIdentity(id, requireNotNull(study.slot(id).validatedExpression))
        }
        val dependencies = selectedIds.flatMap { study.slot(it).usedParameters }.toSortedSet()
        val parameters = dependencies.associateWith { study.parameters.value(it) }
        val angleMode = if (selectedIds.any { usesAngularFunctions(study.slot(it).validatedExpression.orEmpty()) }) {
            study.angleMode
        } else null
        val identity = FunctionValueTableMathIdentity(abscissas, functions, parameters, angleMode)

        synchronized(lock) {
            if (identity == latestIdentity) {
                mutableState.value = mutableState.value.copy(presentationSettings = settings)
                return mutableState.value.generation
            }
            if (identity == acceptedIdentity && acceptedResult != null) {
                latestIdentity = identity
                pendingRequest = null
                mutableState.value = FunctionValueTableCalculationState(
                    generation = mutableState.value.generation,
                    phase = FunctionValueTableCalculationPhase.COMPLETED,
                    currentIdentity = identity,
                    resultIdentity = identity,
                    result = acceptedResult,
                    presentationSettings = settings
                )
                return mutableState.value.generation
            }
            check(nextGeneration < Long.MAX_VALUE) { "Value-table generation exhausted." }
            val request = Request(++nextGeneration, study, settings, abscissas, identity)
            latestIdentity = identity
            pendingRequest = request
            mutableState.value = FunctionValueTableCalculationState(
                generation = request.generation,
                phase = FunctionValueTableCalculationPhase.WAITING,
                currentIdentity = identity,
                presentationSettings = settings
            )
            activeJob?.cancel()
            startPendingLocked()
            return request.generation
        }
    }

    /** Invalidates any active/pending calculation and prevents late publication. */
    fun cancel() = synchronized(lock) {
        if (nextGeneration < Long.MAX_VALUE) nextGeneration++
        latestIdentity = null
        pendingRequest = null
        activeJob?.cancel()
        mutableState.value = FunctionValueTableCalculationState(
            generation = nextGeneration,
            phase = FunctionValueTableCalculationPhase.IDLE
        )
    }

    fun close() = cancel()

    private fun publishValidationError(
        settings: FunctionValueTableSettings,
        error: FunctionValueTableValidationError
    ): Long = synchronized(lock) {
        check(nextGeneration < Long.MAX_VALUE) { "Value-table generation exhausted." }
        nextGeneration++
        latestIdentity = null
        pendingRequest = null
        activeJob?.cancel()
        mutableState.value = FunctionValueTableCalculationState(
            generation = nextGeneration,
            phase = FunctionValueTableCalculationPhase.VALIDATION_ERROR,
            validationError = error,
            presentationSettings = settings
        )
        nextGeneration
    }

    private fun startPendingLocked() {
        if (activeJob != null) return
        val request = pendingRequest ?: return
        pendingRequest = null
        mutableState.value = mutableState.value.copy(
            phase = FunctionValueTableCalculationPhase.CALCULATING,
            validationError = null,
            calculationError = null
        )
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            var outcome: FunctionValueTableCalculation? = null
            var failure: String? = null
            try {
                outcome = withContext(dispatcher) {
                    val context = kotlinx.coroutines.currentCoroutineContext()
                    calculate(request.study, request.settings, request.abscissas) { context.ensureActive() }
                }
            } catch (_: CancellationException) {
                // Cancellation is control flow and must never be exposed as a mathematical error.
            } catch (error: Exception) {
                failure = error.message ?: "Le calcul du tableau a échoué."
            }
            synchronized(lock) {
                if (activeJob === job) {
                    activeJob = null
                    if (request.generation == mutableState.value.generation && request.identity == latestIdentity) {
                        when {
                            failure != null -> mutableState.value = mutableState.value.copy(
                                phase = FunctionValueTableCalculationPhase.CALCULATION_ERROR,
                                calculationError = failure,
                                result = null,
                                resultIdentity = null
                            )
                            outcome is FunctionValueTableCalculation.Completed -> {
                                acceptedIdentity = request.identity
                                acceptedResult = outcome!!.result
                                mutableState.value = mutableState.value.copy(
                                    phase = FunctionValueTableCalculationPhase.COMPLETED,
                                    result = outcome!!.result,
                                    resultIdentity = request.identity,
                                    validationError = null,
                                    calculationError = null
                                )
                            }
                            outcome is FunctionValueTableCalculation.Invalid -> mutableState.value = mutableState.value.copy(
                                phase = FunctionValueTableCalculationPhase.VALIDATION_ERROR,
                                validationError = (outcome as FunctionValueTableCalculation.Invalid).error,
                                result = null,
                                resultIdentity = null
                            )
                        }
                    }
                    startPendingLocked()
                }
            }
        }
        activeJob = job
        job.start()
    }

    private fun usesAngularFunctions(source: String): Boolean {
        val angleFunctions = setOf("sin", "cos", "tan", "asin", "acos", "atan")
        var index = 0
        while (index < source.length) {
            if (!source[index].isLetter()) {
                index++
                continue
            }
            val start = index
            while (index < source.length && source[index].isLetter()) index++
            val word = source.substring(start, index).lowercase()
            if (word in angleFunctions) {
                var next = index
                while (next < source.length && source[next].isWhitespace()) next++
                if (next < source.length && source[next] == '(') return true
            }
        }
        return false
    }
}
