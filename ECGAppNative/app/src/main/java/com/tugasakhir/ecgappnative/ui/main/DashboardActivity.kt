package com.tugasakhir.ecgappnative.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.tugasakhir.ecgappnative.MainApplication
import com.tugasakhir.ecgappnative.R
import com.tugasakhir.ecgappnative.data.model.*
import com.tugasakhir.ecgappnative.databinding.ActivityDashboardBinding
import com.tugasakhir.ecgappnative.ui.BaseActivity
import com.tugasakhir.ecgappnative.ui.ble.BleProvisioningActivity // Pastiin package BLE lo bener
import com.tugasakhir.ecgappnative.ui.history.HistoryActivity
import com.tugasakhir.ecgappnative.ui.profile.ProfileActivity

class DashboardActivity : BaseActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var dashboardAdapter: DashboardAdapter

    private var isFabMenuOpen = false
    private val gson = Gson()

    private val factory by lazy { (application as MainApplication).viewModelFactory }
    private val viewModel: DashboardViewModel by viewModels { factory }

    // --- 1. LAUNCHER SCANNER (Cukup Satu Aja) ---
    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            handleScannedContent(result.contents)
        } else {
            showToast("Scan dibatalkan")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // setSupportActionBar(binding.toolbar) // <-- Hapus karena NoActionBar

        setupRecyclerView()
        setupListeners()
        setupObservers()

        viewModel.loadDashboard()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadDashboard()
    }

    private fun setupRecyclerView() {
        dashboardAdapter = DashboardAdapter(
            onUnclaimClick = { deviceIdStr ->
                showConfirmDialog("Unclaim Device?", "Yakin ingin melepaskan device ini?") {
                    viewModel.unclaimDevice(UnclaimRequest(deviceIdStr))
                }
            },
            onRemoveCorrelativeClick = { patientId ->
                showConfirmDialog("Hapus Kerabat?", "Yakin ingin berhenti memantau pasien ini?") {
                    viewModel.removeCorrelative(RemoveCorrelativeRequest(patientId))
                }
            },
            onItemClick = { userId ->
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

    private fun setupListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadDashboard()
        }

        // Pindah ke Profile
        binding.ivProfileIcon.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // --- LOGIKA FAB MENU ---

        // 1. Tombol Utama (+) -> Buka/Tutup Menu
        binding.fabScan.setOnClickListener {
            if (isFabMenuOpen) closeFabMenu() else openFabMenu()
        }

        // 2. Tombol QR -> Scan QR
        binding.fabQr.setOnClickListener {
            closeFabMenu()
            startQrScanner()
        }

        // 3. Tombol BLE -> Provisioning
        binding.fabBle.setOnClickListener {
            closeFabMenu()
            // Sesuaikan package import Activity ini
            try {
                startActivity(Intent(this, BleProvisioningActivity::class.java))
            } catch (e: Exception) {
                // Jaga-jaga kalo activity belum ke-register di Manifest atau salah import
                showToast("Fitur BLE belum siap: ${e.message}")
            }
        }
    }

    // --- ANIMASI FAB ---
    private fun openFabMenu() {
        isFabMenuOpen = true
        binding.fabScan.setImageResource(android.R.drawable.ic_menu_close_clear_cancel) // Ikon X

        // Munculin QR
        binding.fabQr.visibility = View.VISIBLE
        binding.labelFabQr.visibility = View.VISIBLE
        binding.fabQr.isClickable = true
        binding.fabQr.animate().translationY(-resources.getDimension(R.dimen.fab_margin_1)).alpha(1f).setDuration(300).start()
        binding.labelFabQr.animate().translationY(-resources.getDimension(R.dimen.fab_margin_1)).alpha(1f).setDuration(300).start()

        // Munculin BLE
        binding.fabBle.visibility = View.VISIBLE
        binding.labelFabBle.visibility = View.VISIBLE
        binding.fabBle.isClickable = true
        binding.fabBle.animate().translationY(-resources.getDimension(R.dimen.fab_margin_2)).alpha(1f).setDuration(300).start()
        binding.labelFabBle.animate().translationY(-resources.getDimension(R.dimen.fab_margin_2)).alpha(1f).setDuration(300).start()
    }

    private fun closeFabMenu() {
        isFabMenuOpen = false
        binding.fabScan.setImageResource(android.R.drawable.ic_input_add) // Ikon +

        // Sembunyiin QR
        binding.fabQr.animate().translationY(0f).alpha(0f).setDuration(300).withEndAction {
            binding.fabQr.visibility = View.GONE
            binding.fabQr.isClickable = false
        }.start()
        binding.labelFabQr.animate().translationY(0f).alpha(0f).setDuration(300).withEndAction {
            binding.labelFabQr.visibility = View.GONE
        }.start()

        // Sembunyiin BLE
        binding.fabBle.animate().translationY(0f).alpha(0f).setDuration(300).withEndAction {
            binding.fabBle.visibility = View.GONE
            binding.fabBle.isClickable = false
        }.start()
        binding.labelFabBle.animate().translationY(0f).alpha(0f).setDuration(300).withEndAction {
            binding.labelFabBle.visibility = View.GONE
        }.start()
    }

    // --- LOGIKA SCANNER ---
    private fun startQrScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan QR Code Device atau Pasien")
            setCameraId(0)
            setBeepEnabled(true)
            setOrientationLocked(false)
        }
        qrScannerLauncher.launch(options)
    }

    private fun handleScannedContent(content: String) {
        Log.d("QR_SCAN", "Scanned: $content")

        // 1. Coba Parsing sebagai DEVICE (JSON)
        try {
            val deviceQr = gson.fromJson(content, DeviceQRModel::class.java)
            // Validasi isi JSON (harus punya mac dan id)
            if (deviceQr != null && !deviceQr.mac.isNullOrEmpty() && !deviceQr.id.isNullOrEmpty()) {
                showClaimDeviceDialog(deviceQr)
                return
            }
        } catch (e: JsonSyntaxException) {
            // Lanjut cek tipe lain
        }

        // 2. Coba Parsing sebagai KORELATIF (Angka User ID)
        try {
            // Tes konversi ke Int biar yakin itu ID user
            content.toInt()
            showAddCorrelativeDialog(content)
            return
        } catch (e: NumberFormatException) {
            // Bukan angka
        }

        // 3. Gagal semua
        showToast("QR Code tidak dikenali / Format salah")
    }

    // --- DIALOG HELPERS ---
    private fun showClaimDeviceDialog(device: DeviceQRModel) {
        AlertDialog.Builder(this)
            .setTitle("Klaim Perangkat?")
            .setMessage("ID: ${device.id}\nMAC: ${device.mac}")
            .setPositiveButton("Klaim") { dialog, _ ->
                viewModel.claimDevice(ClaimRequest(device.mac, device.id))
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showAddCorrelativeDialog(code: String) {
        AlertDialog.Builder(this)
            .setTitle("Tambah Kerabat?")
            .setMessage("Anda akan memantau User ID: $code")
            .setPositiveButton("Tambah") { dialog, _ ->
                viewModel.addCorrelative(AddCorrelativeRequest(code))
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showConfirmDialog(title: String, message: String, onConfirm: () -> Unit) {
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

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }
        viewModel.dashboardItems.observe(this) { items ->
            dashboardAdapter.submitList(items)
        }
        viewModel.toastMessage.observe(this) { message ->
            showToast(message)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}