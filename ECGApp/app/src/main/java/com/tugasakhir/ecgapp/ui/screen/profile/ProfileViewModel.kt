package com.tugasakhir.ecgapp.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.ecgapp.core.utils.Result
import com.tugasakhir.ecgapp.data.remote.response.ProfileResponse
import com.tugasakhir.ecgapp.data.repository.AuthRepository
import com.tugasakhir.ecgapp.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: UserRepository,
    private val authRepository: AuthRepository // Inject AuthRepo buat logout
) : ViewModel() {

    // Nampung data profile (user, list monitor, list patient)
    private val _profileState = MutableStateFlow<Result<ProfileResponse>?>(null)
    val profileState = _profileState.asStateFlow()

    // State buat ngasih tau UI (misal: "Sukses nambah kerabat!")
    private val _toastEvent = MutableStateFlow<String?>(null)
    val toastEvent = _toastEvent.asStateFlow()

    // State buat ngasih tau UI kalo udah logout
    private val _logoutEvent = MutableSharedFlow<Boolean>()
    val logoutEvent = _logoutEvent.asSharedFlow()

    // Dipanggil dari UI (ON_RESUME)
    fun fetchProfile() {
        viewModelScope.launch {
            repository.getProfile().collect {
                _profileState.value = it
            }
        }
    }

    // Dipanggil pas QR scanner dapet hasil
    fun onQrCodeScanned(scannedCode: String) {
        viewModelScope.launch {
            repository.addCorrelative(scannedCode).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _toastEvent.value = result.data.message
                        fetchProfile() // Refresh data biar list-nya update
                    }
                    is Result.Error -> _toastEvent.value = result.message ?: "Gagal menambah kerabat"
                    is Result.Loading -> {}
                }
            }
        }
    }

    // Ini buat HAPUS ORANG YG KITA MONITOR (Pasien)
    fun removePatient(patientId: Int) {
        viewModelScope.launch {
            repository.removePatient(patientId).collect {
                _toastEvent.value = if (it is Result.Success) "Pasien berhasil dihapus" else "Gagal"
                fetchProfile() // Refresh
            }
        }
    }

    // Ini buat HAPUS ORANG YG MONITOR KITA (Monitor)
    fun removeMonitor(monitorId: Int) {
        viewModelScope.launch {
            repository.removeMonitor(monitorId).collect {
                _toastEvent.value = if (it is Result.Success) "Izin monitor berhasil dicabut" else "Gagal"
                fetchProfile() // Refresh
            }
        }
    }

    // Dipanggil pas tombol Logout di-klik
    fun onLogoutClicked() {
        viewModelScope.launch {
            authRepository.logout() // Panggil dari AuthRepository
            _logoutEvent.emit(true)
        }
    }

    // Dipanggil UI pas toast udah muncul
    fun onToastHandled() {
        _toastEvent.value = null
    }
}