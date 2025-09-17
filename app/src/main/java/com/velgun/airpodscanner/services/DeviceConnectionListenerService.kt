package com.velgun.airpodscanner.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.velgun.airpodscanner.MainActivity

class DeviceConnectionListenerService : Service() {

    private val TAG = "ForegroundService"
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "airpods_monitor_channel"

    companion object Companion {
        var isRunning = false
        const val ACTION_START_SCAN = "com.velgun.airpodscanner.ACTION_START_SCAN"
        // Key for the extra data (the device name)
        const val EXTRA_DEVICE_NAME = "com.velgun.airpodscanner.EXTRA_DEVICE_NAME"
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent?.action != BluetoothDevice.ACTION_ACL_CONNECTED) {
                return
            }

            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            device?.let {
                if (ActivityCompat.checkSelfPermission(
                        context, Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }

                if (it.name?.contains("AirPods", ignoreCase = true) == true) {
                    Log.d(TAG, "AirPods connected: ${it.name}.")

                    if (Settings.canDrawOverlays(context)) {
                        Log.i(TAG, "Overlay permission is granted. Attempting to start MainActivity.")
                        val mainActivityIntent = Intent(context, MainActivity::class.java).apply {
                            action = ACTION_START_SCAN
                            // MODIFIED: Add the device name as an extra to the intent
                            putExtra(EXTRA_DEVICE_NAME, it.alias)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(mainActivityIntent)
                    } else {
                        Log.e(TAG, "Cannot start activity from background: 'Draw over other apps' permission is not granted.")
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
        registerReceiver(bluetoothReceiver, filter)
        Log.d(TAG, "Service created and receiver registered.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Service started.")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        unregisterReceiver(bluetoothReceiver)
        Log.d(TAG, "Service destroyed and receiver unregistered.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val name = "AirPods Monitor Service"
        val descriptionText = "Persistent notification to keep the AirPods monitor running."
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AirPods Monitor")
            .setContentText("Listening for AirPods to connect.")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }
}