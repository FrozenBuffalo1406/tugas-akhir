package com.tugasakhir.ecgapp.ui.screen.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.tugasakhir.ecgapp.core.utils.Result

// Asumsi responsenya kayak gini, sesuaiin ya
import com.tugasakhir.ecgapp.data.remote.response.ProfileResponse
import com.tugasakhir.ecgapp.data.remote.response.Device // Asumsi
import com.tugasakhir.ecgapp.data.remote.response.User // Asumsi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val profileState by viewModel.profileState.collectAsState()
    val toastEvent by viewModel.toastEvent.collectAsState()
    val context = LocalContext.current

    // --- BAGIAN SCANNER ---
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ScanContract(),
        onResult = { result ->
            if (result.contents == null) {
                Toast.makeText(context, "Dibatalkan", Toast.LENGTH_SHORT).show()
            } else {
                // KIRIM HASIL SCAN KE VIEWMODEL
                viewModel.onQrCodeScanned(result.contents)
            }
        }
    )

    // Tunjukin Toast/Snackbar kalo ada event
    LaunchedEffect(toastEvent) {
        toastEvent?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.onToastHandled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil & Kerabat") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                }
            )
        },
        // Tombol Scan gede di bawah
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = {
                    // MUNCULIN SCANNER-NYA
                    val options = ScanOptions()
                    options.setPrompt("Scan QR Code Kerabat atau Device")
                    options.setBeepEnabled(true)
                    options.setOrientationLocked(false)
                    scannerLauncher.launch(options)
                }
            ) {
                Icon(Icons.Default.QrCodeScanner, "Scan QR", modifier = Modifier.size(36.dp))
            }
        }
    ) { padding ->

        when (val state = profileState) {
            is Result.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is Result.Success -> {
                // Kalo sukses, kita dapet data profile
                ProfileContent(
                    padding = padding,
                    profileData = state.data,
                    onRemovePatient = { viewModel.removePatient(it) },
                    onUnclaimDevice = { viewModel.unclaimDevice(it) }
                )
            }
            is Result.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Gagal load data: ${state.message}") }
            null -> {}
        }
    }
}

@Composable
fun ProfileContent(
    padding: PaddingValues,
    profileData: ProfileResponse, // Asumsi, ganti sama response lo
    onRemovePatient: (Int) -> Unit,
    onUnclaimDevice: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // --- BAGIAN QR CODE LO ---
        item {
            Text("Kode Saya", style = MaterialTheme.typography.headlineSmall)
            Text("Minta kerabat/monitor Anda scan kode ini", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // TAMPILIN QR CODE PAKE LIBRARY
            Box(modifier = Modifier
                .size(250.dp)
                .padding(16.dp), contentAlignment = Alignment.Center) {

                // Ganti "profileData.shareCode" sesuai data di ProfileResponse lo
                val userShareCode = profileData.user.shareCode ?: "KODE_GA_ADA"

                ImageQrCode(
                    content = userShareCode,
                    image = Icons.Default.QrCode2 // Icon di tengah
                )
            }

            Text(userShareCode, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Divider(modifier = Modifier.padding(vertical = 24.dp))
        }

        // --- BAGIAN DEVICE ---
        item {
            Text("Device Saya", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(profileData.devices) { device -> // Asumsi 'devices' itu List<Device>
            DeviceItem(device = device, onUnclaim = { onUnclaimDevice(device.deviceIdStr) }) //
        }

        item {
            Divider(modifier = Modifier.padding(vertical = 24.dp))
            Text("Kerabat Saya", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // --- BAGIAN KERABAT (MONITOR & PASIEN) ---
        items(profileData.monitors) { monitor -> // Asumsi 'monitors' itu List<User>
            CorrelativeItem(user = monitor, onRemove = { onRemovePatient(monitor.id) }) //
        }
        items(profileData.patients) { patient -> // Asumsi 'patients' itu List<User>
            CorrelativeItem(user = patient, onRemove = { onRemovePatient(patient.id) }) //
        }
    }
}

// Composable kecil buat item device
@Composable
fun DeviceItem(device: Device, onUnclaim: () -> Unit) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(device.name, fontWeight = FontWeight.Bold) // Asumsi
                Text(device.macAddress, fontSize = 12.sp, color = Color.Gray) // Asumsi
            }
            Button(onClick = onUnclaim, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Lepas")
            }
        }
    }
}

// Composable kecil buat item kerabat
@Composable
fun CorrelativeItem(user: User, onRemove: () -> Unit) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(user.name, fontWeight = FontWeight.Bold) // Asumsi
                Text(user.email, fontSize = 12.sp, color = Color.Gray) // Asumsi
            }
            Button(onClick = onRemove, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Hapus")
            }
        }
    }
}