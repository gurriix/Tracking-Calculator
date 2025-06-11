package com.example.simplecalculator

data class DeviceFingerprintComponents(
    val elapsedTime: Long,
    val screenWidth: Int,
    val screenHeight: Int,
    val currentTimezone: String,
    val deviceModel: String,
    val manufacturer: String,
    val androidVersion: String,
    val buildFingerprint: String,
    val kernelVersion: String
)
