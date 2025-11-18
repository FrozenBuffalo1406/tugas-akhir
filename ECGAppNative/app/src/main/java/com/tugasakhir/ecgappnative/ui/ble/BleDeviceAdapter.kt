package com.tugasakhir.ecgappnative.ui.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tugasakhir.ecgappnative.databinding.ItemBleDeviceBinding

class BleDeviceAdapter(private val onClick: (BluetoothDevice) -> Unit) :
    RecyclerView.Adapter<BleDeviceAdapter.DeviceViewHolder>() {

    private var devices: List<BluetoothDevice> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun updateDevices(newDevices: List<BluetoothDevice>) {
        this.devices = newDevices
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemBleDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount() = devices.size

    inner class DeviceViewHolder(private val binding: ItemBleDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: BluetoothDevice) {
            binding.tvDeviceName.text = device.name ?: "Unknown Device"
            binding.tvDeviceAddress.text = device.address
            binding.itemRoot.setOnClickListener {
                onClick(device)
            }
        }
    }
}