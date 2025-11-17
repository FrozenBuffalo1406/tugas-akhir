package com.tugasakhir.ecgappnative.data.api

import com.tugasakhir.ecgappnative.data.model.*
import retrofit2.Response
import retrofit2.http.*

// Interface untuk mendefinisikan endpoint API
interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body req: RegisterRequest): Response<GeneralResponse>
}