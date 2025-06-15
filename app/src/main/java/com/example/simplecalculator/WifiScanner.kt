package com.example.wifiscanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.example.simplecalculator.WifiScanInfo
import kotlinx.coroutines.*
import kotlin.coroutines.resume


// Class responsible for scanning Wi-Fi networks
class WifiScanner(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val wifiScanResults = MutableLiveData<List<ScanResult>>()
    private var isReceiverRegistered = false

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            @Suppress("DEPRECATION")
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) {
                    scanSuccess()
                } else {
                    scanFailure()
                }
            }
        }
    }

    // Check if the scan start successfully
    fun startScan(): Boolean {
        if (!hasRequiredPermissionsInternal() || !isWifiEnabled()) {
            return false
        }

        if (!isReceiverRegistered) {
            val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(wifiScanReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(wifiScanReceiver, intentFilter)
                }
                isReceiverRegistered = true
            } catch (e: Exception) {
                return false
            }
        }

        val scanInitiated = wifiManager.startScan()

        return scanInitiated
    }

    // If scan finish successfully post the results
    @SuppressLint("MissingPermission")
    private fun scanSuccess() {
        if (!hasRequiredPermissionsInternal()) {
            wifiScanResults.postValue(emptyList())
            return
        }
        try {
            val results = wifiManager.scanResults
            wifiScanResults.postValue(results ?: emptyList())
        } catch (e: Exception) {
            wifiScanResults.postValue(emptyList())
        }
    }

    // If scan fails post an empty list
    private fun scanFailure() {
        wifiScanResults.postValue(emptyList())
    }

    // Unregister receiver
    fun unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(wifiScanReceiver)
                isReceiverRegistered = false
            } catch (e: Exception) {
                isReceiverRegistered = false
            }
        }
    }

    // Check if Wi-Fi necessary permissions are granted internally
    private fun hasRequiredPermissionsInternal(): Boolean {

        val permissionsToGrant = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToGrant.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val allGranted = permissionsToGrant.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) {
            permissionsToGrant.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        }
        return allGranted
    }

    // Check if Wi-Fi adapter is enabled
    fun isWifiEnabled(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            wifiManager.isWifiEnabled
        } catch (e: Exception) {
            false
        }
    }

    // Launch the Wi-Fi scan and return the results
    @RequiresApi(Build.VERSION_CODES.P)
    suspend fun launchWifiScan(durationMs: Long): List<WifiScanInfo> {
        return withContext(Dispatchers.IO) {
            if (!hasRequiredPermissionsInternal()) {
                return@withContext emptyList<WifiScanInfo>()
            }
            if (!isWifiEnabled()) {
                return@withContext emptyList<WifiScanInfo>()
            }

            val wifiList = mutableListOf<WifiScanInfo>()
            var scanAttempted = false

            try {
                scanAttempted = startScan()

                if (!scanAttempted) {
                    return@withContext emptyList()
                }

                val results = withTimeoutOrNull(durationMs) {
                    suspendCancellableCoroutine<List<ScanResult>?> { continuation ->
                        var observer: Observer<List<ScanResult>>? = null
                        observer = Observer { scanResults ->
                            if (continuation.isActive) {
                                GlobalScope.launch(Dispatchers.Main) {
                                    observer?.let { wifiScanResults.removeObserver(it) }
                                }
                                continuation.resume(scanResults)
                            } else {
                                GlobalScope.launch(Dispatchers.Main) {
                                    observer?.let { wifiScanResults.removeObserver(it) }
                                }
                            }
                        }
                        GlobalScope.launch(Dispatchers.Main) {
                            wifiScanResults.observeForever(observer)
                        }
                        continuation.invokeOnCancellation {
                            GlobalScope.launch(Dispatchers.Main) {
                                observer?.let { wifiScanResults.removeObserver(it) }
                            }
                        }
                    }
                }

                results?.let { rawResults ->
                    wifiList.addAll(rawResults.mapNotNull { wifi ->
                        wifi.BSSID?.let { bssid ->
                            val ssid = if (wifi.SSID.isNullOrEmpty()) "Hidden SSID" else wifi.SSID
                            val rssi = wifi.level
                            WifiScanInfo(ssid, bssid, rssi)
                        }
                    })
                }

            } catch (e: Exception) {
                wifiList.clear()
            }

            return@withContext wifiList
        }
    }
}

