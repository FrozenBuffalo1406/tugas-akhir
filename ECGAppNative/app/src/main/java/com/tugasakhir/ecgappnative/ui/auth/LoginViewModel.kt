package com.tugasakhir.ecgappnative.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.ecgappnative.data.model.AuthResponse
import com.tugasakhir.ecgappnative.data.model.LoginRequest
import com.tugasakhir.ecgappnative.data.repository.MainRepository
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: MainRepository) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _loginResult = MutableLiveData<Result<AuthResponse>>()
    val loginResult: LiveData<Result<AuthResponse>> = _loginResult

    fun login(req: LoginRequest) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = repository.login(req)
                if (response.isSuccessful && response.body() != null) {
                    _loginResult.value = Result.success(response.body()!!)
                } else {
                    val errorMsg = response.body()?.error ?: response.errorBody()?.string() ?: "Login Gagal"
                    _loginResult.value = Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                _loginResult.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}