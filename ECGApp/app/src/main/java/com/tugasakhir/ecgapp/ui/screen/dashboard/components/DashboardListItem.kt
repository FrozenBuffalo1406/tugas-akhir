// com/tugasakhir/ecgapp/ui/screen/dashboard/components/DashboardListItem.kt
package com.tugasakhir.ecgapp.ui.screen.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tugasakhir.ecgapp.core.utils.formatTimestamp
import com.tugasakhir.ecgapp.data.model.DashboardItem

@Composable
fun DashboardListItem(
    item: DashboardItem,
    onHistoryClick: () -> Unit
) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (item.type == "self") "Data Saya" else item.userEmail,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "(${item.deviceName})",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Heart Rate", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = "${item.heartRate?.toInt() ?: "--"}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("BPM", style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Detak Terakhir", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = item.prediction,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (item.prediction) {
                            "PVC" -> Color.Red
                            "Other" -> Color.Blue
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(formatTimestamp(item.timestamp), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onHistoryClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Lihat Riwayat Lengkap")
            }
        }
    }
}