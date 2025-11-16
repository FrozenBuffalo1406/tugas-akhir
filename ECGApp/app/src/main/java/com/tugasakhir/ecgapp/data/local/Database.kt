// com/tugasakhir/ecgapp/data/local/Database.kt
package com.tugasakhir.ecgapp.data.local

import androidx.paging.PagingSource
import androidx.room.*
import com.tugasakhir.ecgapp.data.model.EcgReading

// 1. ENTITY (Tabel buat nyimpen data history)
@Entity(tableName = "ecg_history")
data class EcgReadingEntity(
    @PrimaryKey
    val id: Int,
    val timestamp: String,
    val classification: String,
    val heartRate: Float?
) {
    // Fungsi mapper buat ngubah Entity (DB) jadi Model (UI)
    fun toModel(): EcgReading {
        return EcgReading(
            id = id,
            timestamp = timestamp,
            classification = classification,
            heartRate = heartRate
        )
    }
}

// 2. ENTITY (Tabel buat nyimpen info halaman)
@Entity(tableName = "remote_keys")
data class RemoteKeys(
    @PrimaryKey
    val userId: Int, // Kita simpen page key per user (nanti bisa dikembangin)
    val prevKey: Int?,
    val nextKey: Int?
)

// 3. DAO (Data Access Object)
@Dao
interface EcgHistoryDao {

    // --- History Readings ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReadings(readings: List<EcgReadingEntity>)

    // Fungsi ini yg PENTING: PagingSource ngebaca dari DB
    @Query("SELECT * FROM ecg_history ORDER BY timestamp DESC")
    fun getReadingsPagingSource(): PagingSource<Int, EcgReadingEntity>

    @Query("DELETE FROM ecg_history")
    suspend fun clearReadings()

    // --- Remote Keys ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceKeys(remoteKey: RemoteKeys)

    @Query("SELECT * FROM remote_keys WHERE userId = :userId")
    suspend fun getRemoteKeys(userId: Int): RemoteKeys?

    @Query("DELETE FROM remote_keys WHERE userId = :userId")
    suspend fun clearKeys(userId: Int)
}

// 4. DATABASE CLASS
@Database(
    entities = [EcgReadingEntity::class, RemoteKeys::class],
    version = 1,
    exportSchema = false
)
abstract class EcgDatabase : RoomDatabase() {
    abstract fun ecgHistoryDao(): EcgHistoryDao
}