package com.dailytracker.app.miniapps.flashlight

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide, in-memory state shared between the Flashlight screen and
 * FlashlightService, so the UI always reflects what the service (or a manual
 * toggle) is doing to the torch.
 */
object FlashlightState {
    val hasFlash = MutableStateFlow(true)
    val hasLightSensor = MutableStateFlow(true)
    val torchOn = MutableStateFlow(false)
    val autoModeEnabled = MutableStateFlow(false)
    val thresholdLux = MutableStateFlow(15f)
    val currentLux = MutableStateFlow(-1f)
}
