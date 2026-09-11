package com.dailytracker.app.miniapps.flashlight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dailytracker.app.MainActivity
import kotlin.math.roundToInt

/**
 * Foreground service that watches the ambient light sensor and drives the torch
 * (via FlashlightController) based on FlashlightState.thresholdLux, with a bit of
 * hysteresis so the flashlight doesn't flicker on/off right at the threshold.
 */
class FlashlightService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var torchOnByAuto = false

    override fun onCreate() {
        super.onCreate()
        FlashlightController.init(applicationContext)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        FlashlightState.hasLightSensor.value = lightSensor != null
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val sensor = lightSensor
        if (sensor == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(FlashlightState.currentLux.value))
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        FlashlightState.autoModeEnabled.value = true
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        val lux = event.values[0]
        FlashlightState.currentLux.value = lux

        val threshold = FlashlightState.thresholdLux.value
        val releaseThreshold = threshold + (threshold * 0.5f) + 2f

        if (lux < threshold && !torchOnByAuto) {
            FlashlightController.setTorch(true)
            torchOnByAuto = true
        } else if (lux > releaseThreshold && torchOnByAuto) {
            FlashlightController.setTorch(false)
            torchOnByAuto = false
        }

        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(lux))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        if (torchOnByAuto) {
            FlashlightController.setTorch(false)
            torchOnByAuto = false
        }
        FlashlightState.autoModeEnabled.value = false
        FlashlightState.currentLux.value = -1f
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto Flashlight",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(lux: Float): Notification {
        val stopIntent = Intent(this, FlashlightService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("navigate_to", "flashlight")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (lux >= 0) {
            "Current light level: ${lux.roundToInt()} lux"
        } else {
            "Waiting for a light reading…"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Auto flashlight is watching the light level")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .addAction(0, "Turn off", stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.dailytracker.app.flashlight.action.STOP"
        private const val CHANNEL_ID = "flashlight_auto_mode"
        private const val NOTIFICATION_ID = 4201
    }
}
