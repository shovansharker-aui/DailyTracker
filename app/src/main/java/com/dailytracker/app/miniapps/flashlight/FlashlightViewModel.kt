package com.dailytracker.app.miniapps.flashlight

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.asStateFlow

class FlashlightViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("flashlight_prefs", Context.MODE_PRIVATE)

    val hasFlash = FlashlightState.hasFlash.asStateFlow()
    val hasLightSensor = FlashlightState.hasLightSensor.asStateFlow()
    val torchOn = FlashlightState.torchOn.asStateFlow()
    val autoModeEnabled = FlashlightState.autoModeEnabled.asStateFlow()
    val thresholdLux = FlashlightState.thresholdLux.asStateFlow()
    val currentLux = FlashlightState.currentLux.asStateFlow()

    init {
        FlashlightController.init(context)
        FlashlightState.thresholdLux.value = prefs.getFloat(KEY_THRESHOLD, 15f)
    }

    fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun toggleTorchManually() {
        if (autoModeEnabled.value) return
        FlashlightController.setTorch(!torchOn.value)
    }

    fun setAutoMode(enabled: Boolean) {
        if (enabled) {
            ContextCompat.startForegroundService(context, Intent(context, FlashlightService::class.java))
        } else {
            context.stopService(Intent(context, FlashlightService::class.java))
        }
        prefs.edit().putBoolean(KEY_AUTO_MODE, enabled).apply()
    }

    fun setThreshold(value: Float) {
        FlashlightState.thresholdLux.value = value
        prefs.edit().putFloat(KEY_THRESHOLD, value).apply()
    }

    fun wasAutoModeEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_MODE, false)

    companion object {
        private const val KEY_THRESHOLD = "threshold_lux"
        private const val KEY_AUTO_MODE = "auto_mode_enabled"
    }
}
