// com/tugasakhir/ecgapp/ui/screen/history/components/HistoryItemCard.kt
package com.tugasakhir.ecgapp.ui.screen.history.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tugasakhir.ecgapp.core.utils.formatTimestamp
import com.tugasakhir.ecgapp.data.model.EcgReading

@Composable
fun HistoryItemCard(reading: EcgReading) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reading.classification,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (reading.classification) {
                        "PVC" -> Color.Red
                        "Other" -> Color.Blue
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = formatTimestamp(reading.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Text(
                text = "${reading.heartRate?.toInt() ?: "--"} BPM",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}