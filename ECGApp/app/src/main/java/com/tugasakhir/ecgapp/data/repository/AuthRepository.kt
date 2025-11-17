// com/proyeklo/ecgapp/data/repository/AuthRepository.kt
package com.tugasakhir.ecgapp.data.repository

import com.tugasakhir.ecgapp.core.utils.Result
import com.tugasakhir.ecgapp.data.remote.response.GenericSuccessResponse
import com.tugasakhir.ecgapp.data.remote.response.LoginResponse
import kotlinx.coroutines.flow.Flow

/**
 * Interface buat ngatur semua yg berhubungan dgn Autentikasi.
 * Dipake buat Dependency Injection.
 */
interface AuthRepository {
    fun login(email: String, password: String): Flow<Result<LoginResponse>>
    fun register(name: String, email: String, password: String): Flow<Result<GenericSuccessResponse>>

    suspend fun logout()
}