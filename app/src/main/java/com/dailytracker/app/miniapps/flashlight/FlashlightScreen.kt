package com.dailytracker.app.miniapps.flashlight

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashlightScreen(
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FlashlightViewModel = viewModel()
) {
    val hasFlash by viewModel.hasFlash.collectAsStateWithLifecycle()
    val hasLightSensor by viewModel.hasLightSensor.collectAsStateWithLifecycle()
    val torchOn by viewModel.torchOn.collectAsStateWithLifecycle()
    val autoModeEnabled by viewModel.autoModeEnabled.collectAsStateWithLifecycle()
    val thresholdLux by viewModel.thresholdLux.collectAsStateWithLifecycle()
    val currentLux by viewModel.currentLux.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val cameraGranted = if (results.containsKey(Manifest.permission.CAMERA)) {
            results[Manifest.permission.CAMERA] == true
        } else {
            viewModel.hasCameraPermission()
        }
        if (cameraGranted) {
            pendingAction?.invoke()
        } else {
            scope.launch { snackbarHostState.showSnackbar("Camera permission is required to control the flashlight.") }
        }
        pendingAction = null
    }

    fun runWithPermissions(needsNotification: Boolean, action: () -> Unit) {
        val needed = mutableListOf<String>()
        if (!viewModel.hasCameraPermission()) needed.add(Manifest.permission.CAMERA)
        if (needsNotification &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !viewModel.hasNotificationPermission()
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.wasAutoModeEnabled() && !autoModeEnabled) {
            runWithPermissions(needsNotification = true) {
                viewModel.setAutoMode(true)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Flashlight",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateToHome,
                        modifier = Modifier.testTag("flashlight_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                onClick = {
                    if (!hasFlash) return@Surface
                    if (autoModeEnabled) {
                        scope.launch { snackbarHostState.showSnackbar("Turn off auto mode to control the flashlight manually.") }
                        return@Surface
                    }
                    runWithPermissions(needsNotification = false) {
                        viewModel.toggleTorchManually()
                    }
                },
                enabled = hasFlash,
                shape = CircleShape,
                color = if (torchOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .size(140.dp)
                    .testTag("flashlight_toggle_btn")
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = if (torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                        contentDescription = if (torchOn) "Turn off flashlight" else "Turn on flashlight",
                        tint = if (torchOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(60.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (torchOn) "Flashlight is ON" else "Flashlight is OFF",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            if (!hasFlash) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This device doesn't have a flashlight.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto flashlight",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Turns on automatically when it gets dark, and off again in the light",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoModeEnabled,
                            enabled = hasFlash && hasLightSensor,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    runWithPermissions(needsNotification = true) {
                                        viewModel.setAutoMode(true)
                                    }
                                } else {
                                    viewModel.setAutoMode(false)
                                }
                            },
                            modifier = Modifier.testTag("flashlight_auto_switch")
                        )
                    }

                    if (!hasLightSensor) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This device has no ambient light sensor, so auto mode isn't available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Sensitivity",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Turn on below ${thresholdLux.roundToInt()} lux",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = thresholdLux,
                        onValueChange = { viewModel.setThreshold(it) },
                        valueRange = 1f..100f,
                        enabled = hasFlash && hasLightSensor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("flashlight_threshold_slider")
                    )

                    Text(
                        text = if (currentLux >= 0) {
                            "Current light level: ${currentLux.roundToInt()} lux"
                        } else {
                            "Current light level: —"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
