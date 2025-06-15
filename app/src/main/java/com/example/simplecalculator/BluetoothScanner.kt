package com.example.bluetoothscanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import com.example.simplecalculator.DeviceData

// Class responsible for scanning Bluetooth devices
@SuppressLint("MissingPermission")
class BluetoothScanner(private val context: Context) {

    private val bluetoothManager = context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val _discoveredBluetoothDevicesLiveData = MutableLiveData<List<DeviceData>>()
    val discoveredBluetoothDevicesLiveData: LiveData<List<DeviceData>> get() = _discoveredBluetoothDevicesLiveData
    private val discoveredBluetoothDevicesMap = ConcurrentHashMap<String, DeviceData>()
    private var isBluetoothScanning = false

    private val handler = Handler(Looper.getMainLooper())

    // Manage the Bluetooth scan results
    private val bluetoothScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            if (!hasRequiredPermissionsInternal()) return

            result?.device?.let { device ->
                if (device.address != null) {
                    val deviceName = getDeviceName(device)
                    val rssi = result.rssi
                    discoveredBluetoothDevicesMap[device.address] = DeviceData(deviceName, device.address, rssi)
                    _discoveredBluetoothDevicesLiveData.postValue(discoveredBluetoothDevicesMap.values.toList())
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            if (!hasRequiredPermissionsInternal()) return

            results?.forEach { result ->
                result.device?.let { device ->
                    if (device.address != null) {
                        val deviceName = getDeviceName(device)
                        val rssi = result.rssi
                        discoveredBluetoothDevicesMap[device.address] = DeviceData(deviceName, device.address, rssi)
                    }
                }
            }
            _discoveredBluetoothDevicesLiveData.postValue(discoveredBluetoothDevicesMap.values.toList())
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            isBluetoothScanning = false
        }
    }

    // Gets device name, handling permissions
    private fun getDeviceName(device: BluetoothDevice): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return "Name requires connect permission"
            }
        }
        return try {
            device.name ?: "Unknown Device"
        } catch (e: SecurityException) {
            "Name requires connect permission"
        }
    }

    // Checks if Bluetooth adapter is enabled
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    // Launch the Bluetooth scan and returns the results
    @SuppressLint("MissingPermission")
    suspend fun launchBluetoothScan(durationMs: Long): List<DeviceData> = withContext(Dispatchers.IO) {
        if (!hasRequiredPermissionsInternal()) {
            return@withContext emptyList<DeviceData>()
        }
        if (!isBluetoothEnabled()) {
            return@withContext emptyList<DeviceData>()
        }
        if (isBluetoothScanning) {
            stopBluetoothScan()
            delay(100)
        }

        discoveredBluetoothDevicesMap.clear()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        var scanStartSuccess = false
        try {
            bluetoothLeScanner?.startScan(null, scanSettings, bluetoothScanCallback)
            isBluetoothScanning = true
            scanStartSuccess = true

            delay(durationMs)

        } catch (e: Exception) {
            isBluetoothScanning = false
            scanStartSuccess = false
        } finally {
            if (scanStartSuccess) {
                try {
                    bluetoothLeScanner?.stopScan(bluetoothScanCallback)
                } finally {
                    isBluetoothScanning = false
                }
            }
        }

        return@withContext discoveredBluetoothDevicesMap.values.toList()
    }

    // Stop Bluetooth scan if some conditions are not passed
    fun stopBluetoothScan() {
        if (!hasRequiredPermissionsInternal(checkConnectPermission = false) || !isBluetoothScanning) {
            return
        }

        try {
            bluetoothLeScanner?.stopScan(bluetoothScanCallback)
            isBluetoothScanning = false
        } catch (e: Exception) {
            isBluetoothScanning = false
        }
    }

    // Check if Bluetooth necessary permissions are granted internally
    private fun hasRequiredPermissionsInternal(checkConnectPermission: Boolean = true): Boolean {
        val basePermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            basePermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            if (checkConnectPermission) {
                basePermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            basePermissions.add(Manifest.permission.BLUETOOTH)
        }

        val allGranted = basePermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) {
            basePermissions.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
        }

        return allGranted
    }

    // Lifecycle method
    fun cleanup() {
        stopBluetoothScan()
        handler.removeCallbacksAndMessages(null)
    }
}