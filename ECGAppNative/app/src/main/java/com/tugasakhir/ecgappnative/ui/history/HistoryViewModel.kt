package com.tugasakhir.ecgappnative.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.ecgappnative.data.local.HistoryEntity
import com.tugasakhir.ecgappnative.data.repository.MainRepository
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: MainRepository) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _historyItems = MutableLiveData<List<HistoryEntity>>()
    val historyItems: LiveData<List<HistoryEntity>> = _historyItems

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    fun loadHistory(userId: String) {
        if (userId.isEmpty()) {
            _toastMessage.value = "User ID tidak valid"
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Repository sudah ngurusin cache-logic
                val data = repository.getHistory(userId)
                _historyItems.value = data
            } catch (e: Exception) {
                _toastMessage.value = "Gagal memuat history: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

}