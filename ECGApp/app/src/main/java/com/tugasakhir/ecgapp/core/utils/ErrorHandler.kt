// com/proyeklo/ecgapp/core/utils/ErrorHandler.kt
package com.tugasakhir.ecgapp.core.utils

import com.google.gson.Gson
import com.tugasakhir.ecgapp.data.remote.response.GenericErrorResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException

/**
 * Fungsi helper sakti buat ngebungkus API call.
 * Otomatis ngasih state Loading, Success, atau Error (lengkap dgn parsing pesan error).
 */
fun <T> safeApiCall(apiCall: suspend () -> T): Flow<Result<T>> = flow {
    // 1. Kirim state Loading (true)
    emit(Result.Loading(true))

    try {
        // 2. Panggil API-nya
        val data = apiCall()

        // 3. Kalo sukses, kirim state Success
        emit(Result.Success(data))

    } catch (e: HttpException) {
        // 4. Kalo GAGAL (Error HTTP 4xx/5xx)
        val errorMessage = parseErrorBody(e)
        emit(Result.Error(errorMessage, e.code()))

    } catch (_: IOException) {
        // 5. Kalo GAGAL (Error Jaringan/Internet Mati)
        emit(Result.Error("Masalah jaringan, cek koneksi internet lo.", null))

    } catch (e: Exception) {
        // 6. Kalo GAGAL (Error lain, misal JSON parsing, dll)
        emit(Result.Error("Terjadi kesalahan: ${e.message}", null))
    } finally {
        // 7. Selesai call, kirim state Loading (false)
        emit(Result.Loading(false))
    }
}

/**
 * Fungsi helper buat nge-parse JSON error dari API Flask.
 * API Flask ngirim: { "error": "Email sudah terdaftar" }
 */
private fun parseErrorBody(e: HttpException): String {
    return try {
        val errorBody = e.response()?.errorBody()?.string()
        if (errorBody == null) {
            e.message()
        } else {
            val gson = Gson()
            val errorResponse = gson.fromJson(errorBody, GenericErrorResponse::class.java)
            errorResponse.error ?: "Terjadi kesalahan tidak diketahui"
        }
    } catch (_: Exception) {
        "Error: ${e.message()}"
    }
}