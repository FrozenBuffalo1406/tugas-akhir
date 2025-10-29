package com.tugasakhir.healtyheart.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.tugasakhir.healtyheart.viewmodels.HealthViewModel

@Composable
fun DashboardScreen(navController: NavController, healthViewModel: HealthViewModel = viewModel()) {
    val latestEcgData by healthViewModel.latestEcgData.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Real-time ECG Status", style = MaterialTheme.typography.headlineMedium)

        if (latestEcgData == null) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 20.dp))
        } else {
            Card(
                modifier = Modifier.padding(top = 20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Device: ${latestEcgData?.deviceName}", style = MaterialTheme.typography.titleMedium)
                    Text("Timestamp: ${latestEcgData?.timestamp}", style = MaterialTheme.typography.bodyMedium)
                    Text("Heart Status: ${latestEcgData?.label}", style = MaterialTheme.typography.bodyLarge, color = if (latestEcgData?.label == "Normal") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    // Di sini nanti bisa ditambahkan grafik ECG
                    Text("ECG Data: ... (visual chart here)", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

