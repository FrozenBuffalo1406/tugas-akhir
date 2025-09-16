package com.tugasakhir.healtyheart.service

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.util.UUID
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Konstanta untuk UUID service dan characteristic dari ESP32
private val ECG_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB") // Contoh UUID, ganti dengan milikmu
private val ECG_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB") // Contoh UUID, ganti dengan milikmu

class BluetoothLeService : Service() {


    private val binder = LocalBinder()
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothDeviceAddress: String? = null
    private var connectionState = STATE_DISCONNECTED

    // Status koneksi, untuk broadcast ke UI
    companion object {
        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTED = 2

        const val ACTION_GATT_CONNECTED = "com.example.ble.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.ble.ACTION_GATT_DISCONNECTED"
        const val ACTION_DATA_AVAILABLE = "com.example.ble.ACTION_DATA_AVAILABLE"
        const val EXTRA_DATA = "com.example.ble.EXTRA_DATA"
    }

    // Callback untuk menerima event dari BluetoothGatt
    private val gattCallback = object : BluetoothGattCallback() {
        // Dipanggil saat status koneksi berubah
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val intentAction: String
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    intentAction = ACTION_GATT_CONNECTED
                    connectionState = STATE_CONNECTED
                    broadcastUpdate(intentAction)
                    Log.i("BluetoothLeService", "Connected to GATT server.")
                    Log.i("BluetoothLeService", "Attempting to start service discovery: " + bluetoothGatt?.discoverServices())
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    intentAction = ACTION_GATT_DISCONNECTED
                    connectionState = STATE_DISCONNECTED
                    Log.i("BluetoothLeService", "Disconnected from GATT server.")
                    broadcastUpdate(intentAction)
                }
            }
        }

        // Dipanggil saat services ditemukan
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i("BluetoothLeService", "Services discovered.")
                    // Cari service dan characteristic ECG
                    val service = gatt.getService(ECG_SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(ECG_CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        Log.i("BluetoothLeService", "Found ECG characteristic. Enabling notifications.")
                        gatt.setCharacteristicNotification(characteristic, true)
                        // Kamu bisa tambahkan setel descriptor di sini jika diperlukan
                    }
                }
                else -> {
                    Log.w("BluetoothLeService", "onServicesDiscovered received: $status")
                }
            }
        }

        // Dipanggil saat data dari characteristic berubah (notifikasi dari ESP32)
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.i("BluetoothLeService", "Characteristic changed. Broadcasting data.")
            // Broadcast data mentah ke UI
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
        }
    }

    // Mengirim broadcast ke komponen lain di aplikasi (seperti ViewModel)
    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    // Mengirim broadcast dengan data
    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic) {
        val intent = Intent(action)

        when (characteristic.uuid) {
            ECG_CHARACTERISTIC_UUID -> {
                // Parsing data dari characteristic
                val data = characteristic.value
                val floatValue = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getFloat() // Contoh parsing
                Log.d("BluetoothLeService", "Received data: $floatValue")
                intent.putExtra(EXTRA_DATA, floatValue)
            }
        }
        sendBroadcast(intent)
    }

    // Kelas Binder untuk komunikasi dengan Activity
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothLeService = this@BluetoothLeService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        close()
        return super.onUnbind(intent)
    }

    // Fungsi inisialisasi Bluetooth
    fun initialize(): Boolean {
        if (bluetoothManager == null) {
            bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (bluetoothManager == null) {
                Log.e("BluetoothLeService", "Unable to initialize BluetoothManager.")
                return false
            }
        }
        bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null) {
            Log.e("BluetoothLeService", "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }

    // Fungsi untuk koneksi ke perangkat BLE
    fun connect(address: String): Boolean {
        if (bluetoothAdapter == null || address.isBlank()) {
            Log.w("BluetoothLeService", "BluetoothAdapter not initialized or unspecified address.")
            return false
        }

        // Cek jika sudah pernah terkoneksi ke alamat ini
        if (address == bluetoothDeviceAddress && bluetoothGatt != null) {
            Log.d("BluetoothLeService", "Trying to use an existing mBluetoothGatt for connection.")
            return bluetoothGatt?.connect() ?: false
        }

        // Dapatkan perangkat remote
        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            Log.w("BluetoothLeService", "Device not found. Unable to connect.")
            return false
        }

        // Koneksi ke GATT server
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
        bluetoothDeviceAddress = address
        connectionState = STATE_DISCONNECTED
        return true
    }

    // Fungsi untuk menutup koneksi
    private fun close() {
        bluetoothGatt?.let { gatt ->
            gatt.close()
            bluetoothGatt = null
        }
    }
}

