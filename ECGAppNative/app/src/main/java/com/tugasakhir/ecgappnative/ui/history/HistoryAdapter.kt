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

// PERUBAHAN: Tambahin 'onItemClick' di constructor
class HistoryAdapter(
    private val onItemClick: (HistoryEntity) -> Unit
) : ListAdapter<HistoryEntity, HistoryAdapter.ViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryEntity) {
            binding.tvClassification.text = item.classification
            binding.tvHeartRate.text = "${item.heartRate?.toInt() ?: "--"} BPM"

            // Format timestamp (amanin biar gak crash kalo format aneh)
            try {
                val cleanTimestamp = item.timestamp.replace("Z", "+00:00")
                val instant = Instant.parse(cleanTimestamp)
                val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                binding.tvTimestamp.text = localDateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
            } catch (_: Exception) {
                binding.tvTimestamp.text = item.timestamp
            }

            // INI DIA: Pasang listener biar bisa diklik!
            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}

class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryEntity>() {
    override fun areItemsTheSame(oldItem: HistoryEntity, newItem: HistoryEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: HistoryEntity, newItem: HistoryEntity): Boolean {
        return oldItem == newItem
    }
}