package com.tugasakhir.ecgapp.ui.screen.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.tugasakhir.ecgapp.ui.screen.history.components.HistoryListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    userId: Int,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    LaunchedEffect(userId) {
        viewModel.setUserId(userId)
    }

    val lazyHistoryItems = viewModel.historyData.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat EKG") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ... (Filter UI bisa ditaro di sini) ...

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {

                when (val state = lazyHistoryItems.loadState.refresh) {
                    is LoadState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is LoadState.Error -> {
                        item {
                            Text(
                                text = "Gagal memuat data: ${state.error.message}",
                                color = Color.Red,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    is LoadState.NotLoading -> {
                        if (lazyHistoryItems.itemCount == 0) {
                            item {
                                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Tidak ada riwayat data.",
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                items(
                    count = lazyHistoryItems.itemCount,
                    key = lazyHistoryItems.itemKey { it.id }
                ) { index ->
                    val reading = lazyHistoryItems[index]
                    if (reading != null) {
                        HistoryListItem(reading = reading)
                    }
                }

                when (val state = lazyHistoryItems.loadState.append) {
                    is LoadState.Loading -> {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is LoadState.Error -> {
                        item {
                            Text(
                                text = "Gagal memuat data selanjutnya: ${state.error.message}",
                                color = Color.Red,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}