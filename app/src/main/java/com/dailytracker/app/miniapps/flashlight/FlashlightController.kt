package com.dailytracker.app.miniapps.flashlight

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

/**
 * Thin wrapper around CameraManager's torch API. A CameraManager.TorchCallback keeps
 * FlashlightState.torchOn in sync no matter what turned the torch on or off.
 */
object FlashlightController {

    private const val TAG = "FlashlightController"

    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null
    private var callbackRegistered = false

    fun init(context: Context) {
        if (cameraManager != null) return

        val manager = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraManager = manager
        cameraId = findFlashCameraId(manager)
        FlashlightState.hasFlash.value = cameraId != null

        if (cameraId != null && !callbackRegistered) {
            manager.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraIdChanged: String, enabled: Boolean) {
                    if (cameraIdChanged == cameraId) {
                        FlashlightState.torchOn.value = enabled
                    }
                }
            }, null)
            callbackRegistered = true
        }
    }

    fun setTorch(on: Boolean) {
        val manager = cameraManager ?: return
        val id = cameraId ?: return
        try {
            manager.setTorchMode(id, on)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set torch mode", e)
        }
    }

    private fun findFlashCameraId(manager: CameraManager): String? {
        return try {
            val ids = manager.cameraIdList
            ids.firstOrNull { id -> isBackCameraWithFlash(manager, id) }
                ?: ids.firstOrNull { id -> hasFlash(manager, id) }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding a camera with flash", e)
            null
        }
    }

    private fun hasFlash(manager: CameraManager, id: String): Boolean =
        manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

    private fun isBackCameraWithFlash(manager: CameraManager, id: String): Boolean {
        val characteristics = manager.getCameraCharacteristics(id)
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        return hasFlash(manager, id) && facing == CameraCharacteristics.LENS_FACING_BACK
    }
}
