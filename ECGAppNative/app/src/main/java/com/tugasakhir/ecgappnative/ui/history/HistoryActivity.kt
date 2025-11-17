package com.tugasakhir.ecgappnative.ui.history

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tugasakhir.ecgappnative.MainApplication
import com.tugasakhir.ecgappnative.data.local.HistoryEntity
import com.tugasakhir.ecgappnative.databinding.ActivityHistoryBinding
import com.tugasakhir.ecgappnative.utils.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private var targetUserId: Int = -1

    // Inisialisasi adapter baru
    private lateinit var historyAdapter: HistoryAdapter

    private val factory by lazy { (application as MainApplication).viewModelFactory }
    private val viewModel: HistoryViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup RecyclerView dan Adapter
        setupRecyclerView()
        // Setup Observer ViewModel
        setupObservers()

        // Ambil User ID dari Intent
        targetUserId = intent.getIntExtra("USER_ID", -1)

        // Tentukan User ID target
        if (targetUserId == -1) {
            lifecycleScope.launch {
                targetUserId = SessionManager(this@HistoryActivity).userIdFlow.first()?.toIntOrNull() ?: -1
                viewModel.loadHistory(targetUserId)
            }
        } else {
            viewModel.loadHistory(targetUserId)
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter() // Buat instance adapter
        binding.recyclerViewHistory.apply {
            adapter = historyAdapter // Pasang adapter
            layoutManager = LinearLayoutManager(this@HistoryActivity)
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            // GANTI // TODO DENGAN INI
            if (isLoading) {
                binding.progressBarHistory.visibility = View.VISIBLE
                binding.recyclerViewHistory.visibility = View.GONE
            } else {
                binding.progressBarHistory.visibility = View.GONE
                binding.recyclerViewHistory.visibility = View.VISIBLE
            }
        }

        viewModel.toastMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        viewModel.historyItems.observe(this) { items ->
            binding.tvHistoryTitle.text = "Riwayat Bacaan (User $targetUserId)"
            // Panggil updateRecyclerView
            updateRecyclerView(items)
        }
    }

    private fun updateRecyclerView(data: List<HistoryEntity>) {
        // Hapus //TODO, ganti dengan ini
        historyAdapter.submitList(data) // Kasih data ke adapter
        Log.d("HistoryActivity", "Data to display: ${data.size} items")
    }
}