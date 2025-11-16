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
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {


    // Nampung data profile (user, list monitor, list patient)
    private val _logoutEvent = MutableSharedFlow<Boolean>()
    val logoutEvent = _logoutEvent.asSharedFlow()
    private val _profileState = MutableStateFlow<Result<ProfileResponse>?>(null)
    val profileState = _profileState.asStateFlow()

    private val _toastEvent = MutableStateFlow<String?>(null)
    val toastEvent = _toastEvent.asStateFlow()

    init {
        fetchProfile()
    }

    fun fetchProfile() {
        viewModelScope.launch {
            userRepository.getProfile().collect { //
                _profileState.value = it
            }
        }
    }

    fun onLogoutClicked() {
        viewModelScope.launch {
            // PANGGIL REPO YANG BENER
            authRepository.logout()
            _logoutEvent.emit(true)
        }
    }

    // Dipanggil pas QR scanner dapet hasil
    fun onQrCodeScanned(scannedCode: String) {
        viewModelScope.launch {
            // Langsung panggil addCorrelative. API Flask
            // bakal nanganin scannedCode (yg isinya userId)
            userRepository.addCorrelative(scannedCode).collect { result -> //
                when (result) {
                    is Result.Success -> {
                        _toastEvent.value = result.data.message
                        fetchProfile() // Refresh data biar list-nya update
                    }
                    is Result.Error -> {
                        _toastEvent.value = result.message ?: "Gagal menambah kerabat"
                    }
                    is Result.Loading -> {}
                }
            }
        }
    }

    // Ini buat HAPUS ORANG YG KITA MONITOR (Pasien)
    fun removePatient(patientId: Int) {
        viewModelScope.launch {
            userRepository.removePatient(patientId).collect { //
                _toastEvent.value = if (it is Result.Success) "Pasien berhasil dihapus" else "Gagal"
                fetchProfile() // Refresh
            }
        }
    }

    // Ini buat HAPUS ORANG YG MONITOR KITA (Monitor)
    fun removeMonitor(monitorId: Int) {
        viewModelScope.launch {
            userRepository.removeMonitor(monitorId).collect { //
                _toastEvent.value = if (it is Result.Success) "Izin monitor berhasil dicabut" else "Gagal"
                fetchProfile() // Refresh
            }
        }
    }

    // Buat nge-reset toast event biar gak muncul terus
    fun onToastHandled() {
        _toastEvent.value = null
    }
}