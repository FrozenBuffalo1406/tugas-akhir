// com/tugasakhir/ecgapp/data/remote/HistoryRemoteMediator.kt
package com.tugasakhir.ecgapp.data.remote

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.tugasakhir.ecgapp.data.local.EcgDatabase
import com.tugasakhir.ecgapp.data.local.EcgReadingEntity
import com.tugasakhir.ecgapp.data.local.RemoteKeys
import com.tugasakhir.ecgapp.data.model.EcgReading
import retrofit2.HttpException
import java.io.IOException

@OptIn(ExperimentalPagingApi::class)
class HistoryRemoteMediator(
    private val userId: Int,
    private val filterDay: String?,
    private val filterClass: String?,
    private val apiService: ApiService,
    private val database: EcgDatabase
) : RemoteMediator<Int, EcgReadingEntity>() {

    private val historyDao = database.ecgHistoryDao()

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, EcgReadingEntity>
    ): MediatorResult {
        try {
            // 1. Tentukan halaman yg mau di-load
            val page = when (loadType) {
                LoadType.REFRESH -> 1 // Kalo refresh, selalu mulai dari halaman 1
                LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true) // Kita gak pake prepend
                LoadType.APPEND -> {
                    // Ambil info 'nextKey' dari database
                    val remoteKeys = database.withTransaction { historyDao.getRemoteKeys(userId) }
                    remoteKeys?.nextKey ?: 1 // Kalo null, berarti mulai dari 1
                }
            }

            // 2. Panggil API
            val response = apiService.getHistory(
                userId = userId,
                page = page,
                filterDay = filterDay,
                filterClass = filterClass
            )
            val readings = response.data
            val pagination = response.pagination
            val endOfPaginationReached = (pagination.currentPage == pagination.totalPages) || readings.isEmpty()

            // 3. Simpen ke Database (PENTING: Pake Transaction)
            database.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    // Kalo refresh, bersihin data lama
                    historyDao.clearReadings()
                    historyDao.clearKeys(userId)
                }

                // Hitung keys buat halaman selanjutnya
                val prevKey = if (page == 1) null else page - 1
                val nextKey = if (endOfPaginationReached) null else page + 1

                // Simpen data baru
                val entities = readings.map { it.toEntity() }
                historyDao.insertAllReadings(entities)
                historyDao.insertOrReplaceKeys(RemoteKeys(userId = userId, prevKey = prevKey, nextKey = nextKey))
            }

            return MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)

        } catch (e: IOException) {
            return MediatorResult.Error(e)
        } catch (e: HttpException) {
            return MediatorResult.Error(e)
        }
    }

    // Mapper simpel dari model API ke entity DB
    private fun EcgReading.toEntity(): EcgReadingEntity {
        return EcgReadingEntity(
            id = this.id,
            timestamp = this.timestamp,
            classification = this.classification,
            heartRate = this.heartRate
        )
    }
}