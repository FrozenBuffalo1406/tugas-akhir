package com.tugasakhir.ecgapp.data.remote.response
import com.tugasakhir.ecgapp.data.model.User
import com.tugasakhir.ecgapp.data.model.CorrelativeMonitor
import com.tugasakhir.ecgapp.data.model.CorrelativePatient
import com.tugasakhir.ecgapp.data.model.DashboardItem
import com.tugasakhir.ecgapp.data.model.EcgReading
import com.google.gson.annotations.SerializedName

data class DashboardResponse(
    @SerializedName("data")
    val data: List<DashboardItem> // <-- Pake DashboardItem dari model
)

data class ProfileResponse(
    @SerializedName("user")
    val user: User,

    @SerializedName("correlatives_who_monitor_me")
    val monitors: List<CorrelativeMonitor>,

    @SerializedName("patients_i_monitor")
    val patients: List<CorrelativePatient>
)

data class LoginResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("user_id")
    val userId: Int,
    val role: String,
    val name: String
)

data class GenericErrorResponse(
    @SerializedName("error")
    val error: String?
)

data class HistoryPagingResponse(
    val data: List<EcgReading>,
    val pagination: PaginationInfo
)

data class PaginationInfo(
    val currentPage: Int,
    val totalPages: Int,
    val totalItems: Int
)

data class GenericSuccessResponse(
    val message: String
)