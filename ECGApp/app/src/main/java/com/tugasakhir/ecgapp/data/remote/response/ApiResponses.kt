// com/proyeklo/ecgapp/data/remote/response/ApiResponses.kt
package com.tugasakhir.ecgapp.data.remote.response

import com.google.gson.annotations.SerializedName
import com.tugasakhir.ecgapp.data.model.*

// Kumpulan data class buat nangkep response JSON dari API

/**
 * Response pas login sukses
 * Sesuai sama response API Flask
 */
data class LoginResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("user_id")
    val userId: Int,
    val role: String,
    val name: String
)

/**
 * Response buat nangkep error
 * { "error": "Email sudah terdaftar" }
 */
data class GenericErrorResponse(
    @SerializedName("error")
    val error: String?
)

/**
 * Response buat /api/v1/profile
 */
data class ProfileResponse(
    val user: User,
    @SerializedName("correlatives_who_monitor_me")
    val correlativesWhoMonitorMe: List<CorrelativeMonitor>,
    @SerializedName("patients_i_monitor")
    val patientsIMonitor: List<CorrelativePatient>
)

/**
 * Response buat /api/v1/dashboard
 */
data class DashboardResponse(
    val data: List<DashboardItem>
)

/**
 * Response buat /api/v1/history (Paging)
 */
data class HistoryPagingResponse(
    val data: List<EcgReading>,
    val pagination: PaginationInfo
)

data class PaginationInfo(
    val currentPage: Int,
    val totalPages: Int,
    val totalItems: Int
)

/**
 * Response generik kalo sukses tapi gak ngembaliin data
 * (cth: register, claim, unclaim)
 */
data class GenericSuccessResponse(
    val message: String
)