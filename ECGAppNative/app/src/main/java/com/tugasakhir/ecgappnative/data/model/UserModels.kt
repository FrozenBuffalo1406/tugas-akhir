package com.tugasakhir.ecgappnative.data.model

import com.google.gson.annotations.SerializedName

// Device & Correlative Actions
data class ClaimRequest(
    @SerializedName("mac_address") val macAddress: String,
    @SerializedName("device_id_str") val deviceIdStr: String
)
data class DeviceQRModel( // Helper buat parsing QR Device
    @SerializedName("mac") val mac: String,
    @SerializedName("id") val id: String
)

data class UnclaimRequest(@SerializedName("device_id_str") val deviceIdStr: String)
data class AddCorrelativeRequest(@SerializedName("scannedCode") val scannedCode: String)
data class RemoveCorrelativeRequest(val patientId: String? = null, val monitorId: String? = null)

data class GeneralResponse(val message: String?, val error: String?, val status: String?)

// Dashboard
data class DashboardResponse(val data: List<DashboardItem>)
data class DashboardItem(
    val type: String, // "self" atau "correlative"
    @SerializedName("user_id") val userId: String,
    @SerializedName("user_email") val userEmail: String,
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("heartRate") val heartRate: Double?,
    val prediction: String,
    val timestamp: String,
    // CATATAN: Minta backend nambahin "device_id_str" di sini biar gampang unclaim
    // Untuk sementara kita akalin
)

// History
data class HistoryResponse(val data: List<HistoryItem>)
data class HistoryItem(val id: Int, val timestamp: String, val classification: String, val heartRate: Double?)

data class HistoryDetailResponse(
    @SerializedName("id")
    val id: Int,

    @SerializedName("timestamp")
    val timestamp: String,

    @SerializedName("classification")
    val classification: String,

    @SerializedName("heartRate")
    val heartRate: Float?,

    @SerializedName("ecg_data")
    val ecgData: List<Float>
)
// Profile
data class ProfileResponse(
    val user: UserProfile,
    @SerializedName("correlatives_who_monitor_me") val monitoredBy: List<UserProfile>,
    @SerializedName("patients_i_monitor") val patients: List<UserProfile>
)
data class UserProfile(val id: String, val email: String, val name: String?)