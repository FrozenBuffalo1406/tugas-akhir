package com.tugasakhir.ecgappnative.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.tugasakhir.ecgappnative.MainApplication
import com.tugasakhir.ecgappnative.R
import com.tugasakhir.ecgappnative.data.model.AddCorrelativeRequest
import com.tugasakhir.ecgappnative.data.model.ClaimRequest
import com.tugasakhir.ecgappnative.data.model.DeviceQRModel
import com.tugasakhir.ecgappnative.data.model.RemoveCorrelativeRequest
import com.tugasakhir.ecgappnative.data.model.UnclaimRequest
import com.tugasakhir.ecgappnative.databinding.ActivityDashboardBinding
import com.tugasakhir.ecgappnative.ui.history.HistoryActivity
import com.tugasakhir.ecgappnative.ui.profile.ProfileActivity

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var dashboardAdapter: DashboardAdapter
    private val gson = Gson()

    private val factory by lazy { (application as MainApplication).viewModelFactory }
    private val viewModel: DashboardViewModel by viewModels { factory }

    // Launcher baru untuk QR Scanner
    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            Log.d("QR_SCAN", "Scanned content: ${result.contents}")
            handleScannedContent(result.contents)
        } else {
            Toast.makeText(this, "Scan dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupRecyclerView()
        setupListeners()
        setupObservers()

        viewModel.loadDashboard() // Muat data pertama kali
    }


    override fun onResume() {
        super.onResume()
        // Selalu refresh data saat kembali ke activity ini
        viewModel.loadDashboard()
    }

    private fun setupListeners() {
        binding.fabScan.setOnClickListener { startQrScanner() }
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadDashboard() // Panggil ViewModel untuk refresh
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }

        viewModel.dashboardItems.observe(this) { items ->
            dashboardAdapter.submitList(items)
        }

        viewModel.toastMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun startQrScanner() {
        // ... (Fungsi ini tidak berubah) ...
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan QR Code Device atau Pasien")
            setCameraId(0) // 0 untuk kamera belakang
            setBeepEnabled(true)
            setBarcodeImageEnabled(true)
            setOrientationLocked(false)
        }
        qrScannerLauncher.launch(options)
    }

    private fun setupRecyclerView() {
        dashboardAdapter = DashboardAdapter(
            onUnclaimClick = { deviceIdStr ->
                // Poin 5: Unclaim Device
                showConfirmDialog("Unclaim Device?", "Yakin ingin melepaskan device ini?") {
                    viewModel.unclaimDevice(UnclaimRequest(deviceIdStr))
                }
            },
            onRemoveCorrelativeClick = { patientId ->
                // Poin 6: Remove Collaborative
                showConfirmDialog("Hapus Kerabat?", "Yakin ingin berhenti memantau pasien ini?") {
                    viewModel.removeCorrelative(RemoveCorrelativeRequest(patient_id = patientId))
                }
            },
            onItemClick = { userId ->
                // Buka halaman History
                val intent = Intent(this, HistoryActivity::class.java)
                intent.putExtra("USER_ID", userId)
                startActivity(intent)
            }
        )
        binding.recyclerView.apply {
            adapter = dashboardAdapter
            layoutManager = LinearLayoutManager(this@DashboardActivity)
        }
    }

    // --- LOGIC SCANNER (POIN 3 & 4) ---
    private fun handleScannedContent(content: String) {
        // ... (Fungsi parsing QR tidak berubah, tapi panggil ViewModel) ...
        try {
            val deviceModel = gson.fromJson(content, DeviceQRModel::class.java)
            if (deviceModel != null && deviceModel.mac.isEmpty() && !deviceModel.id.isEmpty()) {
                Log.d("QR_SCAN", "Hasil scan adalah Device QR.")
                showConfirmDialog("Klaim Device?", "Device ID: ${deviceModel.id}\nMAC: ${deviceModel.mac}") {
                    viewModel.claimDevice(ClaimRequest(deviceModel.mac, deviceModel.id))
                }
                return
            }
        } catch (_: JsonSyntaxException) {
            Log.d("QR_SCAN", "Bukan JSON Device, mencoba parsing sebagai User ID.")
        }

        try {
            val patientId = content.toInt()
            Log.d("QR_SCAN", "Hasil scan adalah User ID: $patientId")
            showConfirmDialog("Tambah Kerabat?", "Anda akan memantau User ID: $patientId") {
                viewModel.addCorrelative(AddCorrelativeRequest(content))
            }
            return
        } catch (_: NumberFormatException) {
            Log.d("QR_SCAN", "Bukan User ID.")
        }

        Toast.makeText(this, "QR Code tidak dikenali", Toast.LENGTH_LONG).show()
    }

    private fun showConfirmDialog(title: String, message: String, onConfirm: () -> Unit) {
        // ... (Fungsi ini tidak berubah) ...
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Ya") { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // --- Menu (untuk ke Profile) ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // ... (Fungsi ini tidak berubah) ...
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // ... (Fungsi ini tidak berubah) ...
        return when (item.itemId) {
            R.id.action_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}