// com/tugasakhir/ecgapp/ui/screen/login/LoginViewModel.kt
package com.tugasakhir.ecgapp.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.ecgapp.core.utils.Result
import com.tugasakhir.ecgapp.data.remote.response.LoginResponse
import com.tugasakhir.ecgapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                    // KITA PISAHIN DI SINI
                    when (result) {
                        is Result.Loading -> {
                            // Kalo loading, update spinner-nya aja
                            _isLoading.value = result.isLoading
                        }
                        is Result.Success -> {
                            // Kalo sukses, kirim event sukses
                            _loginEvent.value = result
                        }
                        is Result.Error -> {
                            // Kalo error, kirim event error
                            _loginEvent.value = result
                        }
                    }
                }
        }
    }

    fun onEventHandled() {
        _loginEvent.value = null
    }

}