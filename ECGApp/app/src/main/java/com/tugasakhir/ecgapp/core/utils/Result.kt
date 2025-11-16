package com.tugasakhir.ecgapp.core.utils

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String?, val code: Int? = null) : Result<Nothing>()
    data class Loading(val isLoading: Boolean) : Result<Nothing>()
}