package com.dailytracker.app.miniapps.heartrate

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.annotation.RequiresPermission

/**
 * Opens the back camera directly (Camera2, not CameraX — this app has no CameraX
 * dependency), turns the torch on for the session, and streams the average
 * frame brightness to [onSample]. Callers must hold CAMERA permission.
 */
class HeartRateCameraSource(private val context: Context) {

    var onSample: ((timestampMs: Long, meanLuma: Double) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    @RequiresPermission(android.Manifest.permission.CAMERA)
    fun start() {
        stop() // ensure a clean slate

        val thread = HandlerThread("HeartRateCamera").also { it.start() }
        backgroundThread = thread
        val handler = Handler(thread.looper)
        backgroundHandler = handler

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = findBackCameraWithFlash(manager)
        if (cameraId == null) {
            onError?.invoke("This device has no usable rear camera with flash.")
            return
        }

        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraDevice = device
                    startSession(device)
                }

                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    cameraDevice = null
                }

                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    cameraDevice = null
                    onError?.invoke("Camera error (code $error).")
                }
            }, handler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "openCamera failed", e)
            onError?.invoke("Couldn't access the camera.")
        } catch (e: SecurityException) {
            Log.e(TAG, "openCamera missing permission", e)
            onError?.invoke("Camera permission is required.")
        }
    }

    private fun startSession(device: CameraDevice) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = try {
            manager.getCameraCharacteristics(device.id)
        } catch (e: CameraAccessException) {
            onError?.invoke("Couldn't read camera characteristics.")
            return
        }
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val size = smallestYuvSize(map)
        if (size == null) {
            onError?.invoke("This camera doesn't support the required preview format.")
            return
        }

        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ r ->
            val image = try {
                r.acquireLatestImage()
            } catch (e: Exception) {
                null
            }
            if (image != null) {
                try {
                    val luma = averageLuma(image)
                    onSample?.invoke(System.currentTimeMillis(), luma)
                } finally {
                    image.close()
                }
            }
        }, backgroundHandler)

        try {
            device.createCaptureSession(
                listOf(reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(reader.surface)
                                set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
                                set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
                                set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                            }
                            session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "setRepeatingRequest failed", e)
                            onError?.invoke("Couldn't start the camera preview.")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onError?.invoke("Couldn't configure the camera.")
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "createCaptureSession failed", e)
            onError?.invoke("Couldn't start the camera.")
        }
    }

    fun stop() {
        try {
            captureSession?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing session", e)
        }
        captureSession = null

        try {
            cameraDevice?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
        cameraDevice = null

        imageReader?.close()
        imageReader = null

        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join(500)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        backgroundThread = null
        backgroundHandler = null
    }

    private fun averageLuma(image: Image): Double {
        val plane = image.planes[0] // Y plane: overall brightness, cheap to read
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height

        // Sample a sparse grid instead of every pixel — plenty for a brightness average.
        val stepX = (width / 40).coerceAtLeast(1)
        val stepY = (height / 40).coerceAtLeast(1)

        var sum = 0L
        var count = 0
        var y = 0
        while (y < height) {
            var x = 0
            val rowStart = y * rowStride
            while (x < width) {
                val index = rowStart + x * pixelStride
                if (index < buffer.capacity()) {
                    sum += buffer.get(index).toInt() and 0xFF
                    count++
                }
                x += stepX
            }
            y += stepY
        }
        return if (count > 0) sum.toDouble() / count else 0.0
    }

    private fun smallestYuvSize(map: StreamConfigurationMap?): android.util.Size? {
        val sizes = map?.getOutputSizes(ImageFormat.YUV_420_888) ?: return null
        return sizes.filter { it.width > 0 && it.height > 0 }.minByOrNull { it.width.toLong() * it.height }
    }

    private fun findBackCameraWithFlash(manager: CameraManager): String? {
        return try {
            manager.cameraIdList.firstOrNull { id ->
                val c = manager.getCameraCharacteristics(id)
                val facing = c.get(CameraCharacteristics.LENS_FACING)
                val hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                facing == CameraCharacteristics.LENS_FACING_BACK && hasFlash
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error listing cameras", e)
            null
        }
    }

    companion object {
        private const val TAG = "HeartRateCameraSource"
    }
}
