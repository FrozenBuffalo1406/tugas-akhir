package com.tugasakhir.ecgappnative.ui.profile

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.ecgappnative.data.model.ProfileResponse
import com.tugasakhir.ecgappnative.data.repository.MainRepository
import com.tugasakhir.ecgappnative.data.utils.QRCodeGenerator
import kotlinx.coroutines.launch

// Data class wrapper untuk UI State
data class ProfileUiState(
    val profile: ProfileResponse,
    val qrCodeBitmap: Bitmap?
)

class ProfileViewModel(private val repository: MainRepository) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _profileState = MutableLiveData<ProfileUiState>()
    val profileState: LiveData<ProfileUiState> = _profileState

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private val _logoutComplete = MutableLiveData<Boolean>()
    val logoutComplete: LiveData<Boolean> = _logoutComplete

    fun loadProfile() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = repository.getProfile()
                if (response.isSuccessful && response.body() != null) {
                    val profile = response.body()!!
                    // Generate QR di ViewModel, bukan di View
                    val qrBitmap = QRCodeGenerator.generate(profile.user.id.toString(), 400)

                    _profileState.value = ProfileUiState(profile, qrBitmap)
                } else {
                    _toastMessage.value = "Gagal memuat profil"
                }
            } catch (e: Exception) {
                _toastMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _logoutComplete.value = true
        }
    }
}