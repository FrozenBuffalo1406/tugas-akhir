// com/proyeklo/ecgapp/MainViewModel.kt
package com.tugasakhir.ecgapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.ecgapp.core.navigation.Screen
import com.tugasakhir.ecgapp.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel ini tugasnya cuma satu:
 * Ngecek ke DataStore, "User udah punya token apa belum?"
 * Kalo udah, start app di Dashboard. Kalo belum, start di Login.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _startDestination = MutableStateFlow("")
    val startDestination = _startDestination.asStateFlow()

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            val token = userPreferences.authToken.first()
            if (token.isNullOrEmpty()) {
                _startDestination.value = Screen.Login.route
            } else {
                _startDestination.value = Screen.Dashboard.route
            }
        }
    }
}