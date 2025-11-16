// com/proyeklo/ecgapp/data/repository/AuthRepositoryImpl.kt
package com.tugasakhir.ecgapp.data.repository

import com.tugasakhir.ecgapp.core.utils.Result
import com.tugasakhir.ecgapp.core.utils.safeApiCall
import com.tugasakhir.ecgapp.data.local.EcgDatabase
import com.tugasakhir.ecgapp.data.local.UserPreferences
import com.tugasakhir.ecgapp.data.remote.ApiService
import com.tugasakhir.ecgapp.data.remote.request.LoginRequest
import com.tugasakhir.ecgapp.data.remote.request.RegisterRequest
import com.tugasakhir.ecgapp.data.remote.response.GenericSuccessResponse
import com.tugasakhir.ecgapp.data.remote.response.LoginResponse
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val prefs: UserPreferences,
    private val db : EcgDatabase

) : AuthRepository {

    override fun login(email: String, password: String): Flow<Result<LoginResponse>> {
        return safeApiCall {
            val request = LoginRequest(email, password)
            val response = api.login(request)

            // Kalo login sukses, LANGSUNG SIMPEN data ke DataStore
            prefs.saveLoginData(
                token = response.accessToken,
                userId = response.userId,
                role = response.role,
                name = response.name
            )
            response
        }
    }

    override fun register(name: String, email: String, password: String): Flow<Result<GenericSuccessResponse>> {
        return safeApiCall {
            val request = RegisterRequest(email, password, name)
            api.register(request)
        }
    }

    override suspend fun logout() {
        // Hapus semua data di DataStore
        prefs.clear()
        db.ecgHistoryDao().clearReadings()
        db.ecgHistoryDao().clearKeys(0)
    }
}