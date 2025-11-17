package com.tugasakhir.ecgapp.ui.screen.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.ecgapp.core.utils.Result
import com.tugasakhir.ecgapp.data.remote.response.GenericSuccessResponse
import com.tugasakhir.ecgapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    // State buat spinner
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // State buat event
    private val _registerEvent = MutableStateFlow<Result<GenericSuccessResponse>?>(null)
    val registerEvent = _registerEvent.asStateFlow()

    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            authRepository.register(name, email, password)
                .collect { result ->
                    when (result) {
                        is Result.Loading -> _isLoading.value = result.isLoading
                        is Result.Success -> _registerEvent.value = result
                        is Result.Error -> _registerEvent.value = result
                    }
                }
        }
    }

    fun onEventHandled() {
        _registerEvent.value = null
    }
}