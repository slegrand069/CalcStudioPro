package com.example.calcstudiopro.statistics

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsPersistenceTest {
    private lateinit var database: StatisticsDatabase
    private lateinit var dao: StatisticsDao

    @Before fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), StatisticsDatabase::class.java).allowMainThreadQueries().build()
        dao = database.statisticsDao()
    }

    @After fun closeDatabase() { database.close() }

    @Test fun sessionsAndNamedSeriesPersistIndependentlyAndRejectNameCollisions() = runBlocking {
        val oneD = StoredStatisticsData.OneD(listOf(1.0, 2.5, -3.0))
        val twoD = StoredStatisticsData.TwoD(listOf(Statistics2DEngine.PairValue(2.0, 4.0)))
        dao.saveSession(StatisticsSessionEntity(StatisticsDimension.ONE_D.name, StatisticsDataCodec.encode(oneD), null, true, 1L))
        dao.saveSession(StatisticsSessionEntity(StatisticsDimension.TWO_D.name, StatisticsDataCodec.encode(twoD), null, false, 2L))
        assertEquals(oneD, StatisticsDataCodec.decode(dao.session(StatisticsDimension.ONE_D.name)!!.dataJson))
        assertEquals(twoD, StatisticsDataCodec.decode(dao.session(StatisticsDimension.TWO_D.name)!!.dataJson))
        val id = dao.insertSeries(StatisticsSeriesEntity(name = "Essai", description = "", dimension = "ONE_D", dataJson = StatisticsDataCodec.encode(oneD), createdAt = 3L, updatedAt = 3L))
        assertEquals("Essai", dao.series(id)!!.name)
        assertThrows(Exception::class.java) {
            runBlocking { dao.insertSeries(StatisticsSeriesEntity(name = "Essai", description = "collision", dimension = "ONE_D", dataJson = "{}", createdAt = 4L, updatedAt = 4L)) }
        }
        dao.updateSeries(id, "Renommée", "", StatisticsDataCodec.encode(oneD), 5L)
        assertEquals("Renommée", dao.series(id)!!.name)
        dao.deleteSeries(id)
        assertNull(dao.series(id))
    }
}
