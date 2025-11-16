// com/tugasakhir/ecgapp/ui/screen/login/LoginScreen.kt
package com.tugasakhir.ecgapp.ui.screen.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.tugasakhir.ecgapp.core.navigation.Screen
import com.tugasakhir.ecgapp.core.utils.Result
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isLoading by viewModel.isLoading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val loginEvent by viewModel.loginEvent.collectAsState() // Ganti nama

    LaunchedEffect(loginEvent) {
        when (val state = loginEvent) {
            is Result.Success -> {
                viewModel.onEventHandled()
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            }
            is Result.Error -> {
                snackbarHostState.showSnackbar(state.message ?: "Error tidak diketahui")
                viewModel.onEventHandled()
            }
            is Result.Loading ->  {}
            null -> {}
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
                enabled = !isLoading
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