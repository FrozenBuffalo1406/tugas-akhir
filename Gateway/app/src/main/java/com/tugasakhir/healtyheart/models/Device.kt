package com.tugasakhir.healtyheart.models

data class Device(
    val id: String,
    val name: String,
    val patientName: String,
    val lastActive: String,
    val history: List<History> = emptyList() // Default emptyList biar aman
)
