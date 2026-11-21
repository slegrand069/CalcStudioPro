package com.example.calcstudiopro.statistics

import com.example.calcstudiopro.history.domain.HistoryEntry
import com.example.calcstudiopro.history.domain.HistoryRepository
import com.example.calcstudiopro.history.domain.NewHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class StatisticsViewModelTest {
    private class Repo : HistoryRepository {
        override val entries: Flow<List<HistoryEntry>> = emptyFlow()
        val saved = mutableListOf<NewHistoryEntry>()
        override suspend fun add(entry: NewHistoryEntry): Long { saved += entry; return saved.size.toLong() }
        override suspend fun delete(id: Long) {}
        override suspend fun clear() {}
    }

    @Test fun editInvalidatesAndSaveIsExplicit() = runBlocking {
        val repo = Repo(); val vm = StatisticsViewModel(repo, CoroutineScope(Dispatchers.Unconfined))
        vm.edit("1 2 3")
        assertEquals("1 2 3", vm.state.value.input); assertTrue(vm.state.value.canSave); assertTrue(repo.saved.isEmpty())
        vm.edit("1;3")
        assertFalse(vm.state.value.canSave); assertNull(vm.state.value.result); assertEquals("1;3", vm.state.value.input)
        vm.edit("1 2 3")
        vm.save()
        assertEquals(1, repo.saved.size)
        assertEquals("statistics", repo.saved.single().sourceMode)
        assertNull(repo.saved.single().reusableValue)
        assertTrue(repo.saved.single().detailsJson!!.contains("\"version\":1"))
    }

    @Test fun groupedEditorPreservesDistributionAndOneDTwoDSessionsStaySeparate() {
        val vm = StatisticsViewModel()
        vm.editOneD("1\n1\n2,5")
        vm.setOneDGrouped(true)
        assertEquals("1.0;2\n2.5;1", vm.state.value.oneDFrequencyText)
        vm.finishEditing()
        assertEquals(listOf(1.0, 1.0, 2.5), vm.state.value.oneDValues)
        vm.selectDimension(StatisticsDimension.TWO_D)
        vm.editTwoD("3\t4\n5\t6")
        vm.finishEditing()
        assertEquals(2, vm.state.value.twoDPoints.size)
        vm.selectDimension(StatisticsDimension.ONE_D)
        assertEquals(listOf(1.0, 1.0, 2.5), vm.state.value.oneDValues)
    }
}
