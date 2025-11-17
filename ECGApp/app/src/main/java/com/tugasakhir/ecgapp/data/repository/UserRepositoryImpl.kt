package com.tugasakhir.ecgapp.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.tugasakhir.ecgapp.core.utils.Result
import com.tugasakhir.ecgapp.core.utils.safeApiCall
import com.tugasakhir.ecgapp.data.local.EcgDatabase
import com.tugasakhir.ecgapp.data.local.UserPreferences
import com.tugasakhir.ecgapp.data.model.EcgReading
import com.tugasakhir.ecgapp.data.remote.ApiService
import com.tugasakhir.ecgapp.data.remote.HistoryRemoteMediator
import com.tugasakhir.ecgapp.data.remote.request.AddCorrelativeRequest
import com.tugasakhir.ecgapp.data.remote.request.ClaimDeviceRequest
import com.tugasakhir.ecgapp.data.remote.request.RemoveCorrelativeRequest
import com.tugasakhir.ecgapp.data.remote.request.UnclaimDeviceRequest
import com.tugasakhir.ecgapp.data.remote.response.DashboardResponse
import com.tugasakhir.ecgapp.data.remote.response.GenericSuccessResponse
import com.tugasakhir.ecgapp.data.remote.response.ProfileResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.paging.map
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val prefs: UserPreferences,
    private val database: EcgDatabase,
) : UserRepository {

    override fun getProfile(): Flow<Result<ProfileResponse>> {
        return safeApiCall { api.getProfile() }
    }

    override fun getDashboard(): Flow<Result<DashboardResponse>> {
        return safeApiCall { api.getDashboard() }
    }

    @OptIn(ExperimentalPagingApi::class)
    override fun getHistoryPager(
        userId: Int,
        filterDay: String?,
        filterClass: String?
    ): Flow<PagingData<EcgReading>> {

        // Ambil DAO dari database
        val historyDao = database.ecgHistoryDao()

        // 1. Buat Pager-nya
        return Pager(
            config = PagingConfig(
                pageSize = 20, // Tentukan ukuran halaman
                enablePlaceholders = false
            ),
            // 2. Pasang RemoteMediator
            remoteMediator = HistoryRemoteMediator(
                userId = userId,
                filterDay = filterDay,
                filterClass = filterClass,
                apiService = api,
                database = database
            ),
            // 3. Tentukan sumber data dari database (Room)
            pagingSourceFactory = {
                historyDao.getReadingsPagingSource() // Panggil fungsi DAO yg tadi dibuat
            }
        )
            .flow // Jadikan Flow
            .map { pagingDataEntity ->
                // 4. Ubah/Map data dari Entity (DB) ke Model (UI)
                // PagingData<EcgReadingEntity> -> PagingData<EcgReading>
                pagingDataEntity.map { entity ->
                    entity.toModel() // Bikin fungsi mapper-nya
                }
            }
    }

    override fun claimDevice(macAddress: String, deviceIdStr: String): Flow<Result<GenericSuccessResponse>> {
        return safeApiCall {
            api.claimDevice(ClaimDeviceRequest(macAddress, deviceIdStr))
        }
    }

    override fun unclaimDevice(deviceIdStr: String): Flow<Result<GenericSuccessResponse>> {
        return safeApiCall {
            api.unclaimDevice(UnclaimDeviceRequest(deviceIdStr))
        }
    }

    override fun addCorrelative(scannedCode: String): Flow<Result<GenericSuccessResponse>> {
        return safeApiCall {
            api.addCorrelative(AddCorrelativeRequest(scannedCode))
        }
    }

    override fun removePatient(patientId: Int): Flow<Result<GenericSuccessResponse>> {
        return safeApiCall {
            api.removeCorrelative(RemoveCorrelativeRequest(patientId = patientId))
        }
    }

    override fun removeMonitor(monitorId: Int): Flow<Result<GenericSuccessResponse>> {
        return safeApiCall {
            api.removeCorrelative(RemoveCorrelativeRequest(monitorId = monitorId))
        }
    }
}