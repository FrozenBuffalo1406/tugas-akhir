package com.tugasakhir.healtyheart.presentation

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.tugasakhir.healtyheart.ui.theme.HealtyHeartTheme
import com.tugasakhir.healtyheart.ui.theme.Dimens
import com.tugasakhir.healtyheart.viewmodels.HealthViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.Instant
import kotlin.text.format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    healthViewModel: HealthViewModel = hiltViewModel() // Inject HealthViewModel
) {
    val latestECGData by healthViewModel.latestECGData.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Dimens.PaddingLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Real-time ECG Data", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(Dimens.PaddingLarge))

            // Tampilkan data EKG terbaru
            latestECGData?.let { data ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.PaddingMedium)
                ) {
                    Column(
                        modifier = Modifier.padding(Dimens.PaddingLarge)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Device: ${data.deviceName}", style = MaterialTheme.typography.titleMedium)
                        Text("Value: \"${"%.2f".format(data.value.toFloatOrNull() ?: 0f)}\"", style = MaterialTheme.typography.headlineSmall)
                        Text("Label: ${data.label}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        Text("Timestamp: ${Instant.ofEpochMilli(data.timestamp.toLongOrNull() ?: 0L)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } ?: run {
                Text("Waiting for ECG data...", style = MaterialTheme.typography.bodyLarge)
                CircularProgressIndicator(modifier = Modifier.padding(Dimens.PaddingLarge))
            }
        }
    }
}

// --- Preview untuk DashboardScreen ---
@Preview(showBackground = true, name = "Dashboard Preview")
@Composable
fun DashboardScreenPreview() {
    HealtyHeartTheme {
        // Buat NavController dummy untuk preview
        val dummyNavController = rememberNavController()
        DashboardScreen(navController = dummyNavController)
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dashboard Dark Preview")
@Composable
fun DashboardScreenDarkPreview() {
    HealtyHeartTheme {
        val dummyNavController = rememberNavController()
        DashboardScreen(navController = dummyNavController)
    }
}

