import android.content.Context
import com.example.simplecalculator.DeviceData
import com.example.simplecalculator.DeviceFingerprintComponents
import com.example.simplecalculator.WifiScanInfo
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

// Class responsible for store the tracking information
class TrackingStorageManager(private val context: Context) {

    private val jsonFileName = "tracking_info.json"

    private object JsonKeys {
        const val BOOT_TIME = "boot_time"
        const val SCREEN_WIDTH = "screen_width"
        const val SCREEN_HEIGHT = "screen_height"
        const val TIMEZONE = "timezone"
        const val DEVICE_MODEL = "device_model"
        const val MANUFACTURER = "manufacturer"
        const val ANDROID_VERSION = "android_version"
        const val BUILD_FINGERPRINT = "build_fingerprint"
        const val KERNEL_VERSION = "kernel_version"
        const val WIFI_NETWORKS = "wifi_networks"
        const val BLUETOOTH_DEVICES = "bluetooth_devices"
    }

    // New or change hashes update
    fun updateAndSaveHashes(
        currentDeviceComponents: DeviceFingerprintComponents,
        currentWifiScans: List<WifiScanInfo>,
        currentBluetoothScans: List<DeviceData>
    ) {
        var jsonObject = JSONObject()
        val file = File(context.filesDir, jsonFileName)

        if (file.exists() && file.canRead()) {
            try {
                val fileContent = file.readText(StandardCharsets.UTF_8)
                if (fileContent.isNotBlank()) {
                    jsonObject = JSONObject(fileContent)
                }
            } catch (e: IOException) {
                jsonObject = JSONObject()
            } catch (e: JSONException) {
                jsonObject = JSONObject()
            }
        }

        val existingWifiHashesSet = jsonObject.optJSONArray(JsonKeys.WIFI_NETWORKS)?.toStringMutableSet() ?: mutableSetOf()
        val existingBluetoothHashesSet = jsonObject.optJSONArray(JsonKeys.BLUETOOTH_DEVICES)?.toStringMutableSet() ?: mutableSetOf()

        try {
            jsonObject.put(JsonKeys.BOOT_TIME, hashStringSha256(currentDeviceComponents.elapsedTime.toString()))
            jsonObject.put(JsonKeys.SCREEN_WIDTH, hashStringSha256(currentDeviceComponents.screenWidth.toString()))
            jsonObject.put(JsonKeys.SCREEN_HEIGHT, hashStringSha256(currentDeviceComponents.screenHeight.toString()))
            jsonObject.put(JsonKeys.TIMEZONE, hashStringSha256(currentDeviceComponents.currentTimezone))
            jsonObject.put(JsonKeys.DEVICE_MODEL, hashStringSha256(currentDeviceComponents.deviceModel))
            jsonObject.put(JsonKeys.MANUFACTURER, hashStringSha256(currentDeviceComponents.manufacturer))
            jsonObject.put(JsonKeys.ANDROID_VERSION, hashStringSha256(currentDeviceComponents.androidVersion))
            jsonObject.put(JsonKeys.BUILD_FINGERPRINT, hashStringSha256(currentDeviceComponents.buildFingerprint))
            jsonObject.put(JsonKeys.KERNEL_VERSION, hashStringSha256(currentDeviceComponents.kernelVersion))
        } catch (e: JSONException) {
            return
        }

        currentWifiScans.forEach { wifiInfo ->
            if (!wifiInfo.bssid.isNullOrBlank()) {
                existingWifiHashesSet.add(hashStringSha256(wifiInfo.bssid))
            }
        }
        jsonObject.put(JsonKeys.WIFI_NETWORKS, JSONArray(existingWifiHashesSet.toList().sorted()))

        currentBluetoothScans.forEach { deviceData ->
            if (deviceData.address.isNotBlank()) {
                existingBluetoothHashesSet.add(hashStringSha256(deviceData.address))
            }
        }
        jsonObject.put(JsonKeys.BLUETOOTH_DEVICES, JSONArray(existingBluetoothHashesSet.toList().sorted()))

        try {
            file.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonObject.toString(4))
            }
        } catch (e: IOException) {}
    }

    // Convert json array to string mutable set
    private fun JSONArray.toStringMutableSet(): MutableSet<String> {
        val set = mutableSetOf<String>()
        for (i in 0 until this.length()) {
            try {
                set.add(this.getString(i))
            } catch (e: JSONException) {}
        }
        return set
    }

    // Make the hash of the data
    private fun hashStringSha256(input: String): String {
        val bytes = input.toByteArray(StandardCharsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}