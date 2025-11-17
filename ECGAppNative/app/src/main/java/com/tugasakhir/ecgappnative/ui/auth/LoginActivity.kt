package com.tugasakhir.ecgappnative.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tugasakhir.ecgappnative.MainApplication
import com.tugasakhir.ecgappnative.data.model.LoginRequest
import com.tugasakhir.ecgappnative.databinding.ActivityLoginBinding
import com.tugasakhir.ecgappnative.ui.main.DashboardActivity
import com.tugasakhir.ecgappnative.utils.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager

    // Ambil ViewModelFactory dari Application
    private val factory by lazy { (application as MainApplication).viewModelFactory }
    private val viewModel: LoginViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(applicationContext)

        // Cek jika sudah login, lempar ke Dashboard (Logic ini tetap di View)
        lifecycleScope.launch {
            if (sessionManager.tokenFlow.first() != null) {
                goToDashboard()
            }
        }

        binding.btnLogin.setOnClickListener { handleLogin() }
        binding.tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Observe LiveData dari ViewModel
        setupObservers()
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            setLoading(isLoading)
        }

        viewModel.loginResult.observe(this) { result ->
            result.fold(
                onSuccess = {
                    // Sukses, logic disimpan di repo, View cuma pindah
                    Toast.makeText(this, "Login Berhasil!", Toast.LENGTH_SHORT).show()
                    goToDashboard()
                },
                onFailure = {
                    Toast.makeText(this, it.message ?: "Login Gagal", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun handleLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email dan Password tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }

        // Panggil ViewModel
        viewModel.login(LoginRequest(email, password))
    }

    private fun goToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
    }
}