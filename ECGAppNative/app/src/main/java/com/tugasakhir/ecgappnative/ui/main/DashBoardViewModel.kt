package com.tugasakhir.ecgappnative.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.ecgappnative.data.model.*
import com.tugasakhir.ecgappnative.data.repository.MainRepository
import kotlinx.coroutines.launch
import retrofit2.Response

class DashboardViewModel(private val repository: MainRepository) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _dashboardItems = MutableLiveData<List<DashboardItem>>()
    val dashboardItems: LiveData<List<DashboardItem>> = _dashboardItems

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    fun loadDashboard() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = repository.getDashboard()
                if (response.isSuccessful) {
                    _dashboardItems.value = response.body()?.data ?: emptyList()
                } else {
                    _toastMessage.value = "Gagal memuat data dashboard"
                }
            } catch (e: Exception) {
                _toastMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun handleApiCall(
        apiCall: suspend () -> Response<GeneralResponse>,
        successMsg: String,
        errorMsg: String
    ) {
        try {
            val response = apiCall()
            if (response.isSuccessful) {
                _toastMessage.value = response.body()?.message ?: successMsg
                loadDashboard() // Refresh data
            } else {
                _toastMessage.value = response.body()?.error ?: response.errorBody()?.string() ?: errorMsg
            }
        } catch (e: Exception) {
            _toastMessage.value = "Error: ${e.message}"
        }
    }

    fun claimDevice(req: ClaimRequest) = viewModelScope.launch {
        handleApiCall({ repository.claimDevice(req) }, "Device diklaim!", "Gagal klaim")
    }

    fun addCorrelative(req: AddCorrelativeRequest) = viewModelScope.launch {
        handleApiCall({ repository.addCorrelative(req) }, "Kerabat ditambah!", "Gagal nambah")
    }

    fun unclaimDevice(req: UnclaimRequest) = viewModelScope.launch {
        handleApiCall({ repository.unclaimDevice(req) }, "Device dilepas!", "Gagal lepas")
    }

    fun removeCorrelative(req: RemoveCorrelativeRequest) = viewModelScope.launch {
        handleApiCall({ repository.removeCorrelative(req) }, "Kerabat dihapus!", "Gagal hapus")
    }
}