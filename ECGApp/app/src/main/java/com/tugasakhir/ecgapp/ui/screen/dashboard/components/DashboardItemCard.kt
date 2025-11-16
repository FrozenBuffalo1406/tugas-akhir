package com.tugasakhir.ecgapp.ui.screen.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tugasakhir.ecgapp.core.navigation.Screen
import com.tugasakhir.ecgapp.data.model.DashboardItem

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DashboardItemCard(item: DashboardItem, navController: NavController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        // Kalo 'self' kasih warna beda
        colors = CardDefaults.cardColors(
            containerColor = if (item.type == "self") MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        ),
        onClick = {
            // Navigasi ke History Screen, kirim userId
            // PENTING: NavigationGraph lo harus di-update buat nerima argumen
            navController.navigate("${Screen.History.route}/${item.userId}")
        }
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = if (item.type == "self") "Data Saya" else "Pasien: ${item.userEmail}",
                style = MaterialTheme.typography.titleMedium
            )
            Text("Device: ${item.deviceName}", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.prediction,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (item.prediction == "Normal") Color.Unspecified else MaterialTheme.colorScheme.error
            )
            Text("Detak Jantung: ${item.heartRate ?: "N/A"} bpm")
            Text(item.timestamp, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}