package com.tugasakhir.ecgapp.data.remote

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tugasakhir.ecgapp.data.model.EcgReading
import retrofit2.HttpException
import java.io.IOException

/**
 * Ini adalah inti dari Paging 3.
 * Kelas ini yg ngatur logika "load halaman 1", "load halaman 2", dst.
 */
class HistoryPagingSource(
    private val apiService: ApiService,
    private val userId: Int,
    private val filterDay: String?,
    private val filterClass: String?
) : PagingSource<Int, EcgReading>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, EcgReading> {
        // Tentukan halaman yg mau di-load. Kalo pertama kali, "key" = null, jadi kita pake 1.
        val page = params.key ?: 1

        return try {
            // Panggil API
            val response = apiService.getHistory(
                userId = userId,
                page = page,
                filterDay = filterDay,
                filterClass = filterClass
            )
            val readings = response.data

            // Kirim hasilnya ke Pager
            LoadResult.Page(
                data = readings,
                // Kalo halaman 1, gak ada halaman sebelumnya
                prevKey = if (page == 1) null else page - 1,
                // Kalo halaman ini adalah halaman terakhir, gak ada halaman selanjutnya
                nextKey = if (page == response.pagination.totalPages || readings.isEmpty()) null else page + 1
            )
        } catch (e: IOException) {
            // Error jaringan
            LoadResult.Error(e)
        } catch (e: HttpException) {
            // Error HTTP
            LoadResult.Error(e)
        }
    }

    /**
     * Fungsi ini nentuin "key" (nomor halaman) kalo data-nya di-refresh.
     */
    override fun getRefreshKey(state: PagingState<Int, EcgReading>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}