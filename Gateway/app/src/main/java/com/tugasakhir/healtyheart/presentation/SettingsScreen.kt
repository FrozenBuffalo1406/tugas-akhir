package com.tugasakhir.healtyheart.presentation

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.tugasakhir.healtyheart.ui.theme.HealtyHeartTheme
import com.tugasakhir.healtyheart.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    // settingsViewModel: SettingsViewModel = hiltViewModel() // Jika ada SettingsViewModel
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Dimens.PaddingLarge)
        ) {
            Text("General Settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

            SettingItem(
                title = "Receive Notifications",
                description = "Get alerts for critical ECG changes.",
                checked = true // settingsViewModel.notificationEnabled.collectAsState().value
            ) { newValue ->
                // settingsViewModel.toggleNotifications(newValue)
            }
            Divider(modifier = Modifier.padding(vertical = Dimens.PaddingSmall))

            SettingItem(
                title = "Sync Data Automatically",
                description = "Upload data to cloud in background.",
                checked = false // settingsViewModel.autoSyncEnabled.collectAsState().value
            ) { newValue ->
                // settingsViewModel.toggleAutoSync(newValue)
            }
            Divider(modifier = Modifier.padding(vertical = Dimens.PaddingSmall))

            // Contoh lain
            TextButton(onClick = { /* Handle logout */ }) {
                Text("Logout")
            }
        }
    }
}

@Composable
fun SettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isChecked by remember { mutableStateOf(checked) } // State internal untuk toggle

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.PaddingMedium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = {
                isChecked = it // Update state internal
                onCheckedChange(it) // Panggil callback ke ViewModel
            }
        )
    }
}

// --- Preview untuk SettingsScreen ---
@Preview(showBackground = true, name = "Settings Screen Preview")
@Composable
fun SettingsScreenPreview() {
    HealtyHeartTheme {
        val dummyNavController = rememberNavController()
        SettingsScreen(navController = dummyNavController)
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Settings Dark Preview")
@Composable
fun SettingsScreenDarkPreview() {
    HealtyHeartTheme {
        val dummyNavController = rememberNavController()
        SettingsScreen(navController = dummyNavController)
    }
}

