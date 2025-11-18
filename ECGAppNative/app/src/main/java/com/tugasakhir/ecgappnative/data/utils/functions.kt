package com.tugasakhir.ecgappnative.data.utils

import retrofit2.Response
import java.io.IOException

suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): ResultWrapper<T> {
    return try {
        val response = apiCall()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ResultWrapper.Success(body)
            } else {
                ResultWrapper.Error("Response body is null", response.code())
            }
        } else {
            // Coba ambil pesan error dari server kalo ada
            val errorBody = response.errorBody()?.string()
            val message = if (!errorBody.isNullOrEmpty()) errorBody else response.message()
            ResultWrapper.Error(message, response.code())
        }
    } catch (e: IOException) {
        // Ini biasanya karena gak ada internet
        ResultWrapper.NetworkError("Tidak ada koneksi internet. Cek sambungan Anda.")
    } catch (e: Exception) {
        // Error lain yang gak terduga
        ResultWrapper.Error(e.message ?: "Terjadi kesalahan tidak dikenal")
    }
}