// com/proyeklo/ecgapp/data/remote/ApiService.kt
package com.tugasakhir.ecgapp.data.remote

import com.tugasakhir.ecgapp.data.remote.request.*
import com.tugasakhir.ecgapp.data.remote.response.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Interface Retrofit.
 * Mendefinisikan semua endpoint API Flask.
 */
interface ApiService {

    // --- Auth ---
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): GenericSuccessResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    // --- Profile ---
    @GET("profile")
    suspend fun getProfile(): ProfileResponse

    // --- Dashboard ---
    @GET("dashboard")
    suspend fun getDashboard(): DashboardResponse

    // --- History (Paging) ---
    @GET("history")
    suspend fun getHistory(
        @Query("userId") userId: Int,
        @Query("page") page: Int,
        @Query("filterDay") filterDay: String? = null,
        @Query("filterClass") filterClass: String? = null
    ): HistoryPagingResponse

    // --- Device ---
    @POST("claim-device")
    suspend fun claimDevice(@Body request: ClaimDeviceRequest): GenericSuccessResponse

    @POST("unclaim-device")
    suspend fun unclaimDevice(@Body request: UnclaimDeviceRequest): GenericSuccessResponse

    // --- Correlative (Kerabat) ---
    @POST("correlatives/add")
    suspend fun addCorrelative(@Body request: AddCorrelativeRequest): GenericSuccessResponse

    // API Flask lo pake DELETE, tapi ngirim body.
    // Retrofit butuh @HTTP biar bisa ngirim body dgn method DELETE.
    @HTTP(method = "DELETE", path = "correlatives/remove", hasBody = true)
    suspend fun removeCorrelative(@Body request: RemoveCorrelativeRequest): GenericSuccessResponse
}