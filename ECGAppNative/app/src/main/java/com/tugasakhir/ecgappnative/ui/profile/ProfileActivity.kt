package com.tugasakhir.ecgappnative.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.tugasakhir.ecgappnative.MainApplication
import com.tugasakhir.ecgappnative.databinding.ActivityProfileBinding
import com.tugasakhir.ecgappnative.ui.auth.LoginActivity

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    // Inisialisasi ViewModel dengan Factory
    private val factory by lazy { (application as MainApplication).viewModelFactory }
    private val viewModel: ProfileViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Panggil ViewModel
        viewModel.loadProfile()

        // Setup listener
        binding.btnLogout.setOnClickListener {
            viewModel.logout()
        }

        // Setup observer untuk data dari ViewModel
        setupObservers()
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            setLoading(isLoading)
        }

        viewModel.toastMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        viewModel.logoutComplete.observe(this) { hasLoggedOut ->
            if (hasLoggedOut) {
                performLogoutNavigation()
            }
        }

        viewModel.profileState.observe(this) { state ->
            // Update UI dengan data dari ViewModel
            val profile = state.profile
            profile.user.let { user ->
                binding.tvProfileName.text = user.name ?: "Tanpa Nama"
                binding.tvProfileEmail.text = user.email
            }

            // Set QR Code Bitmap
            binding.ivMyQRCode.setImageBitmap(state.qrCodeBitmap)

            // Tampilkan daftar pasien (jika ada)
            profile.patients.let { list ->
                if (list.isNotEmpty()) {
                    binding.tvPatientsTitle.visibility = View.VISIBLE
                    binding.tvPatientsList.visibility = View.VISIBLE
                    binding.tvPatientsList.text = list.joinToString("\n") { "- ${it.name} (${it.email})" }
                } else {
                    binding.tvPatientsTitle.visibility = View.GONE
                    binding.tvPatientsList.visibility = View.GONE
                }
            }

            // Tampilkan daftar monitor (jika ada)
            profile.monitoredBy.let { list ->
                if (list.isNotEmpty()) {
                    binding.tvMonitorsTitle.visibility = View.VISIBLE
                    binding.tvMonitorsList.visibility = View.VISIBLE
                    binding.tvMonitorsList.text = list.joinToString("\n") { "- ${it.name} (${it.email})" }
                } else {
                    binding.tvMonitorsTitle.visibility = View.GONE
                    binding.tvMonitorsList.visibility = View.GONE
                }
            }
        }
    }

    private fun performLogoutNavigation() {
        Toast.makeText(this, "Logout berhasil", Toast.LENGTH_SHORT).show()

        // Lempar ke LoginActivity
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}