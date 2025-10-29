package com.tugasakhir.healtyheart.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.healtyheart.models.Device
import com.tugasakhir.healtyheart.models.ECGData
import com.tugasakhir.healtyheart.repositories.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val healthRepository: HealthRepository
) : ViewModel() {

    private val _latestECGData = MutableStateFlow<ECGData?>(null)
    val latestECGData: StateFlow<ECGData?> = _latestECGData.asStateFlow()

    private val _userDevices = MutableStateFlow<List<Device>>(emptyList())
    val userDevices: StateFlow<List<Device>> = _userDevices.asStateFlow()

    init {
        loadInitialData()
        observeRealtimeData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _userDevices.value = healthRepository.getUserDevices()
        }
    }

    private fun observeRealtimeData() {
        viewModelScope.launch {
            healthRepository.getLatestECGData().collect { data ->
                _latestECGData.value = data
            }
        }
    }

    fun requestClassification(data: ECGData) {
        viewModelScope.launch {
            // Logika untuk request klasifikasi ke server via repository
            val result = healthRepository.requestClassificationFromServer(data)
            // Lakukan sesuatu dengan hasilnya...
        }
    }
}

