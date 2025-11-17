package com.tugasakhir.ecgapp.ui.screen.profile

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.tugasakhir.ecgapp.core.navigation.Screen
import com.tugasakhir.ecgapp.core.utils.QrCodeGenerator
import com.tugasakhir.ecgapp.core.utils.QrCodeScanner
import com.tugasakhir.ecgapp.core.utils.Result
import com.tugasakhir.ecgapp.data.remote.response.ProfileResponse
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val profileState by viewModel.profileState.collectAsState()
    val toastEvent by viewModel.toastEvent.collectAsState()
    val context = LocalContext.current

    // State buat nentuin mode scan
    var isScanning by remember { mutableStateOf(false) }

    // Dengerin event toast
    LaunchedEffect(toastEvent) {
        toastEvent?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.onToastHandled()
        }
    }

    // Dengerin event logout
    LaunchedEffect(key1 = Unit) {
        viewModel.logoutEvent.collectLatest {
            // Kalo sukses logout, balik ke Login
            navController.navigate(Screen.Login.route) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // --- LOGIC AUTO-REFRESH (ON_RESUME) ---
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Kalo lagi gak scan DAN layarnya resume
            if (event == Lifecycle.Event.ON_RESUME && !isScanning) {
                viewModel.fetchProfile()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    // --- END LOGIC AUTO-REFRESH ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isScanning) "Scan Kode QR" else "Profil & Kerabat") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isScanning) {
                            isScanning = false // Back dari scanner
                        } else {
                            navController.popBackStack() // Back dari profile
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                // Tombol Logout
                actions = {
                    if (!isScanning) {
                        IconButton(onClick = { viewModel.onLogoutClicked() }) {
                            Icon(Icons.Default.Logout, "Logout")
                        }
                    }
                }
            )
        },
        // Tombol Scan FAB
        floatingActionButton = {
            if (!isScanning) {
                LargeFloatingActionButton(
                    onClick = { isScanning = true } // Nyalain mode scan
                ) {
                    Icon(Icons.Default.QrCodeScanner, "Scan QR", modifier = Modifier.size(36.dp))
                }
            }
        }
    ) { padding ->

        if (isScanning) {
            // TAMPILKAN SCANNER
            QrCodeScanner(
                modifier = Modifier.fillMaxSize().padding(padding),
                onQrCodeScanned = { scannedCode ->
                    viewModel.onQrCodeScanned(scannedCode)
                    isScanning = false // Tutup scanner kalo udah dapet
                }
            )
        } else {
            // TAMPILKAN KONTEN PROFILE
            when (val state = profileState) {
                is Result.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is Result.Success -> {
                    ProfileContent(
                        padding = padding,
                        profileData = state.data,
                        onRemovePatient = { viewModel.removePatient(it) },
                        onRemoveMonitor = { viewModel.removeMonitor(it) }
                    )
                }
                is Result.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Gagal load data: ${state.message}") }
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
fun ProfileContent(
    padding: PaddingValues,
    profileData: ProfileResponse,
    onRemovePatient: (Int) -> Unit,
    onRemoveMonitor: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp), // Padding bawah biar FAB gak nutupin
        horizontalAlignment = Alignment.Start
    ) {

        // --- BAGIAN QR CODE ---
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Kode Saya", style = MaterialTheme.typography.headlineSmall)
                Text("Minta kerabat Anda scan kode ini", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                val myUserIdString = profileData.user.id.toString()

                // Panggil QrCodeGenerator dari QRHelper.kt
                QrCodeGenerator(
                    text = myUserIdString,
                    modifier = Modifier.size(250.dp)
                )

                Text(
                    "ID Anda: $myUserIdString",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Divider(modifier = Modifier.padding(vertical = 24.dp))
            }
        }

        // --- DAFTAR PASIEN (YANG SAYA MONITOR) ---
        item {
            Text("Pasien Saya", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (profileData.patients.isEmpty()) {
            item { Text("Anda belum memonitor siapa pun.", style = MaterialTheme.typography.bodySmall) }
        } else {
            items(profileData.patients) { patient ->
                CorrelativeItem(
                    name = patient.name,
                    email = patient.email,
                    actionText = "Hapus",
                    onActionClick = { onRemovePatient(patient.id) }
                )
            }
        }

        item {
            Divider(modifier = Modifier.padding(vertical = 24.dp))
            Text("Monitor Saya", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // --- DAFTAR MONITOR (YANG MEMONITOR SAYA) ---
        if (profileData.monitors.isEmpty()) {
            item { Text("Belum ada yang memonitor Anda.", style = MaterialTheme.typography.bodySmall) }
        } else {
            items(profileData.monitors) { monitor ->
                CorrelativeItem(
                    name = monitor.name,
                    email = monitor.email,
                    actionText = "Cabut Izin",
                    onActionClick = { onRemoveMonitor(monitor.id) }
                )
            }
        }
    }
}

// Composable buat nampilin satu baris kerabat
@Composable
fun CorrelativeItem(
    name: String,
    email: String,
    actionText: String,
    onActionClick: () -> Unit
) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold)
                Text(email, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(actionText)
            }
        }
    }
}