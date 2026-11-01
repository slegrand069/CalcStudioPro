package com.example.calcstudiopro.history.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HistoryEntryEntity::class], version = 1, exportSchema = true)
abstract class CalcStudioDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var instance: CalcStudioDatabase? = null

        fun getInstance(context: Context): CalcStudioDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CalcStudioDatabase::class.java,
                "calc_studio_history.db"
            ).build().also { instance = it }
        }
    }
}
