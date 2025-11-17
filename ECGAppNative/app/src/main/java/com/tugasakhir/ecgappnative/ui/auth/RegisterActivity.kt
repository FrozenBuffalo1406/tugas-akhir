package com.tugasakhir.ecgappnative.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.tugasakhir.ecgappnative.MainApplication
import com.tugasakhir.ecgappnative.data.model.RegisterRequest
import com.tugasakhir.ecgappnative.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    private val factory by lazy { (application as MainApplication).viewModelFactory }
    private val viewModel: RegisterViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener { handleRegister() }
        binding.tvGoToLogin.setOnClickListener {
            goToLogin()
        }

        setupObservers()
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            setLoading(isLoading)
        }

        viewModel.registerResult.observe(this) { result ->
            result.fold(
                onSuccess = {
                    Toast.makeText(this, "Registrasi Berhasil! Silakan Login.", Toast.LENGTH_LONG).show()
                    goToLogin()
                },
                onFailure = {
                    Toast.makeText(this, it.message ?: "Registrasi Gagal", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun handleRegister() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Semua field wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.register(RegisterRequest(email, password, name))
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !isLoading
    }
}