package com.tugasakhir.ecgappnative.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val password: String, val name: String)
data class AuthResponse(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("user_id") val userId: Int?,
    val role: String?,
    val name: String?,
    val error: String?
)

data class RefreshResponse(
    @SerializedName("access_token") val accessToken: String
)
