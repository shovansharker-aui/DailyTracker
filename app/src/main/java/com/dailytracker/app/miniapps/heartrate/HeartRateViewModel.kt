package com.dailytracker.app.miniapps.heartrate

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HeartRatePhase {
    IDLE, AWAITING_FINGER, MEASURING, RESULT, ERROR
}

data class HeartRateUiState(
    val phase: HeartRatePhase = HeartRatePhase.IDLE,
    val liveBpm: Int? = null,
    val resultBpm: Int? = null,
    val elapsedSeconds: Int = 0,
    val totalSeconds: Int = MEASURE_DURATION_SEC,
    val waveform: List<Float> = emptyList(),
    val errorMessage: String? = null
) {
    companion object {
        const val MEASURE_DURATION_SEC = 20
    }
}

class HeartRateViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val cameraSource = HeartRateCameraSource(context)
    private val detector = PulseDetector()

    private val _uiState = MutableStateFlow(HeartRateUiState())
    val uiState = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var fingerPresentStreak = 0
    private var recentWaveform = ArrayDeque<Float>()

    fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    fun startMeasurement() {
        if (_uiState.value.phase == HeartRatePhase.AWAITING_FINGER || _uiState.value.phase == HeartRatePhase.MEASURING) return
        if (!hasCameraPermission()) return

        detector.reset()
        recentWaveform.clear()
        fingerPresentStreak = 0
        _uiState.value = HeartRateUiState(phase = HeartRatePhase.AWAITING_FINGER)

        cameraSource.onSample = { timestampMs, meanLuma -> handleSample(timestampMs, meanLuma) }
        cameraSource.onError = { message ->
            timerJob?.cancel()
            cameraSource.stop()
            _uiState.value = HeartRateUiState(phase = HeartRatePhase.ERROR, errorMessage = message)
        }
        cameraSource.start()

        startTimer()
    }

    fun stopMeasurement() {
        timerJob?.cancel()
        timerJob = null
        cameraSource.stop()
        _uiState.value = HeartRateUiState(phase = HeartRatePhase.IDLE)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val current = _uiState.value
                if (current.phase != HeartRatePhase.MEASURING) continue
                val elapsed = current.elapsedSeconds + 1
                if (elapsed >= current.totalSeconds) {
                    finishMeasurement()
                    break
                } else {
                    _uiState.update { it.copy(elapsedSeconds = elapsed) }
                }
            }
        }
    }

    private fun finishMeasurement() {
        cameraSource.stop()
        val finalBpm = if (detector.hasEnoughData) detector.bpm else null
        _uiState.value = if (finalBpm != null) {
            HeartRateUiState(phase = HeartRatePhase.RESULT, resultBpm = finalBpm)
        } else {
            HeartRateUiState(
                phase = HeartRatePhase.ERROR,
                errorMessage = "Couldn't get a steady reading. Hold your fingertip still, covering both the camera lens and the flash."
            )
        }
    }

    private fun handleSample(timestampMs: Long, meanLuma: Double) {
        val current = _uiState.value
        if (current.phase != HeartRatePhase.AWAITING_FINGER && current.phase != HeartRatePhase.MEASURING) return

        val fingerPresent = meanLuma >= FINGER_LUMA_THRESHOLD
        if (!fingerPresent) {
            fingerPresentStreak = 0
            if (current.phase == HeartRatePhase.MEASURING) {
                // Lost contact mid-measurement: pause and ask the user to re-cover the lens.
                detector.reset()
                recentWaveform.clear()
                _uiState.update {
                    it.copy(phase = HeartRatePhase.AWAITING_FINGER, liveBpm = null, elapsedSeconds = 0, waveform = emptyList())
                }
            }
            return
        }

        fingerPresentStreak++
        if (current.phase == HeartRatePhase.AWAITING_FINGER && fingerPresentStreak >= FINGER_CONFIRM_FRAMES) {
            _uiState.update { it.copy(phase = HeartRatePhase.MEASURING, elapsedSeconds = 0) }
        }

        detector.addSample(timestampMs, meanLuma)

        recentWaveform.addLast(meanLuma.toFloat())
        while (recentWaveform.size > WAVEFORM_POINTS) recentWaveform.removeFirst()

        _uiState.update {
            if (it.phase == HeartRatePhase.MEASURING) {
                it.copy(liveBpm = detector.bpm, waveform = recentWaveform.toList())
            } else {
                it.copy(waveform = recentWaveform.toList())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        cameraSource.stop()
    }

    companion object {
        private const val FINGER_LUMA_THRESHOLD = 60.0
        private const val FINGER_CONFIRM_FRAMES = 8
        private const val WAVEFORM_POINTS = 90
    }
}
