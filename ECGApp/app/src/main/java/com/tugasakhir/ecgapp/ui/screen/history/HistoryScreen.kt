package com.tugasakhir.ecgapp.ui.screen.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.compose.itemKey
import androidx.paging.compose.itemContentType
import androidx.paging.compose.collectAsLazyPagingItems
import com.tugasakhir.ecgapp.ui.screen.history.components.HistoryItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    userId: Int,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val historyItems = viewModel.historyData.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat EKG (User: $userId)") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                count = historyItems.itemCount,
                key = historyItems.itemKey { it },
                contentType = historyItems.itemContentType { "MyPagingItems" }
            ) { reading ->
                val reading = historyItems[reading]
                if (reading != null) {
                    HistoryItemCard(reading)
                } else {
                    // TODO: Bikin placeholder/shimmer loading
                    Text("Loading...")
                }
            }

            // TODO: Handle Paging load states (refresh, error, etc)
            // pake historyItems.loadState
        }
    }
}
