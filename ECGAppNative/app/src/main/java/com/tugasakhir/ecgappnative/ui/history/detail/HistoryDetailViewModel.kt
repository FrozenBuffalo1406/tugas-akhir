package com.tugasakhir.ecgappnative.ui.history.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.ecgappnative.data.model.HistoryDetailResponse
import com.tugasakhir.ecgappnative.data.repository.MainRepository
import com.tugasakhir.ecgappnative.data.utils.ResultWrapper
import kotlinx.coroutines.launch

class HistoryDetailViewModel(private val repository: MainRepository) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private val _detailData = MutableLiveData<HistoryDetailResponse>()
    val detailData: LiveData<HistoryDetailResponse> = _detailData

    fun loadDetail(readingId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = repository.getReadingDetail(readingId)) {
                is ResultWrapper.Success -> {
                    _detailData.value = result.data
                    _isLoading.value = false
                }
                is ResultWrapper.Error -> {
                    _toastMessage.value = result.message ?: "Gagal memuat detail"
                    _isLoading.value = false
                }
                is ResultWrapper.NetworkError -> {
                    _toastMessage.value = "Kesalahan jaringan"
                    _isLoading.value = false
                }
                is ResultWrapper.Loading -> {
                    _toastMessage.value = "Memuat detail"
                    _isLoading.value = true
                }
            }
        }
    }
}