package com.dailytracker.app.miniapps.heartrate

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateScreen(
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HeartRateViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startMeasurement() }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopMeasurement() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Heart Rate",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.stopMeasurement()
                            onNavigateToHome()
                        },
                        modifier = Modifier.testTag("heartrate_back_btn")
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
                shape = CircleShape,
                color = when (uiState.phase) {
                    HeartRatePhase.MEASURING -> MaterialTheme.colorScheme.primary
                    HeartRatePhase.RESULT -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.size(140.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (uiState.phase == HeartRatePhase.RESULT) {
                        Text(
                            text = "${uiState.resultBpm}",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = if (uiState.phase == HeartRatePhase.MEASURING) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = statusText(uiState),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )

            if (uiState.phase == HeartRatePhase.MEASURING) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${uiState.elapsedSeconds}s / ${uiState.totalSeconds}s" +
                        (uiState.liveBpm?.let { "  •  ~$it bpm" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.phase == HeartRatePhase.ERROR && uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            if (uiState.phase == HeartRatePhase.MEASURING || uiState.phase == HeartRatePhase.AWAITING_FINGER) {
                Spacer(modifier = Modifier.height(20.dp))
                PulseWaveform(
                    values = uiState.waveform,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            when (uiState.phase) {
                HeartRatePhase.IDLE, HeartRatePhase.RESULT, HeartRatePhase.ERROR -> {
                    Button(
                        onClick = {
                            if (viewModel.hasCameraPermission()) {
                                viewModel.startMeasurement()
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("heartrate_start_btn")
                    ) {
                        Text(if (uiState.phase == HeartRatePhase.RESULT) "Measure Again" else "Start Measuring")
                    }
                }
                HeartRatePhase.AWAITING_FINGER, HeartRatePhase.MEASURING -> {
                    OutlinedButton(
                        onClick = { viewModel.stopMeasurement() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("heartrate_stop_btn")
                    ) {
                        Text("Cancel")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How to measure",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Gently cover the rear camera lens and flash with your fingertip, hold still, " +
                            "and keep steady pressure for the whole reading.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This is an estimate from your camera's light sensor, not a medical device. " +
                            "Don't use it to make health decisions — see a doctor for that.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun statusText(uiState: HeartRateUiState): String = when (uiState.phase) {
    HeartRatePhase.IDLE -> "Ready to measure"
    HeartRatePhase.AWAITING_FINGER -> "Cover the camera and flash with your fingertip"
    HeartRatePhase.MEASURING -> "Hold still…"
    HeartRatePhase.RESULT -> "bpm"
    HeartRatePhase.ERROR -> "Couldn't get a reading"
}

@Composable
private fun PulseWaveform(values: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)

        val path = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { index, value ->
            val normalized = (value - min) / range
            val x = index * stepX
            val y = size.height - (normalized * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = androidx.compose.ui.graphics.Color(0xFFE53935),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 4f,
                cap = StrokeCap.Round
            )
        )
    }
}
