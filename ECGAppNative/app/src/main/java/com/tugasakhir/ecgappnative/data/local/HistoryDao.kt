package com.tugasakhir.ecgappnative.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HistoryDao {
    // Ambil semua cache
    @Query("SELECT * FROM history_cache ORDER BY timestamp DESC")
    suspend fun getAll(): List<HistoryEntity>

    // Insert (timpa jika ada)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HistoryEntity>)

    // Hapus semua cache
    @Query("DELETE FROM history_cache")
    suspend fun clear()
}