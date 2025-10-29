package com.tugasakhir.healtyheart.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tugasakhir.healtyheart.models.User
import com.tugasakhir.healtyheart.repositories.HealthRepository // Gunakan HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val healthRepository: HealthRepository // Inject HealthRepository
) : ViewModel() {

    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile = _userProfile.asStateFlow()

    init {
        fetchUserProfile()
    }

    private fun fetchUserProfile() {
        viewModelScope.launch {
            // Mengambil data user dari repository
            _userProfile.value = healthRepository.getUserProfile()
        }
    }

    // Fungsi lain untuk update profil, dll.
    fun updateUserName(newName: String) {
        viewModelScope.launch {
            _userProfile.value = _userProfile.value?.copy(name = newName)
            // Di sini juga panggil repository.updateUserName(newName)
        }
    }
}

