package com.tugasakhir.ecgappnative.data.api

import com.tugasakhir.ecgappnative.data.model.*
import retrofit2.Response
import retrofit2.http.*

// Interface untuk mendefinisikan endpoint API
interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body req: RegisterRequest): Response<GeneralResponse>

    @POST("claim-device")
    suspend fun claimDevice(@Body req: ClaimRequest): Response<GeneralResponse>

    @POST("unclaim-device")
    suspend fun unclaimDevice(@Body req: UnclaimRequest): Response<GeneralResponse>

    @POST("correlatives/add")
    suspend fun addCorrelative(@Body req: AddCorrelativeRequest): Response<GeneralResponse>

    // DELETE request dengan body
    @HTTP(method = "DELETE", path = "correlatives/remove", hasBody = true)
    suspend fun removeCorrelative(@Body req: RemoveCorrelativeRequest): Response<GeneralResponse>

    @GET("dashboard")
    suspend fun getDashboard(): Response<DashboardResponse>

    @GET("history")
    suspend fun getHistory(@Query("userId") userId: Int): Response<HistoryResponse>
    @GET("reading/{id}")
    suspend fun getReadingDetail(
        @Path("id") readingId: Int
    ): Response<HistoryDetailResponse>
    @GET("profile")
    suspend fun getProfile(): Response<ProfileResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(@Header("Authorization") refreshToken: String): Response<RefreshResponse>
}