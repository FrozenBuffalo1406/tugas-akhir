package com.tugasakhir.ecgappnative.ui.main

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tugasakhir.ecgappnative.data.model.DashboardItem
import com.tugasakhir.ecgappnative.databinding.ItemDashboardBinding
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DashboardAdapter(
    private val onUnclaimClick: (String) -> Unit,
    private val onRemoveCorrelativeClick: (Int) -> Unit,
    private val onItemClick: (Int) -> Unit
) : ListAdapter<DashboardItem, DashboardAdapter.ViewHolder>(DashboardDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDashboardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, onUnclaimClick, onRemoveCorrelativeClick, onItemClick)
    }

    class ViewHolder(private val binding: ItemDashboardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: DashboardItem,
            onUnclaimClick: (String) -> Unit,
            onRemoveCorrelativeClick: (Int) -> Unit,
            onItemClick: (Int) -> Unit
        ) {
            binding.tvDeviceName.text = item.deviceName
            binding.tvUserEmail.text = item.userEmail
            binding.tvHRPred.text = "${item.heartRate ?: "--"} BPM | ${item.prediction}"

            // Format timestamp
            try {
                val instant = Instant.parse(item.timestamp.replace("Z", "+00:00"))
                val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                binding.tvTimestamp.text = localDateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss"))
            } catch (_: Exception) {
                binding.tvTimestamp.text = item.timestamp
            }

            // Atur tombol berdasarkan tipe
            if (item.type == "self") {
                binding.btnAction.text = "Unclaim"
                binding.btnAction.setBackgroundColor(Color.parseColor("#D32F2F")) // Merah
                binding.btnAction.setOnClickListener {
                    // TODO: API Dashboard TIDAK mengembalikan 'device_id_str'
                    // Ini adalah kekurangan di API. Kita akali sementara
                    // dengan asumsi deviceName unik, padahal harusnya pakai ID.
                    // MINTA backend tambahkan 'device_id_str' di DashboardResponse

                    // HACK: Asumsi deviceName = device_id_str (SANGAT TIDAK IDEAL)
                    val deviceIdStr = item.deviceName
                    onUnclaimClick(deviceIdStr)
                }
            } else { // type == "correlative"
                binding.btnAction.text = "Hapus"
                binding.btnAction.setBackgroundColor(Color.parseColor("#757575")) // Abu-abu
                binding.btnAction.setOnClickListener {
                    onRemoveCorrelativeClick(item.userId) // Hapus berdasarkan user ID pasien
                }
            }

            // Klik item untuk lihat history
            binding.root.setOnClickListener {
                onItemClick(item.userId)
            }
        }
    }
}

class DashboardDiffCallback : DiffUtil.ItemCallback<DashboardItem>() {
    override fun areItemsTheSame(oldItem: DashboardItem, newItem: DashboardItem): Boolean {
        // Asumsi kombinasi userID dan deviceName unik
        return oldItem.userId == newItem.userId && oldItem.deviceName == newItem.deviceName
    }

    override fun areContentsTheSame(oldItem: DashboardItem, newItem: DashboardItem): Boolean {
        return oldItem == newItem
    }
}