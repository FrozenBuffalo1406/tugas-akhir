package com.tugasakhir.ecgappnative.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tugasakhir.ecgappnative.data.local.HistoryEntity
import com.tugasakhir.ecgappnative.databinding.ItemHistoryBinding
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HistoryAdapter : ListAdapter<HistoryEntity, HistoryAdapter.ViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }
    class ViewHolder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryEntity) {
            binding.tvClassification.text = item.classification
            binding.tvHeartRate.text = "${item.heartRate ?: "--"} BPM"

            // Format timestamp
            try {
                val instant = Instant.parse(item.timestamp.replace("Z", "+00:00"))
                val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                binding.tvTimestamp.text = localDateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss"))
            } catch (_: Exception) {
                binding.tvTimestamp.text = item.timestamp
            }

            // (Opsional) Tambahin OnClickListener jika perlu
            // binding.root.setOnClickListener { ... }
        }
    }
}

// DiffUtil biar RecyclerView update-nya pinter
class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryEntity>() {
    override fun areItemsTheSame(oldItem: HistoryEntity, newItem: HistoryEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: HistoryEntity, newItem: HistoryEntity): Boolean {
        return oldItem == newItem
    }
}