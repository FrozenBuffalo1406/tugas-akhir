package com.tugasakhir.healtyheart.models

data class History(
    val id: String,
    val timestamp: String,
    val classification: String,
    val peakValue: Float
)