// com/tugasakhir/ecgapp/ui/screen/dashboard/DashboardViewModel.kt
package com.tugasakhir.ecgapp.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.ecgapp.core.utils.Result
import com.tugasakhir.ecgapp.data.remote.response.DashboardResponse
import com.tugasakhir.ecgapp.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: UserRepository
) : ViewModel() {

    private val _dashboardState = MutableStateFlow<Result<DashboardResponse>?>(null)
    val dashboardState = _dashboardState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            repository.getDashboard().collect {
                _dashboardState.value = it
            }
        }
    }
}