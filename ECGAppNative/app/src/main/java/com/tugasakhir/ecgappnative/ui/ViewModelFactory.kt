package com.tugasakhir.ecgappnative.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tugasakhir.ecgappnative.data.repository.MainRepository
import com.tugasakhir.ecgappnative.ui.auth.LoginViewModel
import com.tugasakhir.ecgappnative.ui.auth.RegisterViewModel
import com.tugasakhir.ecgappnative.ui.history.HistoryViewModel
import com.tugasakhir.ecgappnative.ui.main.DashboardViewModel
import com.tugasakhir.ecgappnative.ui.profile.ProfileViewModel

/**
 * Factory kustom.
 * Dibutuhkan karena ViewModel kita sekarang punya parameter (MainRepository)
 * di constructor-nya.
 */
class ViewModelFactory(private val repository: MainRepository) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(LoginViewModel::class.java) -> {
                LoginViewModel(repository) as T
            }
            modelClass.isAssignableFrom(RegisterViewModel::class.java) -> {
                RegisterViewModel(repository) as T
            }
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                DashboardViewModel(repository) as T
            }
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                ProfileViewModel(repository) as T
            }
            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> {
                HistoryViewModel(repository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}