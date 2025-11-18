package com.tugasakhir.ecgappnative.ui.history

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
// import androidx.appcompat.app.AppCompatActivity // <-- HAPUS INI
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tugasakhir.ecgappnative.MainApplication
import com.tugasakhir.ecgappnative.data.local.HistoryEntity
import com.tugasakhir.ecgappnative.databinding.ActivityHistoryBinding
import com.tugasakhir.ecgappnative.ui.BaseActivity // <-- IMPORT INI
import com.tugasakhir.ecgappnative.data.utils.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.content.Intent
import com.tugasakhir.ecgappnative.ui.history.detail.HistoryDetailActivity

// GANTI JADI BaseActivity
class HistoryActivity : BaseActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private var targetUserId: Int = -1

    private lateinit var historyAdapter: HistoryAdapter

    private val factory by lazy { (application as MainApplication).viewModelFactory }
    private val viewModel: HistoryViewModel by viewModels { factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupListeners() // <-- Tambahin ini buat tombol back

        targetUserId = intent.getIntExtra("USER_ID", -1)

        // ... (Logika loading history sama persis) ...
        if (targetUserId == -1) {
            lifecycleScope.launch {
                targetUserId = SessionManager(this@HistoryActivity).userIdFlow.first()?.toIntOrNull() ?: -1
                viewModel.loadHistory(targetUserId)
            }
        } else {
            viewModel.loadHistory(targetUserId)
        }
    }

    // Tambahin ini biar tombol back di header jalan (kalo ada)
    private fun setupListeners() {
        // binding.ivBack.setOnClickListener { finish() } // Uncomment kalo ada tombol back
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter { historyItem ->
            onHistoryItemClicked(historyItem)
        }
        binding.recyclerViewHistory.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(this@HistoryActivity)
        }
    }

    // Fungsi klik item
    private fun onHistoryItemClicked(item: HistoryEntity) { // Pake HistoryEntity dari DB
        val intent = Intent(this, HistoryDetailActivity::class.java).apply {
            putExtra(HistoryDetailActivity.EXTRA_READING_ID, item.id)
            putExtra(HistoryDetailActivity.EXTRA_PREDICTION, item.classification)
            putExtra(HistoryDetailActivity.EXTRA_TIMESTAMP, item.timestamp)
        }
        startActivity(intent)
    }

    private fun setupObservers() {
        // ... (Sama persis kayak sebelumnya) ...
        viewModel.isLoading.observe(this) { isLoading ->
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
            binding.tvHistoryTitle.text = "Riwayat Bacaan"
            historyAdapter.submitList(items)
        }
    }
}