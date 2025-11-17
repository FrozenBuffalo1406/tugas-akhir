package com.tugasakhir.ecgapp.ui.screen.login

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.tugasakhir.ecgapp.core.navigation.Screen
import com.tugasakhir.ecgapp.core.utils.Result

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Dengerin dua state dari ViewModel
    val isLoading by viewModel.isLoading.collectAsState()
    val loginEvent by viewModel.loginEvent.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // LaunchedEffect buat nanganin event (navigasi/toast)
    LaunchedEffect(loginEvent) {
        when (val event = loginEvent) {
            is Result.Success -> {
                // 1. Reset event-nya dulu
                viewModel.onEventHandled()
                // 2. Baru navigasi
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
            is Result.Error -> {
                // 1. Tunjukin error
                val message = event.message ?: "Error tidak diketahui"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                // 2. Reset event-nya
                viewModel.onEventHandled()
            }
            else -> {} // Loading atau null gak ngapa-ngapain di sini
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Login EKG App", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.login(email, password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading // Tombol mati pas loading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Login")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            ClickableText(
                text = AnnotatedString("Belum punya akun? Daftar di sini"),
                style = MaterialTheme.typography.bodyMedium,
                onClick = {
                    if (!isLoading) {
                        navController.navigate(Screen.Register.route)
                    }
                }
            )
        }
    }
}