package com.tugasakhir.ecgapp.data.model

data class User(
    val id: Int,
    val email: String,
    val name: String,
    val role: String
)

data class EcgReading(
    val id: Int,
    val timestamp: String,
    val classification: String,
    val heartRate: Float?
)

data class DashboardItem(
    val type: String,
    val userId: Int,
    val userEmail: String,
    val deviceName: String,
    val heartRate: Float?,
    val prediction: String,
    val timestamp: String
)

data class CorrelativeMonitor( //
    val id: Int,
    val email: String,
    val name: String
)

data class CorrelativePatient( //
    val id: Int,
    val email: String,
    val name: String
)