package com.velgun.airpodscanner

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AirPodsBLEScanner(val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val _airPodsStatus = MutableStateFlow<AirPodsStatus?>(null)
    val airPodsStatus: StateFlow<AirPodsStatus?> = _airPodsStatus.asStateFlow()

    val airPodsStatusLiveData = MutableLiveData<AirPodsStatus?>()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanCallback: ScanCallback? = null

    /**
     * Check if device supports BLE and has necessary permissions
     */
    fun hasRequiredPermissions(): Boolean {
        val hasBluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }

        return hasBluetoothPermissions &&
                bluetoothAdapter?.isEnabled == true &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    /**
     * Start scanning for AirPods
     */
    fun startScanning() {
        if (!hasRequiredPermissions()) {
            Log.e("AirPodsScanner", "Missing required permissions")
            return
        }else{
            Log.e("AirPodsScanner","Permissions Fulfilled")
        }

        if (_isScanning.value) {
            Log.w("AirPodsScanner", "Already scanning")
            return
        }else{
            Log.w("AirPodsScanner", "New scanning")
        }

        val filters = createScanFilters()
        val settings = createScanSettings()

        scanCallback = object : ScanCallback() {

            override fun onBatchScanResults(results: List<ScanResult?>?) {
                Log.d("AirPodsScanner", "onBatchScanResults: ${results?.size}")

                results?.forEach { result->
                    result?.let {
                        val status = PodsStatusParser.parseFromScanResult(it)
                        if (status != null) {
                            _airPodsStatus.value = status
                            airPodsStatusLiveData.postValue(status)
                            stopScanning()
                            Log.d("AirPodsScanner", "AirPods detected: $status")
                        }
                        Log.d("AirPodsScanner", "AirPods device model: ${status?.deviceModel}")
                    }
                }
                super.onBatchScanResults(results)
            }

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                Log.d("AirPodsScanner", "AirPods result: $result")
                val status = PodsStatusParser.parseFromScanResult(result)
                Log.d("AirPodsScanner", "AirPods device model: ${status?.deviceModel}")


                if (status != null) {
                    _airPodsStatus.value = status
                    airPodsStatusLiveData.postValue(status)
                    stopScanning()
                    Log.d("AirPodsScanner", "AirPods detected: $status")
                }

            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e("AirPodsScanner", "Scan failed with error code: $errorCode")
                _isScanning.value = false
            }
        }

        try {
            bluetoothLeScanner?.startScan(filters, settings, scanCallback)
            _isScanning.value = true
            Log.i("AirPodsScanner", "Started BLE scanning for AirPods")
        } catch (e: SecurityException) {
            Log.e("AirPodsScanner", "Security exception during scan start", e)
        }
    }

    /**
     * Stop scanning
     */
    fun stopScanning() {
        if (!_isScanning.value) return

        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            _isScanning.value = false
            Log.i("AirPodsScanner", "Stopped BLE scanning")
        } catch (e: SecurityException) {
            Log.e("AirPodsScanner", "Security exception during scan stop", e)
        }
    }

    /**
     * Create scan filters for Apple devices
     */
    private fun createScanFilters(): List<ScanFilter> {
        val manufacturerData = ByteArray(27)
        val manufacturerDataMask = ByteArray(27)

        // Filter for Apple's continuity protocol (data[0] = 7, data[1] = 25)
        manufacturerData[0] = 7
        manufacturerData[1] = 25
        manufacturerDataMask[0] = -1
        manufacturerDataMask[1] = -1

        val builder = ScanFilter.Builder()
        builder.setManufacturerData(76, manufacturerData, manufacturerDataMask)
        return listOf(builder.build())
    }


    private fun createScanSettings(): ScanSettings {
        return ScanSettings.Builder()
            // Use the highest-duty cycle scan mode for lowest latency.
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            // Report results immediately instead of batching them.
            .setReportDelay(2)
            .build()
    }
}