package com.tugasakhir.ecgapp.ui.screen.profile

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.tugasakhir.ecgapp.core.navigation.Screen
import com.tugasakhir.ecgapp.core.utils.Result
import com.tugasakhir.ecgapp.core.utils.QrCodeGenerator
import com.tugasakhir.ecgapp.core.utils.QrCodeScanner
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

    var isScanning by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Panggil fetch-nya lagi
                viewModel.fetchProfile()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    // Tunjukin Toast/Snackbar kalo ada event
    LaunchedEffect(toastEvent) {
        toastEvent?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.onToastHandled()
        }
    }
    LaunchedEffect(key1 = Unit) {
        viewModel.logoutEvent.collectLatest {
            navController.navigate(Screen.Login.route) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isScanning) "Scan Kode QR" else "Profil & Kerabat") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isScanning) {
                            isScanning = false // Kalo lagi scan, tombol back buat nutup scanner
                        } else {
                            navController.popBackStack() // Kalo di profile, back ke dashboard
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    if (!isScanning) { // Tunjukin cuma pas di halaman profile
                        IconButton(onClick = { viewModel.onLogoutClicked() }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, "Logout")
                        }
                    }
                }
            )

        },
        floatingActionButton = {
            if (!isScanning) { // Tunjukin cuma pas di halaman profile
                LargeFloatingActionButton(
                    onClick = { isScanning = true } // Nyalain mode scan
                ) {
                    Icon(Icons.Default.QrCodeScanner, "Scan QR", modifier = Modifier.size(36.dp))
                }
            }
        }

    ) { padding ->
        if (isScanning) {
            QrCodeScanner(
                modifier = Modifier.fillMaxSize().padding(padding),
                onQrCodeScanned = { scannedCode ->
                    viewModel.onQrCodeScanned(scannedCode)
                    isScanning = false
                }
            )
        } else {
            when (val state = profileState) {
                is Result.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is Result.Success -> {
                    ProfileContent(
                        padding = padding,
                        profileData = state.data,
                        onRemovePatient = { viewModel.removePatient(it) },
                        onRemoveMonitor = { viewModel.removeMonitor(it) },
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
    onRemoveMonitor: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        horizontalAlignment = Alignment.Start
    ) {

        // --- BAGIAN QR CODE LO ---
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Kode Saya", style = MaterialTheme.typography.headlineSmall)
                Text("Minta kerabat Anda scan kode ini", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                val myUserIdString = profileData.user.id.toString()

                // --- PANGGIL COMPOSABLE GENERATOR LO ---
                QrCodeGenerator(
                    text = myUserIdString,
                    modifier = Modifier.size(250.dp)
                )
                // --- BERES ---

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pasien Saya", style = MaterialTheme.typography.headlineSmall)
                // --- TOMBOL SCAN BARU (PENGGANTI FAB) ---
            }
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

@Composable
fun CorrelativeItem(
    name: String, // <-- Parameter 'name'
    email: String, // <-- Parameter 'email'
    actionText: String, // <-- Parameter 'actionText'
    onActionClick: () -> Unit // <-- Parameter 'onActionClick'
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
                Text(name, fontWeight = FontWeight.Bold) // <-- Pake 'name'
                Text(email, style = MaterialTheme.typography.bodySmall, color = Color.Gray) // <-- Pake 'email'
            }
            Button(
                onClick = onActionClick, // <-- Pake 'onActionClick'
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(actionText) // <-- Pake 'actionText'
            }
        }
    }
}