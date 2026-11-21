package com.example.calcstudiopro.statistics

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "statistics_sessions")
data class StatisticsSessionEntity(
    @PrimaryKey val dimension: String,
    val dataJson: String,
    val sourceSeriesId: Long?,
    val dirty: Boolean,
    val updatedAt: Long
)

@Entity(tableName = "statistics_series", indices = [androidx.room.Index(value = ["name"], unique = true)])
data class StatisticsSeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val dimension: String,
    val dataJson: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Dao
interface StatisticsDao {
    @Query("SELECT * FROM statistics_sessions") fun sessions(): Flow<List<StatisticsSessionEntity>>
    @Query("SELECT * FROM statistics_sessions WHERE dimension = :dimension") suspend fun session(dimension: String): StatisticsSessionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSession(session: StatisticsSessionEntity)
    @Query("SELECT * FROM statistics_series ORDER BY updatedAt DESC") fun series(): Flow<List<StatisticsSeriesEntity>>
    @Query("SELECT * FROM statistics_series WHERE id = :id") suspend fun series(id: Long): StatisticsSeriesEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSeries(series: StatisticsSeriesEntity): Long
    @Query("UPDATE statistics_series SET name=:name, description=:description, dataJson=:dataJson, updatedAt=:updatedAt WHERE id=:id")
    suspend fun updateSeries(id: Long, name: String, description: String, dataJson: String, updatedAt: Long): Int
    @Query("DELETE FROM statistics_series WHERE id = :id") suspend fun deleteSeries(id: Long): Int
}

@Database(entities = [StatisticsSessionEntity::class, StatisticsSeriesEntity::class], version = 1, exportSchema = false)
abstract class StatisticsDatabase : RoomDatabase() {
    abstract fun statisticsDao(): StatisticsDao
    companion object {
        @Volatile private var instance: StatisticsDatabase? = null
        fun getInstance(context: Context): StatisticsDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, StatisticsDatabase::class.java, "calc_studio_statistics.db")
                .build().also { instance = it }
        }
    }
}

enum class StatisticsDimension { ONE_D, TWO_D }

sealed interface StoredStatisticsData {
    data class OneD(val values: List<Double>) : StoredStatisticsData
    data class TwoD(val points: List<Statistics2DEngine.PairValue>) : StoredStatisticsData
}

/** Strict JSON codec: malformed persisted content is rejected rather than partially loaded. */
object StatisticsDataCodec {
    fun encode(data: StoredStatisticsData): String = when (data) {
        is StoredStatisticsData.OneD -> "{\"version\":1,\"dimension\":\"1D\",\"values\":[${data.values.joinToString(",") { number(it) }}]}"
        is StoredStatisticsData.TwoD -> "{\"version\":1,\"dimension\":\"2D\",\"pairs\":[${data.points.joinToString(",") { "[${number(it.x)},${number(it.y)}]" }}]}"
    }

    fun decode(json: String): StoredStatisticsData? = runCatching {
        val root = org.json.JSONObject(json)
        require(root.optInt("version") == 1)
        when (root.getString("dimension")) {
            "1D" -> {
                val array = root.getJSONArray("values")
                require(array.length() <= StatisticsEngine.MAX_VALUES)
                val values = (0 until array.length()).map { array.getDouble(it).also { value -> require(value.isFinite()) } }
                StoredStatisticsData.OneD(values)
            }
            "2D" -> {
                val array = root.getJSONArray("pairs")
                require(array.length() <= StatisticsEngine.MAX_VALUES)
                val points = (0 until array.length()).map {
                    val pair = array.getJSONArray(it); require(pair.length() == 2)
                    Statistics2DEngine.PairValue(pair.getDouble(0), pair.getDouble(1)).also { p -> require(p.x.isFinite() && p.y.isFinite()) }
                }
                StoredStatisticsData.TwoD(points)
            }
            else -> error("Unknown statistics dimension")
        }
    }.getOrNull()

    private fun number(value: Double): String { require(value.isFinite()); return java.lang.Double.toString(value) }
}

/** Context-aware input parsing. Decimal commas are accepted; commas never split observations. */
object StatisticsInputParser {
    sealed interface Parsed<out T> {
        data class Valid<T>(val value: T) : Parsed<T>
        data class Invalid(val message: String, val line: Int? = null) : Parsed<Nothing>
    }

    fun oneD(text: String): Parsed<List<Double>> {
        if (text.isBlank()) return Parsed.Valid(emptyList())
        val groups = text.trim().split(';')
        if (groups.any { it.isBlank() }) return Parsed.Invalid("Une valeur manque entre deux séparateurs.")
        val tokens = groups.flatMap { it.trim().split(Regex("\\s+")).filter(String::isNotEmpty) }
        if (tokens.size > StatisticsEngine.MAX_VALUES) return Parsed.Invalid("La série est limitée à ${StatisticsEngine.MAX_VALUES} valeurs.")
        val values = tokens.mapIndexed { index, token ->
            StatisticsEngine.parseNumber(token) ?: return Parsed.Invalid("Valeur invalide : « $token ».", index + 1)
        }
        return Parsed.Valid(values)
    }

    /** Accepts spreadsheet TSV or semicolon rows. A comma is reserved for French decimals. */
    fun twoD(text: String): Parsed<List<Statistics2DEngine.PairValue>> {
        if (text.isBlank()) return Parsed.Valid(emptyList())
        val lines = text.lines()
        val pairs = ArrayList<Statistics2DEngine.PairValue>()
        lines.forEachIndexed { index, raw ->
            if (raw.isBlank()) return@forEachIndexed
            val line = index + 1
            val cells = when {
                raw.contains('\t') -> raw.split('\t')
                raw.contains(';') -> raw.split(';')
                else -> return Parsed.Invalid("Utilisez deux colonnes séparées par une tabulation ou un point-virgule.", line)
            }.map(String::trim)
            if (index == 0 && cells.size == 2 && cells[0].equals("x", true) && cells[1].equals("y", true)) return@forEachIndexed
            if (cells.size != 2) return Parsed.Invalid("Deux colonnes X et Y sont requises.", line)
            if (cells.any(String::isEmpty)) return Parsed.Invalid("Couple incomplet.", line)
            val x = StatisticsEngine.parseNumber(cells[0]) ?: return Parsed.Invalid("Valeur X invalide : « ${cells[0]} ».", line)
            val y = StatisticsEngine.parseNumber(cells[1]) ?: return Parsed.Invalid("Valeur Y invalide : « ${cells[1]} ».", line)
            pairs += Statistics2DEngine.PairValue(x, y)
            if (pairs.size > StatisticsEngine.MAX_VALUES) return Parsed.Invalid("La série est limitée à ${StatisticsEngine.MAX_VALUES} couples.", line)
        }
        return Parsed.Valid(pairs)
    }
}
