package com.tugasakhir.ecgapp.data.model

data class User(
    val id: Int,
    val email: String,
    val name: String,
    val role: String
)

data class Device(
    val id: Int,
    val name: String,
    val userId: Int?
)

data class EcgReading(
    val id: Int,
    val timestamp: String,
    val classification: String,
    val heartRate: Float?
)

/**
 * Model buat data di Dashboard Screen
 */
data class DashboardItem(
    val type: String, // "self" atau "correlative"
    val userId: Int,
    val userEmail: String,
    val deviceName: String,
    val heartRate: Float?,
    val prediction: String,
    val timestamp: String
)

/**
 * Model buat list kerabat di Profile Screen
 */
data class CorrelativeMonitor( // Orang yg memonitor kita
    val id: Int,
    val email: String,
    val name: String
)

data class CorrelativePatient( // Orang yg kita monitor
    val id: Int,
    val email: String,
    val name: String
)