// com/proyeklo/ecgapp/data/repository/UserRepository.kt
package com.tugasakhir.ecgapp.data.repository

import androidx.paging.PagingData
import com.tugasakhir.ecgapp.core.utils.Result
import com.tugasakhir.ecgapp.data.model.EcgReading
import com.tugasakhir.ecgapp.data.remote.response.DashboardResponse
import com.tugasakhir.ecgapp.data.remote.response.GenericSuccessResponse
import com.tugasakhir.ecgapp.data.remote.response.ProfileResponse
import kotlinx.coroutines.flow.Flow

/**
 * Interface buat ngatur semua data user (profil, dashboard, history, device, kerabat)
 */
interface UserRepository {
    fun getProfile(): Flow<Result<ProfileResponse>>
    fun getDashboard(): Flow<Result<DashboardResponse>>

    // Paging
    fun getHistoryPager(
        userId: Int,
        filterDay: String?,
        filterClass: String?
    ): Flow<PagingData<EcgReading>>
    // Device

    fun claimDevice(macAddress: String, deviceIdStr: String): Flow<Result<GenericSuccessResponse>>
    fun unclaimDevice(deviceIdStr: String): Flow<Result<GenericSuccessResponse>>

    // Correlative
    fun addCorrelative(scannedCode: String): Flow<Result<GenericSuccessResponse>>
    fun removePatient(patientId: Int): Flow<Result<GenericSuccessResponse>>
    fun removeMonitor(monitorId: Int): Flow<Result<GenericSuccessResponse>>
}