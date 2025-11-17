package com.tugasakhir.ecgapp.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.ecgapp.core.utils.Result
import com.tugasakhir.ecgapp.data.remote.response.LoginResponse
import com.tugasakhir.ecgapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _loginEvent = MutableStateFlow<Result<LoginResponse>?>(null)
    val loginEvent = _loginEvent.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            authRepository.login(email, password)
                .collect { result ->
                    // Pisahin state Loading dan state Event
                    when (result) {
                        is Result.Loading -> _isLoading.value = result.isLoading
                        is Result.Success -> _loginEvent.value = result
                        is Result.Error -> _loginEvent.value = result
                    }
                }
        }
    }

    fun onEventHandled() {
        _loginEvent.value = null
    }
}