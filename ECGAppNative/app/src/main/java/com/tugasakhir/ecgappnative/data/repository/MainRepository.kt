package com.tugasakhir.ecgappnative.data.repository

import com.tugasakhir.ecgappnative.data.api.ApiService
import com.tugasakhir.ecgappnative.data.local.HistoryDao
import com.tugasakhir.ecgappnative.data.local.HistoryEntity
import com.tugasakhir.ecgappnative.data.model.*
import com.tugasakhir.ecgappnative.data.utils.ResultWrapper
import com.tugasakhir.ecgappnative.data.utils.SessionManager
import com.tugasakhir.ecgappnative.data.utils.safeApiCall
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import retrofit2.Response

/**
 * Repository.
 * Satu-satunya sumber data (Single Source of Truth).
 * Semua ViewModel akan ngomong ke class ini, gak boleh langsung ke API/DB.
 */
class MainRepository(
    private val api: ApiService,
    private val historyDao: HistoryDao,
    private val session: SessionManager
) {

    private val _onSessionExpired = MutableSharedFlow<Unit>()
    val onSessionExpired = _onSessionExpired.asSharedFlow()

    private suspend fun <T> handleApiCall(call: suspend () -> Response<T>): Response<T> {
        val response = call()
        if (response.code() == 401) {
            session.clearSession()
            _onSessionExpired.emit(Unit)
        }
        return response
    }


    suspend fun login(req: LoginRequest): Response<AuthResponse> {
        val response = api.login(req)
        if (response.isSuccessful) {
            val body = response.body()
            body?.accessToken?.let {
                session.saveSession(body.accessToken, body.refreshToken.toString(), body.userId.toString(), body.name ?: "User")
            }
        }
        return response
    }

    suspend fun register(req: RegisterRequest) = api.register(req)

    suspend fun logout() {
        session.clearSession()
    }

    // --- Profile ---
    suspend fun getProfile() = handleApiCall { api.getProfile() }

    // --- Dashboard ---
    suspend fun getDashboard() = handleApiCall { api.getDashboard() }
    suspend fun claimDevice(req: ClaimRequest) = handleApiCall { api.claimDevice(req) }
    suspend fun addCorrelative(req: AddCorrelativeRequest) = handleApiCall { api.addCorrelative(req) }
    suspend fun unclaimDevice(req: UnclaimRequest) = handleApiCall { api.unclaimDevice(req) }
    suspend fun removeCorrelative(req: RemoveCorrelativeRequest) = handleApiCall { api.removeCorrelative(req) }

    // --- History (Cache Logic) ---
    suspend fun getHistory(userId: String): List<HistoryEntity> {
        // 1. Coba ambil dari network
        try {
            val response = handleApiCall { api.getHistory(userId) }
            if (response.isSuccessful) {
                val items = response.body()?.data ?: emptyList()
                val entities = items.map { HistoryEntity(it.id, it.timestamp, it.classification, it.heartRate) }

                // 3. Simpan ke DB (cache)
                historyDao.clear() // Hapus cache lama
                historyDao.insertAll(entities)
                return entities
            }
        } catch (_: Exception) {
            // Gagal konek, lanjut ambil dari cache
        }
        return historyDao.getAll()
    }
    suspend fun getReadingDetail(readingId: Int): ResultWrapper<HistoryDetailResponse> {
        // Kita pake 'sessionManager.tokenFlow.first()' buat mastiin dapet token
        // Walaupun 'AuthInterceptor' udah nanganin, ini buat jaga-jaga
        // Kalo lo yakin 100% Interceptor jalan, ini gak perlu, tapi ini lebih aman
        val token = session.tokenFlow.first()
        if (token.isNullOrEmpty()) {
            return ResultWrapper.Error("User not logged in", 401)
        }
        return safeApiCall {
            handleApiCall { api.getReadingDetail(readingId) }
        }
    }


}