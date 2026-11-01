package com.example.calcstudiopro.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calcstudiopro.history.data.CalcStudioDatabase
import com.example.calcstudiopro.history.data.RoomHistoryRepository
import com.example.calcstudiopro.history.domain.HistoryModes
import com.example.calcstudiopro.history.domain.HistoryValueTypes
import com.example.calcstudiopro.history.domain.HistoryEntry
import com.example.calcstudiopro.history.domain.NewHistoryEntry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoryRepositoryTest {
    private lateinit var database: CalcStudioDatabase
    private lateinit var repository: RoomHistoryRepository

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        repository = RoomHistoryRepository(database.historyDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertsEntriesInReverseChronologicalOrderAndUsesIdToBreakTies() = runBlocking {
        repository.add(entry("older", time = 100))
        val firstAtSameTime = repository.add(entry("first", time = 200))
        val secondAtSameTime = repository.add(entry("second", time = 200))
        repository.add(entry("newer", time = 300))

        val rows = repository.entries.first()
        assertEquals(listOf("newer", "second", "first", "older"), rows.map { it.description })
        assertTrue(secondAtSameTime > firstAtSameTime)
    }

    @Test
    fun deletesOneEntryAndClearsAllEntries() = runBlocking {
        val firstId = repository.add(entry("first", 1))
        repository.add(entry("second", 2))

        repository.delete(firstId)
        assertEquals(listOf("second"), repository.entries.first().map { it.description })

        repository.clear()
        assertEquals(emptyList<HistoryEntry>(), repository.entries.first())
    }

    @Test
    fun concurrentInsertsArePrunedToTheOneThousandNewestEntries() = runBlocking {
        coroutineScope {
            (1L..1_010L).map { timestamp ->
                launch { repository.add(entry("entry-$timestamp", timestamp)) }
            }.forEach { it.join() }
        }

        val rows = repository.entries.first()
        assertEquals(1_000, rows.size)
        assertEquals("entry-1010", rows.first().description)
        assertEquals("entry-11", rows.last().description)
    }

    @Test
    fun entriesSurviveClosingAndReopeningTheDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "history-${UUID.randomUUID()}.db"
        try {
            var fileDatabase = Room.databaseBuilder(
                context,
                CalcStudioDatabase::class.java,
                databaseName
            ).allowMainThreadQueries().build()
            val fileRepository = RoomHistoryRepository(fileDatabase.historyDao())
            fileRepository.add(entry("persisted", 10))
            fileDatabase.close()

            fileDatabase = Room.databaseBuilder(
                context,
                CalcStudioDatabase::class.java,
                databaseName
            ).allowMainThreadQueries().build()
            val restored = RoomHistoryRepository(fileDatabase.historyDao()).entries.first()
            assertEquals("persisted", restored.single().description)
            fileDatabase.close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun insertionAssignsUniqueIdentifiers() = runBlocking {
        val first = repository.add(entry("first", 1))
        val second = repository.add(entry("second", 2))
        assertNotEquals(first, second)
    }

    private fun inMemoryDatabase(): CalcStudioDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        CalcStudioDatabase::class.java
    ).allowMainThreadQueries().build()

    private fun entry(description: String, time: Long) = NewHistoryEntry(
        sourceMode = HistoryModes.SCIENTIFIC_CALCULATOR,
        description = description,
        displayResult = "1",
        reusableValue = "1.0",
        reusableValueType = HistoryValueTypes.DECIMAL_DOUBLE,
        createdAtEpochMillis = time
    )
}
