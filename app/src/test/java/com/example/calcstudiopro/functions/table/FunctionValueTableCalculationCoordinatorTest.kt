package com.example.calcstudiopro.functions.table

import com.example.calcstudiopro.domain.functions.FunctionAngleMode
import com.example.calcstudiopro.domain.functions.MathExpressionEngine
import com.example.calcstudiopro.functions.domain.FunctionSlotId
import com.example.calcstudiopro.functions.domain.FunctionStudy
import com.example.calcstudiopro.functions.domain.StudyParameterUpdate
import com.example.calcstudiopro.functions.domain.StudyValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FunctionValueTableCalculationCoordinatorTest {
    private val engine = MathExpressionEngine()

    @Test fun calculationRunsAsynchronouslyAndPublishesACompletedResult() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = FunctionValueTableCalculationCoordinator(scope)
        try {
            val generation = coordinator.submit(study("a*x", 2.0))
            val complete = await(coordinator) { it.generation == generation && it.phase == FunctionValueTableCalculationPhase.COMPLETED }
            assertEquals(listOf(FunctionSlotId.F1), complete.result!!.columns.map { it.slotId })
            assertEquals(2.0, (complete.result!!.columns.single().cells.single() as FunctionValueTableCell.Value).value, 0.0)
        } finally {
            coordinator.close()
            scope.cancel()
        }
    }

    @Test fun draftsPresentationGraphVisibilityUnusedParametersAndIrrelevantAngleDoNotRecalculate() = runBlocking {
        val calls = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = FunctionValueTableCalculationCoordinator(scope, calculate = { s, settings, xs, cancellationCheck ->
            calls.incrementAndGet()
            FunctionValueTableCalculator.calculate(s, settings, xs, cancellationCheck)
        })
        try {
            var source = study("a*x", 2.0)
            var generation = coordinator.submit(source)
            await(coordinator) { it.generation == generation && it.phase == FunctionValueTableCalculationPhase.COMPLETED }

            source = source.withDraft(FunctionSlotId.F1, "sqrt(")
                .withColor(FunctionSlotId.F1, com.example.calcstudiopro.functions.domain.FunctionColor.RASPBERRY)
                .withVisibility(FunctionSlotId.F1, false)
                .withAngleMode(FunctionAngleMode.DEG)
            source = (source.withParameter('b', 99.0) as StudyParameterUpdate.Updated).study
            source = source.withValueTableSettings(source.valueTableSettings.copy(decimalPlaces = 2, scientificNotation = false))
            generation = coordinator.submit(source)
            await(coordinator) { it.generation == generation && it.phase == FunctionValueTableCalculationPhase.COMPLETED }
            assertEquals(1, calls.get())

            source = (source.withParameter('a', 3.0) as StudyParameterUpdate.Updated).study
            generation = coordinator.submit(source)
            await(coordinator) { it.generation == generation && it.phase == FunctionValueTableCalculationPhase.COMPLETED }
            assertEquals(2, calls.get())

            source = when (val validation = source.withDraft(FunctionSlotId.F1, "a*x+1")
                .validateFunction(FunctionSlotId.F1, engine)) {
                is StudyValidationResult.Accepted -> validation.study
                is StudyValidationResult.Rejected -> throw AssertionError(validation.error)
            }
            generation = coordinator.submit(source)
            await(coordinator) { it.generation == generation && it.phase == FunctionValueTableCalculationPhase.COMPLETED }
            assertEquals(3, calls.get())

            source = source.withValueTableSettings(source.valueTableSettings.copy(
                generationMode = FunctionValueTableGenerationMode.IntervalAndCount(2.0, 2.0, 1)
            ))
            generation = coordinator.submit(source)
            await(coordinator) { it.generation == generation && it.phase == FunctionValueTableCalculationPhase.COMPLETED }
            assertEquals(4, calls.get())
        } finally {
            coordinator.close()
            scope.cancel()
        }
    }

    @Test fun angleModeInvalidatesOnlyExpressionsThatUseAngularFunctions() = runBlocking {
        val calls = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = FunctionValueTableCalculationCoordinator(scope, calculate = { s, settings, xs, cancellationCheck ->
            calls.incrementAndGet()
            FunctionValueTableCalculator.calculate(s, settings, xs, cancellationCheck)
        })
        try {
            var source = study("sin(x)", 1.0)
            var generation = coordinator.submit(source)
            await(coordinator) { it.generation == generation && it.phase == FunctionValueTableCalculationPhase.COMPLETED }
            source = source.withAngleMode(FunctionAngleMode.DEG)
            generation = coordinator.submit(source)
            await(coordinator) { it.generation == generation && it.phase == FunctionValueTableCalculationPhase.COMPLETED }
            assertEquals(2, calls.get())
        } finally {
            coordinator.close()
            scope.cancel()
        }
    }

    @Test fun rapidRequestsKeepOneActiveAndReplaceTheSinglePendingRequest() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val evaluatedParameters = CopyOnWriteArrayList<Double>()
        val running = AtomicInteger()
        val maximumRunning = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = FunctionValueTableCalculationCoordinator(scope, calculate = { s, settings, xs, cancellationCheck ->
            val count = running.incrementAndGet()
            maximumRunning.accumulateAndGet(count, ::maxOf)
            try {
                val parameter = s.parameters.value('a')
                evaluatedParameters += parameter
                if (parameter == 1.0) {
                    started.countDown()
                    check(release.await(5, TimeUnit.SECONDS)) { "Test did not release the first calculation" }
                    cancellationCheck()
                }
                FunctionValueTableCalculator.calculate(s, settings, xs, cancellationCheck)
            } finally {
                running.decrementAndGet()
            }
        })
        try {
            coordinator.submit(study("a*x", 1.0))
            assertTrue(started.await(5, TimeUnit.SECONDS))
            coordinator.submit(study("a*x", 2.0))
            val lastGeneration = coordinator.submit(study("a*x", 3.0))
            release.countDown()
            val complete = await(coordinator) {
                it.generation == lastGeneration && it.phase == FunctionValueTableCalculationPhase.COMPLETED
            }
            assertEquals(listOf(1.0, 3.0), evaluatedParameters.toList())
            assertEquals(1, maximumRunning.get())
            assertEquals(3.0, (complete.result!!.columns.single().cells.single() as FunctionValueTableCell.Value).value, 0.0)
        } finally {
            release.countDown()
            coordinator.close()
            scope.cancel()
        }
    }

    @Test fun invalidSubmissionCancelsWorkAndPublishesTypedValidationState() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = FunctionValueTableCalculationCoordinator(scope)
        try {
            val bad = study("x", 1.0).withValueTableSettings(FunctionValueTableSettings(decimalPlaces = 13))
            val generation = coordinator.submit(bad)
            val invalid = await(coordinator) {
                it.generation == generation && it.phase == FunctionValueTableCalculationPhase.VALIDATION_ERROR
            }
            assertEquals(FunctionValueTableValidationError.InvalidDecimalPlaces, invalid.validationError)
            assertEquals(null, invalid.result)
        } finally {
            coordinator.close()
            scope.cancel()
        }
    }

    @Test fun cancellationIsCheckedForEveryCellAndRemainsControlFlow() {
        val source = study("x+1", 1.0)
        val settings = source.valueTableSettings
        val xs = (0 until 1_000).map { it.toDouble() }
        var checks = 0
        try {
            FunctionValueTableCalculator.calculate(source, settings, xs) {
                checks++
                if (checks == 37) throw CancellationException("cancel")
            }
            throw AssertionError("Expected cooperative cancellation")
        } catch (_: CancellationException) {
            assertEquals(37, checks)
        }
    }

    private suspend fun await(
        coordinator: FunctionValueTableCalculationCoordinator,
        predicate: (FunctionValueTableCalculationState) -> Boolean
    ): FunctionValueTableCalculationState = withTimeout(5_000) { coordinator.state.first(predicate) }

    private fun study(expression: String, parameter: Double): FunctionStudy {
        var result = FunctionStudy.newWorkingSession()
        val edited = result.withDraft(FunctionSlotId.F1, expression)
        result = when (val validation = edited.validateFunction(FunctionSlotId.F1, engine)) {
            is StudyValidationResult.Accepted -> validation.study
            is StudyValidationResult.Rejected -> throw AssertionError(validation.error)
        }
        result = (result.withParameter('a', parameter) as StudyParameterUpdate.Updated).study
        val settings = FunctionValueTableSettings(
            generationMode = FunctionValueTableGenerationMode.IntervalAndCount(1.0, 1.0, 1),
            selectedSlots = setOf(FunctionSlotId.F1)
        )
        return result.withValueTableSettings(settings)
    }
}
