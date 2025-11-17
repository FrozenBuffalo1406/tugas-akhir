package com.tugasakhir.ecgappnative.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tugasakhir.ecgappnative.MainApplication
import com.tugasakhir.ecgappnative.data.repository.MainRepository
import com.tugasakhir.ecgappnative.ui.auth.LoginActivity
import kotlinx.coroutines.launch

/**
 * Activity Induk.
 * Semua Activity yang butuh login (Dashboard, Profile, History)
 * harus 'extends' ke sini, BUKAN ke AppCompatActivity.
 */
abstract class BaseActivity : AppCompatActivity() {

    private lateinit var sessionRepository: MainRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ambil repository
        sessionRepository = (application as MainApplication).mainRepository

        // Langsung pasang "kuping"
        observeSessionExpired()
    }
    private fun observeSessionExpired() {
        lifecycleScope.launch {
            sessionRepository.onSessionExpired.collect {
                Toast.makeText(
                    this@BaseActivity,
                    "Sesi Anda telah berakhir. Silakan login kembali.",
                    Toast.LENGTH_LONG
                ).show()

                // Nendang ke Login
                goToLogin()
            }
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}