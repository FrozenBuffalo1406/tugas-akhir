package com.tugasakhir.ecgappnative.data.utils

sealed class ResultWrapper<out T> {
    // Kalo sukses, bawa datanya (T)
    data class Success<out T>(val data: T) : ResultWrapper<T>()

    // Kalo error dari server (misal 401, 404, 500), bawa pesannya dan kodenya
    data class Error(val message: String? = null, val code: Int? = null) : ResultWrapper<Nothing>()

    // Kalo error koneksi (misal gak ada internet, timeout), bawa pesannya
    data class NetworkError(val message: String? = null) : ResultWrapper<Nothing>()

    // Status loading (opsional, kadang dipake di UI)
    object Loading : ResultWrapper<Nothing>()
}