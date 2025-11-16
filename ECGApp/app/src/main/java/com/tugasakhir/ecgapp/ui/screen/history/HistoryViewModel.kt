// com/tugasakhir/ecgapp/ui/screen/history/HistoryViewModel.kt
package com.tugasakhir.ecgapp.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tugasakhir.ecgapp.data.model.EcgReading
import com.tugasakhir.ecgapp.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: UserRepository
) : ViewModel() {

    private val _userId = MutableStateFlow(0)

    private val _filterDay = MutableStateFlow<String?>(null)
    val filterDay = _filterDay.asStateFlow()

    private val _filterClass = MutableStateFlow<String?>(null)
    val filterClass = _filterClass.asStateFlow()

    fun setUserId(id: Int) {
        _userId.value = id
    }

    fun applyFilter(day: String?, classification: String?) {
        _filterDay.value = day
        _filterClass.value = classification
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val historyData: Flow<PagingData<EcgReading>> = combine(
        _userId.filter { it > 0 },
        _filterDay,
        _filterClass
    ) { id, day, classification ->
        Triple(id, day, classification)
    }.flatMapLatest { (id, day, classification) ->
        repository.getHistoryPager(
            userId = id,
            filterDay = day,
            filterClass = classification
        )
    }.cachedIn(viewModelScope)
}