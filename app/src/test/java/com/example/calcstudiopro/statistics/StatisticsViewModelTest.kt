package com.example.calcstudiopro.statistics

import com.example.calcstudiopro.history.domain.HistoryEntry
import com.example.calcstudiopro.history.domain.HistoryRepository
import com.example.calcstudiopro.history.domain.NewHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlin.math.pow
import org.junit.Assert.*
import org.junit.Test

class StatisticsViewModelTest {
    private class StatisticsMemoryDao : StatisticsDao {
        val stored = mutableMapOf<String, StatisticsSessionEntity>()
        override fun sessions(): kotlinx.coroutines.flow.Flow<List<StatisticsSessionEntity>> = flowOf(stored.values.toList())
        override suspend fun session(dimension: String): StatisticsSessionEntity? = stored[dimension]
        override suspend fun saveSession(session: StatisticsSessionEntity) { stored[session.dimension] = session }
        override fun series(): kotlinx.coroutines.flow.Flow<List<StatisticsSeriesEntity>> = emptyFlow()
        override suspend fun series(id: Long): StatisticsSeriesEntity? = null
        override suspend fun insertSeries(series: StatisticsSeriesEntity): Long = 1L
        override suspend fun updateSeries(id: Long, name: String, description: String, dataJson: String, updatedAt: Long): Int = 0
        override suspend fun deleteSeries(id: Long): Int = 0
    }

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

    @Test fun clearCurrentDataKeepsEditorAndPersistsOnlyTheActiveDimension() = runBlocking {
        val dao = StatisticsMemoryDao()
        val vm = StatisticsViewModel(coroutineScopeOverride = CoroutineScope(Dispatchers.Unconfined), dao = dao)
        vm.editOneD("1\n2\n3")
        vm.finishEditing()
        vm.openEditor()
        vm.clearCurrentData()
        withTimeout(5_000) { while (dao.session("ONE_D")?.dataJson?.contains("\"values\":[]") != true) kotlinx.coroutines.delay(10) }
        assertEquals(StatisticsPage.EDITOR, vm.state.value.page)
        assertTrue(vm.state.value.oneDValues.isEmpty())
        assertNull(vm.state.value.result)
        assertTrue(vm.state.value.oneDDirty)
        assertEquals(emptyList<Double>(), (StatisticsDataCodec.decode(dao.session("ONE_D")!!.dataJson) as StoredStatisticsData.OneD).values)

        val oneDSession = dao.session("ONE_D")
        vm.selectDimension(StatisticsDimension.TWO_D)
        vm.editTwoD("4\t5\n6\t7")
        vm.finishEditing()
        vm.openEditor()
        vm.clearCurrentData()
        withTimeout(5_000) { while (dao.session("TWO_D")?.dataJson?.contains("\"pairs\":[]") != true) kotlinx.coroutines.delay(10) }
        assertEquals(StatisticsPage.EDITOR, vm.state.value.page)
        assertTrue(vm.state.value.twoDPoints.isEmpty())
        assertEquals(oneDSession?.dataJson, dao.session("ONE_D")?.dataJson)
        assertEquals(emptyList<Statistics2DEngine.PairValue>(), (StatisticsDataCodec.decode(dao.session("TWO_D")!!.dataJson) as StoredStatisticsData.TwoD).points)
    }

    @Test fun comparisonAndRecommendationRefreshWhenTwoDDatasetIsReplaced() = runBlocking {
        val vm = StatisticsViewModel(coroutineScopeOverride = CoroutineScope(Dispatchers.Unconfined))
        vm.selectDimension(StatisticsDimension.TWO_D)
        vm.editTwoD((1..8).joinToString("\n") { "$it\t${2.0 * it + 1.0}" })
        vm.finishEditing()
        vm.compareModels()
        val first = withTimeout(15_000) {
            vm.state.first { it.twoDPoints.isNotEmpty() && it.comparisonData == it.twoDPoints && !it.comparisonLoading }
        }
        assertEquals(Statistics2DEngine.Family.LINEAR, first.recommendedModel?.family)

        vm.editTwoD((1..8).joinToString("\n") { "$it\t${3.0 * it.toDouble().pow(1.5)}" })
        vm.finishEditing()
        val second = withTimeout(15_000) {
            vm.state.first { it.comparisonData == it.twoDPoints && it.twoDPoints != first.twoDPoints && !it.comparisonLoading }
        }
        assertNotEquals(first.comparisonData, second.comparisonData)
        assertEquals(Statistics2DEngine.recommend(second.comparison, second.twoDPoints).message, second.recommendationMessage)
        assertEquals(Statistics2DEngine.Family.POWER, second.recommendedModel?.family)
    }
}
