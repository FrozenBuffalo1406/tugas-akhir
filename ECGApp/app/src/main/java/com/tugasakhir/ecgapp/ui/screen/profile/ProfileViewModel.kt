package com.tugasakhir.ecgapp.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.ecgapp.core.utils.Result
import com.tugasakhir.ecgapp.data.remote.response.ProfileResponse
import com.tugasakhir.ecgapp.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: UserRepository
) : ViewModel() {

    // Nampung data profile (yg isinya ada share_code, list kerabat, list device)
    private val _profileState = MutableStateFlow<Result<ProfileResponse>?>(null)
    val profileState = _profileState.asStateFlow()

    // State buat ngasih tau UI (misal: "Sukses nambah kerabat!")
    private val _toastEvent = MutableStateFlow<String?>(null)
    val toastEvent = _toastEvent.asStateFlow()

    init {
        // Langsung fetch data profile pas ViewModel dibikin
        fetchProfile()
    }

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
            // Coba tambahin kerabat dulu
            repository.addCorrelative(scannedCode).collect { result ->
                when (result) {
                    is Result.Success -> _toastEvent.value = "Kerabat berhasil ditambahkan!"
                    is Result.Error -> {
                        // Kalo gagal, mungkin itu kode device? Coba claim device
                        // (Ini cuma contoh, logic-nya bisa lo kompleksin)
                        // Kita asumsi kode device ada MAC address, jadi beda format
                        if (scannedCode.contains(":")) {
                            claimScannedDevice(scannedCode)
                        } else {
                            _toastEvent.value = result.message ?: "Gagal menambah kerabat"
                        }
                    }
                    is Result.Loading -> {}
                }
            }
            fetchProfile() // Refresh data profile setelah nambah
        }
    }

    // Lo bisa bikin fungsi spesifik buat claim device
    private fun claimScannedDevice(macAddress: String) {
        viewModelScope.launch {
            // Asumsi deviceIdStr dapet dari mana, mungkin dari QR juga?
            // Kita pake "default" dulu
            val deviceIdStr = "ID-Device-dari-QR"
            repository.claimDevice(macAddress, deviceIdStr).collect { /* ... handle result ... */ }
        }
    }

    fun removePatient(patientId: Int) {
        viewModelScope.launch {
            repository.removePatient(patientId).collect {
                _toastEvent.value = if (it is Result.Success) "Pasien berhasil dihapus" else "Gagal"
                fetchProfile() // Refresh
            }
        }
    }

    fun unclaimDevice(deviceIdStr: String) {
        viewModelScope.launch {
            repository.unclaimDevice(deviceIdStr).collect {
                _toastEvent.value = if (it is Result.Success) "Device berhasil dilepas" else "Gagal"
                fetchProfile() // Refresh
            }
        }
    }

    // Buat nge-reset toast event biar gak muncul terus
    fun onToastHandled() {
        _toastEvent.value = null
    }
}