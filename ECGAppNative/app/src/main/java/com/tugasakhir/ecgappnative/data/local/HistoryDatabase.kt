package com.tugasakhir.ecgappnative.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    // Singleton pattern untuk database
    companion object {
        @Volatile
        private var INSTANCE: HistoryDatabase? = null

        fun getDatabase(context: Context): HistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HistoryDatabase::class.java,
                    "ecg_app_database"
                )
                    .fallbackToDestructiveMigration(true) // Hapus dan bangun ulang DB jika skema berubah
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}