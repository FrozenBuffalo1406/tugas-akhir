package com.tugasakhir.ecgappnative.ui.ble

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPDevice 
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.provisioning.listeners.BleScanListener
import com.espressif.provisioning.listeners.ProvisionListener
import com.tugasakhir.ecgappnative.databinding.ActivityBleProvisioningBinding
import com.tugasakhir.ecgappnative.ui.BaseActivity

@SuppressLint("MissingPermission")
class BleProvisioningActivity : BaseActivity() {

    private lateinit var binding: ActivityBleProvisioningBinding
    private lateinit var bleDeviceAdapter: BleDeviceAdapter // Adapter lo yang terpisah
    private val scanResults = mutableMapOf<String, BluetoothDevice>()
    private lateinit var provisionManager: ESPProvisionManager
    private var selectedDevice: BluetoothDevice? = null
    private val deviceUuids = mutableMapOf<String, String>()

    // Prefix nama device yang mau dicari (Sesuaikan sama firmware ESP32 lo)
    private val devicePrefix = "ECG_SETUP_"

    // --- PERMISSION HANDLER ---
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach { if (!it.value) allGranted = false }

            if (allGranted) {
                if (isBluetoothEnabled()) {
                    startBleScan()
                } else {
                    showToast("Tolong nyalakan Bluetooth")
                }
            } else {
                showToast("Izin Lokasi dan Bluetooth dibutuhkan")
            }
        }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBleProvisioningBinding.inflate(layoutInflater)
        setContentView(binding.root)

        provisionManager = ESPProvisionManager.getInstance(applicationContext)

        setupRecyclerView()
        setupListeners()

        binding.etPop.error = "Harus sama dengan PoP di firmware ESP32"
    }

    private fun setupRecyclerView() {
        // Inisialisasi adapter lo
        bleDeviceAdapter = BleDeviceAdapter { device ->
            stopBleScan()
            selectedDevice = device
            binding.tvDeviceStatus.text = "Perangkat Terpilih: ${device.name ?: device.address}"
            binding.btnSendCredentials.isEnabled = true
            binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.holo_green_dark))
        }

        binding.rvBleDevices.layoutManager = LinearLayoutManager(this)
        binding.rvBleDevices.adapter = bleDeviceAdapter
    }

    private fun setupListeners() {
        binding.btnScanBle.setOnClickListener { checkPermissionsAndScan() }
        binding.btnSendCredentials.setOnClickListener { sendCredentials() }
    }

    private fun checkPermissionsAndScan() {
        if (!isBluetoothEnabled()) {
            showToast("Tolong nyalakan Bluetooth Anda")
            return
        }

        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            startBleScan()
        }
    }

    private fun startBleScan() {
        Log.d("BLE_Scan", "Mulai scan...")
        scanResults.clear()
        bleDeviceAdapter.updateDevices(emptyList())

        setLoadingUi(true, "Mencari perangkat...")

        provisionManager.searchBleEspDevices(devicePrefix, object : BleScanListener {

            override fun scanStartFailed() {
                runOnUiThread {
                    setLoadingUi(false, "Gagal memulai scan")
                    showToast("Scan gagal dimulai")
                }
            }

            override fun onPeripheralFound(device: BluetoothDevice?, scanResult: ScanResult?) {
                if (device != null && device.name != null) {
                    val uuids = scanResult?.scanRecord?.serviceUuids
                    if (!uuids.isNullOrEmpty()) {
                        val primaryUuid = uuids[0].toString()
                        deviceUuids[device.address] = primaryUuid // Simpen UUID-nya!
                        Log.d("BLE_Scan", "UUID saved for ${device.address}: $primaryUuid")
                    }
                    if (!scanResults.containsKey(device.address)) {
                        Log.d("BLE_Scan", "Found: ${device.name}")
                        scanResults[device.address] = device

                        runOnUiThread {
                            bleDeviceAdapter.updateDevices(scanResults.values.toList())
                        }
                    }
                }
            }

            override fun scanCompleted() {
                runOnUiThread {
                    setLoadingUi(false, "Scan selesai. Pilih perangkat.")
                }
            }

            override fun onFailure(e: Exception?) {
                runOnUiThread {
                    setLoadingUi(false, "Error scan: ${e?.message}")
                }
            }
        })
    }

    private fun stopBleScan() {
        provisionManager.stopBleScan()
        setLoadingUi(false, "Scan dihentikan.")
    }

    // --- INI DIA YANG KITA BENERIN ---
    private fun sendCredentials() {

        val device = selectedDevice
        val pop = binding.etPop.text.toString()
        val ssid = binding.etSsid.text.toString()
        val password = binding.etPassword.text.toString()
        val primaryUuid = device?.let { deviceUuids[it.address] }
        if (primaryUuid.isNullOrEmpty()) {
            showToast("Gagal mendapatkan UUID Device. Coba scan ulang.")
            return
        }

        if (pop.isEmpty()) { showToast("PoP wajib diisi"); return }
        if (ssid.isEmpty()) { showToast("SSID wajib diisi"); return }

        setLoadingUi(true, "Menghubungkan & Mengirim...")

        // 1. Minta manager bikinin object ESPDevice
        // Kita pake SECURITY_1 karena ada PoP (Proof of Possession)
        val espDevice: ESPDevice = provisionManager.createESPDevice(
            ESPConstants.TransportType.TRANSPORT_BLE,
            ESPConstants.SecurityType.SECURITY_1
        )
        try {
            // 4. PANGGIL PAKE 2 PARAMETER (DEVICE + UUID)
            espDevice.connectBLEDevice(device, primaryUuid) // <--- INI YANG DIBENERIN
        } catch (e: Exception) {
            Log.e("Prov", "Gagal connect BLE", e)
            setLoadingUi(false, "Gagal Connect BLE")
            return
        }

        // 3. Set PoP-nya
        espDevice.proofOfPossession = pop

        // 4. Panggil provision di objek espDevice (BUKAN di manager)
        espDevice.provision(ssid, password, object : ProvisionListener {

            override fun createSessionFailed(e: Exception?) {
                runOnUiThread {
                    Log.e("Prov", "Session Failed", e)
                    setLoadingUi(false, "Gagal sesi. Cek PoP!")
                    showToast("Gagal Connect. PoP Salah?")
                }
            }

            override fun wifiConfigSent() {
                runOnUiThread {
                    binding.tvProvisionStatus.text = "Status: Config terkirim. Menunggu apply..."
                }
            }

            override fun wifiConfigFailed(e: Exception?) {
                runOnUiThread {
                    setLoadingUi(false, "Gagal mengirim config WiFi")
                }
            }

            override fun wifiConfigApplied() {
                runOnUiThread {
                    binding.tvProvisionStatus.text = "Status: ESP sedang mencoba connect WiFi..."
                }
            }

            override fun wifiConfigApplyFailed(e: Exception?) {
                runOnUiThread {
                    setLoadingUi(false, "ESP gagal apply config")
                }
            }

            override fun provisioningFailedFromDevice(reason: ESPConstants.ProvisionFailureReason?) {
                runOnUiThread {
                    val msg = when(reason) {
                        ESPConstants.ProvisionFailureReason.AUTH_FAILED -> "Password WiFi Salah!"
                        ESPConstants.ProvisionFailureReason.NETWORK_NOT_FOUND -> "SSID Tidak Ditemukan!"
                        else -> "Gagal: ${reason?.name}"
                    }
                    setLoadingUi(false, msg)
                    showToast(msg)
                }
            }

            override fun deviceProvisioningSuccess() {
                runOnUiThread {
                    setLoadingUi(false, "SUKSES! Perangkat Online.")
                    showToast("Provisioning Berhasil!")
                }
            }

            override fun onProvisioningFailed(e: Exception?) {
                runOnUiThread {
                    setLoadingUi(false, "Gagal: ${e?.message}")
                }
            }
        })
    }

    private fun setLoadingUi(isLoading: Boolean, message: String) {
        binding.bleLoading.visibility = if (isLoading) View.VISIBLE else View.INVISIBLE
        binding.tvProvisionStatus.text = "Status: $message"
        binding.btnScanBle.isEnabled = !isLoading
        binding.btnSendCredentials.isEnabled = !isLoading && selectedDevice != null
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBleScan()
        if (::provisionManager.isInitialized) {
             provisionManager.stopBleScan()
        }
    }
}