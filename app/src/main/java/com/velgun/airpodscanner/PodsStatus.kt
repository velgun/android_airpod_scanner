package com.velgun.airpodscanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult

data class AirPodsStatus(
    val leftBattery: Int = -1,
    val rightBattery: Int = -1,
    val caseBattery: Int = -1,
    val isLeftCharging: Boolean = false,
    val isRightCharging: Boolean = false,
    val isCaseCharging: Boolean = false,
    val deviceModel: String = "Unknown",
    val isConnected: Boolean = false,
    val rssi: Int = 0,
    val aliasName : String = ""
)

object PodsStatusParser {

    private const val AIRPODS_MANUFACTURER_ID = 76 // 0x004C (Apple)
    private const val AIRPODS_DATA_LENGTH = 27
    private const val MIN_RSSI = -60

    /**
     * Parse AirPods status from BLE scan result
     * Based on OpenPods reverse engineering implementation
     */
    fun parseFromScanResult(scanResult: ScanResult): AirPodsStatus? {
        val manufacturerData = scanResult.scanRecord
            ?.getManufacturerSpecificData(AIRPODS_MANUFACTURER_ID)
            ?: return null

        // Validate data length and signal strength
        if (manufacturerData.size != AIRPODS_DATA_LENGTH || scanResult.rssi < MIN_RSSI) {
            return null
        }

        return parseManufacturerData(manufacturerData, scanResult.rssi, scanResult.device)
    }

    /**
     * Parse the 27-byte manufacturer specific data
     * Protocol reverse engineered from OpenPods project
     */
    @SuppressLint("MissingPermission")
    private fun parseManufacturerData(
        data: ByteArray,
        rssi: Int,
        device: BluetoothDevice
    ): AirPodsStatus {

        // Convert to hex string for easier parsing
        val hexData = data.joinToString("") {
            String.format("%02x", it.toInt() and 0xFF)
        }

        // Check if data is flipped (bit manipulation from OpenPods)
        val isFlipped = (hexData[10].digitToInt(16) and 0x02) == 0

        // Extract battery levels (positions 12-13 in hex string)
        val leftBatteryRaw = hexData[if (isFlipped) 12 else 13].digitToInt(16)
        val rightBatteryRaw = hexData[if (isFlipped) 13 else 12].digitToInt(16)
        val caseBatteryRaw = hexData[15].digitToInt(16)

        // Convert raw values to battery percentages
        val leftBattery = correctBatteryLevel(leftBatteryRaw)
        val rightBattery = correctBatteryLevel(rightBatteryRaw)
        val caseBattery = correctBatteryLevel(caseBatteryRaw)



        // Parse charging status (position 14 in hex string)
        val chargeStatus = hexData[14].digitToInt(16)
        val isLeftCharging = (chargeStatus and if (isFlipped) 0b00000010 else 0b00000001) != 0
        val isRightCharging = (chargeStatus and if (isFlipped) 0b00000001 else 0b00000010) != 0
        val isCaseCharging = (chargeStatus and 0b00000100) != 0

        // Detect device model from data pattern
        val deviceModel = detectDeviceModel(hexData)

        return AirPodsStatus(
            leftBattery = leftBattery,
            rightBattery = rightBattery,
            caseBattery = caseBattery,
            isLeftCharging = isLeftCharging,
            isRightCharging = isRightCharging,
            isCaseCharging = isCaseCharging,
            deviceModel = deviceModel,
            isConnected = true,
            rssi = rssi,
            aliasName = device.alias?:""
        )
    }

    /**
     * Convert raw battery value to percentage
     * Formula based on OpenPods: (value * 10) + 5 for better accuracy
     */
    private fun correctBatteryLevel(rawValue: Int): Int {
        return when {
            rawValue < 0 || rawValue > 10 -> -1 // Invalid/disconnected
            rawValue == 10 -> 100
            else -> (rawValue * 10) + 5
        }
    }

    /**
     * Detect AirPods model from manufacturer data pattern
     * Model IDs reverse engineered from OpenPods
     */
    private fun detectDeviceModel(hexData: String): String {
        val modelId = hexData.substring(6, 10)
        return when (modelId) {
            "0220" -> "AirPods (1st generation)"
            "0f20" -> "AirPods (2nd generation)"
            "1320" -> "AirPods (3rd generation)"
            "0e20" -> "AirPods Pro"
            "1420" -> "AirPods Pro (2nd generation)"
            "0a20" -> "AirPods Max"
            "1020" -> "Beats Solo 3"
            "0620" -> "BeatsX"
            "1120" -> "Powerbeats Pro"
            "0b20" -> "Beats Studio 3"
            "1720" -> "Beats Flex"
            else -> "Unknown Apple Device"
        }
    }
}