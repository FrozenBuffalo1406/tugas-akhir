package com.tugasakhir.ecgappnative.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.ecgappnative.data.model.RegisterRequest
import com.tugasakhir.ecgappnative.data.repository.MainRepository
import kotlinx.coroutines.launch

class RegisterViewModel(private val repository: MainRepository) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _registerResult = MutableLiveData<Result<Unit>>()
    val registerResult: LiveData<Result<Unit>> = _registerResult

    fun register(req: RegisterRequest) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = repository.register(req)
                if (response.isSuccessful) {
                    _registerResult.value = Result.success(Unit)
                } else {
                    val errorMsg = response.body()?.error ?: response.errorBody()?.string() ?: "Daftar Gagal"
                    _registerResult.value = Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                _registerResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}