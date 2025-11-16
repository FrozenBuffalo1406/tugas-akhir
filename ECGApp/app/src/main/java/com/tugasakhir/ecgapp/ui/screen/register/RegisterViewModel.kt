// com/tugasakhir/ecgapp/ui/screen/register/RegisterViewModel.kt
package com.tugasakhir.ecgapp.ui.screen.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.ecgapp.core.utils.Result
import com.tugasakhir.ecgapp.data.remote.response.GenericSuccessResponse
import com.tugasakhir.ecgapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _registerState = MutableStateFlow<Result<GenericSuccessResponse>?>(null)
    val registerState = _registerState.asStateFlow()

    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            authRepository.register(name, email, password)
                .collect { result ->
                    _registerState.value = result
                }
        }
    }
}