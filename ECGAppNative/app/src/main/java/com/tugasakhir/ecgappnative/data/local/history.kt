package com.tugasakhir.ecgappnative.data.local

import android.content.Context
import androidx.room.*

// Entity untuk Room (cache data history)
@Entity(tableName = "history_cache")
data class HistoryEntity(
    @PrimaryKey val id: Int,
    val timestamp: String,
    val classification: String,
    val heartRate: Double?
)




