package com.velgun.airpodscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.velgun.airpodscanner.services.DeviceConnectionListenerService
import com.velgun.airpodscanner.ui.theme.AirpodScannerTheme
import kotlinx.coroutines.launch
import kotlin.system.exitProcess
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private lateinit var airPodsScanner: AirPodsBLEScanner

    private var scanTriggerAction by mutableStateOf<String?>(null)
    private var detectedDeviceName by mutableStateOf<String?>(null)


    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        airPodsScanner = AirPodsBLEScanner(this)

        handleIntent(intent)

        setContent {
            AirpodScannerTheme {
                var open by rememberSaveable { mutableStateOf(true) }
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                val scope = rememberCoroutineScope()

                LaunchedEffect(open) {
                    if (!open) {
                        finish()
                        overridePendingTransition(0, 0)
                    }
                }

                BackHandler(enabled = open) {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        open = false
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    if (open) {
                        ModalBottomSheet(
                            onDismissRequest = {
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    open = false
                                    exitProcess(-1)
                                }
                            },
                            sheetState = sheetState
                        ) {
                            ModernAirPodsMonitorApp(
                                scanner = airPodsScanner,
                                scanTriggerAction = scanTriggerAction,
                                detectedDeviceName = detectedDeviceName,
                                onScanTriggerConsumed = {
                                    scanTriggerAction = null
                                    // Keep detectedDeviceName until the next connection
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == DeviceConnectionListenerService.ACTION_START_SCAN) {
            this.scanTriggerAction = intent.action
            this.detectedDeviceName = intent.getStringExtra(DeviceConnectionListenerService.EXTRA_DEVICE_NAME)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        airPodsScanner.stopScanning()
    }
}

@Composable
fun ModernAirPodsMonitorApp(
    scanner: AirPodsBLEScanner,
    scanTriggerAction: String?,
    detectedDeviceName: String?,
    onScanTriggerConsumed: () -> Unit
) {
    val context = LocalContext.current

    val airPodsStatus by scanner.airPodsStatus.collectAsState()
    val isScanning by scanner.isScanning.collectAsState()

    var hasBluetoothPermissions by remember { mutableStateOf(false) }
    var serviceRunning by remember { mutableStateOf(DeviceConnectionListenerService.isRunning) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            title = { Text("Permission Required") },
            text = { Text("To automatically open the app and show battery status when your AirPods connect, this app needs permission to display over other apps.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOverlayPermissionDialog = false
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        )
                        overlayPermissionLauncher.launch(intent)
                    }
                ) { Text("Go to Settings") }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDialog = false }) { Text("Cancel") }
            }
        )
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasBluetoothPermissions = permissions.all { it.value }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(context, DeviceConnectionListenerService::class.java)
            context.startForegroundService(intent)
        }
    }

    LaunchedEffect(scanTriggerAction, hasBluetoothPermissions) {
        if (scanTriggerAction == DeviceConnectionListenerService.ACTION_START_SCAN && hasBluetoothPermissions) {
            scanner.startScanning()
            onScanTriggerConsumed()
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { DeviceConnectionListenerService.isRunning }.collect {
            serviceRunning = it
        }
    }

    LaunchedEffect(Unit) {
        hasBluetoothPermissions = scanner.hasRequiredPermissions()
        if (!hasBluetoothPermissions) {
            val permissions =
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            bluetoothPermissionLauncher.launch(permissions)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Monitoring Service",
                style = MaterialTheme.typography.titleMedium
            )
            Switch(
                checked = serviceRunning,
                onCheckedChange = { isChecked ->
                    val intent = Intent(context, DeviceConnectionListenerService::class.java)
                    if (isChecked) {
                        if (!Settings.canDrawOverlays(context)) {
                            showOverlayPermissionDialog = true
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    context.startForegroundService(intent)
                                } else {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                context.startForegroundService(intent)
                            }
                            serviceRunning = false
                        }
                    } else {
                        serviceRunning = false
                        context.stopService(intent)
                    }
                }
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )

        AnimatedContent(
            targetState = hasBluetoothPermissions,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            }, label = ""
        ) { hasPermission ->
            if (hasPermission) {
                if (airPodsStatus != null) {
                    AirPodsStatusCard(
                        status = airPodsStatus!!,
                        // MODIFIED: Pass the detected name to the status card
                        detectedDeviceName = detectedDeviceName,
                        isScanning = isScanning,
                        onToggleScanning = {
                            if (isScanning) scanner.stopScanning() else scanner.startScanning()
                        }
                    )
                } else {
                    SearchingCard(
                        isScanning = isScanning,
                        onToggleScanning = {
                            if (isScanning) scanner.stopScanning() else scanner.startScanning()
                        },
                        detectedDeviceName = detectedDeviceName
                    )
                }
            } else {
                PermissionRequiredCard(
                    onRequestPermissions = {
                        val permissions =
                            arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                            )
                        bluetoothPermissionLauncher.launch(permissions)
                    }
                )
            }
        }
    }
}

