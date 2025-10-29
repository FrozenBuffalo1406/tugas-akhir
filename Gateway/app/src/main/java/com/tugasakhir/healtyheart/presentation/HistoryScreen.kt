package com.tugasakhir.healtyheart.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.tugasakhir.healtyheart.models.Device
import com.tugasakhir.healtyheart.viewmodels.HealthViewModel

@Composable
fun HistoryScreen(navController: NavController, healthViewModel: HealthViewModel = viewModel()) {
    val devices by healthViewModel.userDevices.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text("Device History", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
        }

        items(items = devices, key = { device -> device.id }) { device ->
            DeviceHistoryCard(device) {
                // Navigasi ke detail history device nanti di sini
                // navController.navigate("historyDetail/${device.id}")
            }
        }
    }
}

@Composable
fun DeviceHistoryCard(device: Device, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Device Name: ${device.name}", style = MaterialTheme.typography.titleMedium)
            Text(text = "Patient: ${device.patientName}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Last Active: ${device.lastActive}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

