package com.tugasakhir.ecgapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.ecgapp.core.navigation.Screen
import com.tugasakhir.ecgapp.data.local.UserPreferences
import com.tugasakhir.ecgapp.data.remote.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val tokenManager: TokenManager // <-- Inject TokenManager
) : ViewModel() {

    private val _startDestination = MutableStateFlow("")
    val startDestination = _startDestination.asStateFlow()

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            // 1. Cek Gudang Awet (DataStore)
            val savedToken = userPreferences.authToken.first()

            if (savedToken.isNullOrEmpty()) {
                // 2. GAK ADA TOKEN -> Ke Login
                _startDestination.value = Screen.Login.route
            } else {
                // 3. ADA TOKEN!
                // Salin ke Gudang Cepat (RAM) biar Interceptor siap kerja
                tokenManager.authToken = savedToken

                // 4. Langsung gass ke Dashboard
                _startDestination.value = Screen.Dashboard.route
            }
        }
    }
}