@Composable
fun SearchingCard(
    isScanning: Boolean,
    onToggleScanning: () -> Unit,
    modifier: Modifier = Modifier,
    detectedDeviceName: String?
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isScanning) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                contentDescription = "Searching",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))

            val scanningText = if (!detectedDeviceName.isNullOrBlank()) {
                "Connecting to $detectedDeviceName..."
            } else {
                "Searching for AirPods..."
            }
            Text(
                text = scanningText,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        } else {
            Icon(
                imageVector = Icons.Default.BluetoothDisabled,
                contentDescription = "Not Searching",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("No AirPods detected.", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(32.dp))

        FilledTonalButton(onClick = onToggleScanning) {
            Text(if (isScanning) "Stop Scanning" else "Start Scanning")
        }
    }
}

// MODIFIED: This composable now shows both the device name and the model
@Composable
fun AirPodsStatusCard(
    status: AirPodsStatus,
    detectedDeviceName: String?,
    isScanning: Boolean,
    onToggleScanning: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Prioritize showing the user's custom device name. Fall back to the model name if it's not available.
    val displayName = detectedDeviceName?.takeIf { it.isNotBlank() } ?: status.deviceModel

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
       Text(
            text = displayName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        if(status.deviceModel != displayName) Text(
            // Show the specific model from the scan as a subtitle
            text = status.deviceModel,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if(status.deviceModel != displayName) Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Signal Strength: ${status.rssi} dBm",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BatteryIndicator(
                label = "Left",
                batteryLevel = status.leftBattery,
                isCharging = status.isLeftCharging,
            )
            BatteryIndicator(
                label = "Case",
                batteryLevel = status.caseBattery,
                isCharging = status.isCaseCharging,
            )
            BatteryIndicator(
                label = "Right",
                batteryLevel = status.rightBattery,
                isCharging = status.isRightCharging,
            )
        }
        Spacer(modifier = Modifier.height(32.dp))

        FilledTonalButton(onClick = onToggleScanning) {
            Text(if (isScanning) "Stop Scanning" else "Scan Again")
        }
    }
}

@Composable
fun BatteryIndicator(
    label: String,
    batteryLevel: Int,
    isCharging: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        val batteryText = when {
            batteryLevel < 0 -> "N/A"
            isCharging -> "$batteryLevel% ⚡"
            else -> "$batteryLevel%"
        }
        val textColor = when {
            batteryLevel < 0 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            else -> LocalContentColor.current
        }

        Text(
            text = batteryText,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
    }
}

@Composable
fun PermissionRequiredCard(
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BluetoothDisabled,
            contentDescription = "Bluetooth Disabled",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Bluetooth Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "This app needs Bluetooth access to find and connect to your AirPods.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermissions) {
            Text("Grant Permission")
        }
    }
}