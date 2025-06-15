package com.example.simplecalculator

import TrackingStorageManager
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.system.Os
import android.text.Html
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.bluetoothscanner.BluetoothScanner
import com.example.wifiscanner.WifiScanner
import kotlinx.coroutines.*
import java.time.ZoneId
import androidx.core.content.FileProvider
import java.io.File

// Main class which implements the app functionality
class MainActivity : AppCompatActivity(), View.OnClickListener {

    lateinit var addButton: Button
    lateinit var subButton: Button
    lateinit var multButton: Button
    lateinit var divButton: Button
    lateinit var valueA: EditText
    lateinit var valueB: EditText
    lateinit var result: TextView
    lateinit var showTrackingButton: Button
    lateinit var trackingContainer: View
    lateinit var closeTrackingButton: ImageButton
    lateinit var reloadTrackingButton: ImageButton
    lateinit var shareTrackingButton: ImageButton
    lateinit var trackingInfo: TextView

    lateinit var wifiScanner: WifiScanner
    lateinit var bluetoothScanner: BluetoothScanner

    private var currentBluetoothDevices: List<DeviceData> = emptyList()
    private var trackingJob: Job? = null
    private var isCurrentlyShowingTracking = false

    private val trackingManager by lazy { TrackingStorageManager(applicationContext) }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        wifiScanner = WifiScanner(this)
        bluetoothScanner = BluetoothScanner(this)

        findViews()
        setClickListeners()

