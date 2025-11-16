// com/tugasakhir/ecgapp/ui/screen/dashboard/DashboardScreen.kt
package com.tugasakhir.ecgapp.ui.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.tugasakhir.ecgapp.core.navigation.Screen
import com.tugasakhir.ecgapp.core.utils.Result
import com.tugasakhir.ecgapp.ui.screen.dashboard.components.DashboardListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.dashboardState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    IconButton(onClick = { viewModel.loadDashboard() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profil")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val result = state) {
                is Result.Loading -> {
                    if (result.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
                is Result.Success -> {
                    val items = result.data.data
                    if (items.isEmpty()) {
                        Text("Data kosong. Claim device di menu Profil.", modifier = Modifier.align(Alignment.Center).padding(16.dp))
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(items, key = { it.userId.toString() + it.deviceName }) { item ->
                                DashboardListItem(
                                    item = item,
                                    onHistoryClick = {
                                        navController.navigate(
                                            Screen.History.createRoute(item.userId)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                is Result.Error -> {
                    Text(
                        text = result.message ?: "Gagal memuat data",
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                null -> {}
            }
        }
    }
}