package com.tugasakhir.ecgappnative.ui.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.tugasakhir.ecgappnative.MainApplication
import com.tugasakhir.ecgappnative.data.model.ClaimRequest
import com.tugasakhir.ecgappnative.databinding.ActivityBlePairingBinding
import com.tugasakhir.ecgappnative.ui.BaseActivity
import com.tugasakhir.ecgappnative.ui.main.DashboardViewModel
import java.util.*

@SuppressLint("MissingPermission")
class BlePairingActivity : BaseActivity() {

    private lateinit var binding: ActivityBlePairingBinding
    private lateinit var deviceAdapter: BleDeviceAdapter
    private var bluetoothGatt: BluetoothGatt? = null

    private val factory by lazy { (application as MainApplication).viewModelFactory }
    private val viewModel: DashboardViewModel by viewModels { factory }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val foundDevices = mutableListOf<BluetoothDevice>()
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    // =====================================================================
    // PAKE UUID STANDAR BLUETOOTH SIG (LEBIH DINAMIS)
    // =====================================================================
    // 0x180A = Device Information Service
    private val SERVICE_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")

    // 0x2A23 = System ID (Kita pake buat nyimpen MAC Address / Unique ID)
    private val CHAR_MAC_UUID = UUID.fromString("00002a23-0000-1000-8000-00805f9b34fb")

    // 0x2A24 = Model Number String (Kita pake buat nyimpen Device ID kita)
    private val CHAR_ID_UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    // =====================================================================

    private var tempMacAddress: String? = null
    private var tempDeviceId: String? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) startScan()
            else showToast("Izin Bluetooth & Lokasi diperlukan")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlePairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        binding.btnScanBle.setOnClickListener { checkPermissions() }

        viewModel.toastMessage.observe(this) { msg ->
            showToast(msg)
            if (msg.contains("berhasil", true) || msg.contains("diklaim", true)) finish()
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun setupRecyclerView() {
        deviceAdapter = BleDeviceAdapter { device ->
            stopScan()
            connectToDevice(device)
        }
        binding.rvBleDevices.apply {
            layoutManager = LinearLayoutManager(this@BlePairingActivity)
            adapter = deviceAdapter
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissions.all { ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startScan()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startScan() {
        if (isScanning) return
        foundDevices.clear()
        deviceAdapter.updateDevices(emptyList())
        isScanning = true
        binding.btnScanBle.isEnabled = false
        binding.tvStatus.text = "Scanning..."

        handler.postDelayed({ stopScan() }, 10000)

        bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
    }

    private fun stopScan() {
        if (!isScanning) return
        isScanning = false
        binding.btnScanBle.isEnabled = true
        binding.tvStatus.text = "Scan Selesai"
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                // Filter: Cuma tampilin yang punya nama (biar gak sampah)
                // Kalo mau lebih ketat, bisa cek scanRecord.serviceUuids contains SERVICE_UUID
                if (device.name != null && !foundDevices.any { it.address == device.address }) {
                    foundDevices.add(device)
                    runOnUiThread { deviceAdapter.updateDevices(foundDevices) }
                }
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        runOnUiThread { binding.tvStatus.text = "Menghubungkan ke ${device.name}..." }
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread { binding.tvStatus.text = "Terhubung. Mencari Services..." }
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    binding.tvStatus.text = "Terputus"
                    showToast("Koneksi terputus")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(SERVICE_UUID)
                if (service != null) {
                    Log.d("BLE_PAIR", "Service Info Found!")
                    // 1. BACA MAC (System ID)
                    val macChar = service.getCharacteristic(CHAR_MAC_UUID)
                    if (macChar != null) {
                        gatt.readCharacteristic(macChar)
                    } else {
                        showError("Karakteristik MAC tidak ditemukan")
                    }
                } else {
                    showError("Ini bukan Device ECG yang valid (Service Info 0x180A missing)")
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                // Baca data sebagai String
                @Suppress("DEPRECATION")
                val value = characteristic.getStringValue(0)

                if (characteristic.uuid == CHAR_MAC_UUID) {
                    tempMacAddress = value
                    Log.d("BLE_PAIR", "MAC Read: $value")

                    // 2. LANJUT BACA DEVICE ID (Model Number)
                    val service = gatt?.getService(SERVICE_UUID)
                    val idChar = service?.getCharacteristic(CHAR_ID_UUID)
                    if (idChar != null) {
                        gatt.readCharacteristic(idChar)
                    }
                } else if (characteristic.uuid == CHAR_ID_UUID) {
                    tempDeviceId = value
                    Log.d("BLE_PAIR", "ID Read: $value")

                    // 3. SEMUA DATA DAPET -> KONFIRMASI USER
                    runOnUiThread {
                        showConfirmDialog(tempMacAddress, tempDeviceId)
                        gatt?.disconnect()
                    }
                }
            }
        }
    }

    private fun showConfirmDialog(mac: String?, id: String?) {
        AlertDialog.Builder(this)
            .setTitle("Device Ditemukan")
            .setMessage("Apakah Anda ingin mengklaim device ini?\n\nMAC: $mac\nID: $id")
            .setPositiveButton("Ya, Klaim") { _, _ ->
                if (mac != null && id != null) {
                    viewModel.claimDevice(ClaimRequest(mac, id))
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showError(msg: String) {
        runOnUiThread {
            binding.tvStatus.text = "Error: $msg"
            showToast(msg)
            // Disconnect kalo salah device
            bluetoothGatt?.disconnect()
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt?.close()
        }
    }
}