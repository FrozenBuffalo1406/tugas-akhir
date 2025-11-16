// com/proyeklo/ecgapp/data/remote/request/ApiRequests.kt
package com.tugasakhir.ecgapp.data.remote.request

import com.google.gson.annotations.SerializedName

// Kumpulan data class buat ngirim JSON body ke API

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

data class ClaimDeviceRequest(
    @SerializedName("mac_address")
    val macAddress: String,
    @SerializedName("device_id_str")
    val deviceIdStr: String
)

data class UnclaimDeviceRequest(
    @SerializedName("device_id_str")
    val deviceIdStr: String
)

data class AddCorrelativeRequest(
    @SerializedName("scannedCode") // Sesuai API Flask: "scannedCode"
    val scannedCode: String
)

data class RemoveCorrelativeRequest(
    // Ngirim salah satu
    @SerializedName("patient_id")
    val patientId: Int? = null,

    @SerializedName("monitor_id")
    val monitorId: Int? = null
)