        bluetoothScanner.discoveredBluetoothDevicesLiveData.observe(
            this,
            Observer { bluetoothDevices ->
                currentBluetoothDevices = bluetoothDevices
            })
    }

    private fun findViews() {
        addButton = findViewById(R.id.add_button)
        subButton = findViewById(R.id.sub_button)
        multButton = findViewById(R.id.mult_button)
        divButton = findViewById(R.id.div_button)
        valueA = findViewById(R.id.value_a)
        valueB = findViewById(R.id.value_b)
        result = findViewById(R.id.result)
        showTrackingButton = findViewById(R.id.show_tracking_button)
        trackingContainer = findViewById(R.id.tracking_container)
        closeTrackingButton = findViewById(R.id.close_tracking_button)
        reloadTrackingButton = findViewById(R.id.reload_tracking_button)
        trackingInfo = findViewById(R.id.tracking_info)
        shareTrackingButton = findViewById(R.id.share_tracking_button)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun setClickListeners() {
        addButton.setOnClickListener(this)
        subButton.setOnClickListener(this)
        multButton.setOnClickListener(this)
        divButton.setOnClickListener(this)

        showTrackingButton.setOnClickListener {
            handleTrackingAction()
        }

        reloadTrackingButton.setOnClickListener {
            handleTrackingAction()
        }

        shareTrackingButton.setOnClickListener {
            shareTrackingInfo()
        }

        closeTrackingButton.setOnClickListener {
            hideTracking()
        }
    }

    override fun onClick(v: View?) {
        try {
            val aStr = valueA.text.toString()
            val bStr = valueB.text.toString()

            if (aStr.isEmpty()) {
                result.text = "Enter a value!"
                return
            } else if (bStr.isEmpty()) {
                result.text = "Enter b value!"
                return
            }

            val a = aStr.toDouble()
            val b = bStr.toDouble()
            var res = 0.0

            when (v?.id) {
                R.id.add_button -> res = a + b
                R.id.sub_button -> res = a - b
                R.id.mult_button -> res = a * b
                R.id.div_button -> {
                    if (b == 0.0) {
                        result.text = "Cannot divide by zero!"
                        return
                    }
                    res = a / b
                }
            }
            result.text = "Result is $res"
        } catch (e: NumberFormatException) {
            valueA.setText("")
            valueB.setText("")
            result.text = "Only numeric characters!"
        }
    }

    // Handle tracking process
    @RequiresApi(Build.VERSION_CODES.P)
    private fun handleTrackingAction() {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        var currentFocusedView = currentFocus
        if (currentFocusedView == null) {
            currentFocusedView = View(this)
        }
        inputMethodManager.hideSoftInputFromWindow(currentFocusedView.windowToken, 0)

        if (hasAllRequiredPermissions()) {
            trackingJob?.cancel()
            trackingJob = lifecycleScope.launch {
                try {
                    showTracking()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "The scanning process has not finished",
                        Toast.LENGTH_LONG
                    ).show()
                    if (isCurrentlyShowingTracking) {
                        hideTrackingUiElements()
                    }
                }
            }
        } else {
            requestRequiredPermissions()
        }
    }

    // Function to share the file with tracking info
    private fun shareTrackingInfo() {
        val context = this
        val jsonFileName = "tracking_info.json"
        val privateFile = File(context.filesDir, jsonFileName)

        if (!privateFile.exists()) {
            return
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                privateFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share file with..."))

        } catch (e: IllegalArgumentException) {
            Toast.makeText(
                this@MainActivity,
                "There are no files to share",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Function to show all the tracking data
    @RequiresApi(Build.VERSION_CODES.P)
    private suspend fun showTracking() {

        withContext(Dispatchers.Main) {
            trackingContainer.visibility = View.VISIBLE
            trackingInfo.text = "Checking services..."
            isCurrentlyShowingTracking = true
        }

        val isBluetoothOn = bluetoothScanner.isBluetoothEnabled()
        val isWifiOn = wifiScanner.isWifiEnabled()
        val isLocationOn = isLocationEnabled()

        if (!isWifiOn || !isLocationOn || !isBluetoothOn) {
            val issues = mutableListOf<String>()
            if (!isWifiOn) issues.add("- Wi-Fi is disabled.")
            if (!isLocationOn) issues.add("- Location is disabled.")
            if (!isBluetoothOn) issues.add("- Bluetooth is disabled")
            val message = issues.joinToString(separator = "\n") + ". \n\nPlease enable the required services to proceed."
            withContext(Dispatchers.Main) { trackingInfo.text = message }
            return
        }

        withContext(Dispatchers.Main) { trackingInfo.text = "Scanning..." }

        val wifiScanDeferred: Deferred<List<WifiScanInfo>> =
            lifecycleScope.async(Dispatchers.IO) {
                wifiScanner.launchWifiScan(5000L)
            }
        val bluetoothScanDeferred: Deferred<List<DeviceData>> =
            lifecycleScope.async(Dispatchers.IO) {
                bluetoothScanner.launchBluetoothScan(5000L)
            }

        val wifiScanList = wifiScanDeferred.await()
        val bluetoothScanList = bluetoothScanDeferred.await()

        val elapsedTimeValue = SystemClock.elapsedRealtime()
        val displayMetrics = resources.displayMetrics
        val screenWidthValue = displayMetrics.widthPixels
        val screenHeightValue = displayMetrics.heightPixels
        val currentTimezoneValue = ZoneId.systemDefault().id
        val deviceModelValue = Build.MODEL
        val manufacturerValue = Build.MANUFACTURER
        val androidVersionValue = Build.VERSION.RELEASE
        val buildFingerprintValue = Build.FINGERPRINT
        val kernelVersionValue = Os.uname()?.release ?: "N/A"

        val deviceComponents = DeviceFingerprintComponents(
            elapsedTime = elapsedTimeValue,
            screenWidth = screenWidthValue,
            screenHeight = screenHeightValue,
            currentTimezone = currentTimezoneValue,
            deviceModel = deviceModelValue,
            manufacturer = manufacturerValue,
            androidVersion = androidVersionValue,
            buildFingerprint = buildFingerprintValue,
            kernelVersion = kernelVersionValue
        )

        withContext(Dispatchers.IO) {
            trackingManager.updateAndSaveHashes(deviceComponents, wifiScanList, bluetoothScanList)
        }

        val formattedWifiList = formatWifiResults(wifiScanList)
        val formattedBluetoothList = formatBluetoothResults(bluetoothScanList)

        val formattedTrackingInfo = buildTrackingInfoHtml(
            elapsedTimeValue,
            screenWidthValue,
            screenHeightValue,
            currentTimezoneValue,
            deviceModelValue,
            manufacturerValue,
            androidVersionValue,
            buildFingerprintValue,
            kernelVersionValue,
            formattedWifiList,
            formattedBluetoothList
        )

        withContext(Dispatchers.Main) {
            trackingInfo.text = Html.fromHtml(formattedTrackingInfo, Html.FROM_HTML_MODE_LEGACY)
        }
    }

    // Format wifi scan results to show in html
    private fun formatWifiResults(wifiList: List<WifiScanInfo>): String {
        return if (wifiList.isEmpty()) {
            "No WiFi devices found or scan failed."
        } else {
            wifiList.sortedByDescending { it.rssi }.joinToString("<br>") {
                "• ${it.ssid} - ${it.bssid} (RSSI: ${it.rssi})"
            }
        }
    }

    // Format bluetooth scan results to show in html
    private fun formatBluetoothResults(bluetoothList: List<DeviceData>): String {
        return if (bluetoothList.isEmpty()) {
            "No Bluetooth devices found or scan failed."
        } else {
            bluetoothList.sortedByDescending { it.rssi }.joinToString("<br>") {
                "• ${it.name} - ${it.address} (RSSI: ${it.rssi})"
            }
        }
    }

    // Build tracking info html
    private fun buildTrackingInfoHtml(
        elapsedTime: Long, screenWidth: Int, screenHeight: Int, timezone: String,
        model: String, manufacturer: String, androidVersion: String, fingerprint: String,
        kernelVersion: String, wifiHtml: String, bluetoothHtml: String
    ): String {
        return "<b>Elapsed Realtime:</b> $elapsedTime ms<br>" +
                "<b>Display metrics:</b> $screenWidth x $screenHeight px<br>" +
                "<b>Current Timezone:</b> $timezone<br>" +
                "<b>Device model:</b> $model<br>" +
                "<b>Manufacturer:</b> $manufacturer<br>" +
                "<b>Android version:</b> $androidVersion<br>" +
                "<b>Build fingerprint:</b> $fingerprint<br>" +
                "<b>Kernel version:</b> $kernelVersion<br><br>" +
                "<b>Detected WiFi Networks (SSID-BSSID (RSSI)):</b><br>${wifiHtml}<br><br>" +
                "<b>Detected Bluetooth Devices (Name-Address (RSSI)):</b><br>${bluetoothHtml}<br>"
    }

    // Hide UI tracking elements
    private fun hideTracking() {
        hideTrackingUiElements()
        trackingJob?.cancel()
        trackingJob = null
        bluetoothScanner.stopBluetoothScan()
    }

    // Hide UI tracking elements complementary function
    private fun hideTrackingUiElements() {
        trackingContainer.visibility = View.GONE
        isCurrentlyShowingTracking = false
    }

    // Check required permissions
    private val requiredPermissions: Array<String> by lazy {
        val permissions = mutableSetOf<String>()
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            permissions.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        permissions.add(Manifest.permission.ACCESS_WIFI_STATE)
        permissions.add(Manifest.permission.CHANGE_WIFI_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val distinctPermissions = permissions.distinct().toTypedArray()
        distinctPermissions
    }

    // Check if all required permissions are granted
    private fun hasAllRequiredPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Required permissions launcher
    @RequiresApi(Build.VERSION_CODES.P)
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsMap ->
            val allGranted = permissionsMap.entries.all { it.value }
            if (!allGranted) {
                Toast.makeText(
                    this,
                    "Necessary permissions not granted",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, "All required permissions granted", Toast.LENGTH_SHORT)
                    .show()
            }
        }

    // Request required permissions
    @RequiresApi(Build.VERSION_CODES.P)
    private fun requestRequiredPermissions() {
        requestPermissionLauncher.launch(requiredPermissions)
    }

    // Check if location is enabled
    @RequiresApi(Build.VERSION_CODES.P)
    private fun isLocationEnabled(): Boolean {
        val locationManager =
            applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager?
        return locationManager?.isLocationEnabled ?: false
    }

    //Activity lifecycle methods
    override fun onDestroy() {
        super.onDestroy()
        trackingJob?.cancel()
        bluetoothScanner.cleanup()
        wifiScanner.unregisterReceiver()
    }
}
