package com.henrydavl.apilogkit

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.henrydavl.apilogkit.model.ApiLogger
import com.henrydavl.apilogkit.ui.ApiLogActivity
import java.lang.ref.WeakReference
import kotlin.math.sqrt

/**
 * Opens the log inspector on a device shake, from any screen, with no host
 * boilerplate — the Android counterpart of the iOS `ShakeDetector`.
 *
 * Where iOS swizzles `UIApplication.sendEvent`, here we track the foreground
 * Activity via [Application.ActivityLifecycleCallbacks] and listen to the
 * accelerometer only while an Activity is resumed (to save battery).
 */
internal object ShakeDetector : SensorEventListener {

    private const val SHAKE_THRESHOLD_GRAVITY = 2.7f
    private const val SHAKE_COOLDOWN_MS = 1_000L

    private var installed = false
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    // WeakReference so the static singleton never pins an Activity in memory.
    private var currentActivity: WeakReference<Activity>? = null
    private var lastShakeTime = 0L

    fun install(application: Application) {
        if (installed) return
        installed = true

        sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivity = WeakReference(activity)
                // Don't listen for shakes while the inspector itself is showing.
                if (activity is ApiLogActivity) {
                    stopListening()
                } else {
                    startListening()
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivity?.get() === activity) currentActivity = null
                stopListening()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun startListening() {
        val manager = sensorManager ?: return
        val sensor = accelerometer ?: return
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val gX = event.values[0] / SensorManager.GRAVITY_EARTH
        val gY = event.values[1] / SensorManager.GRAVITY_EARTH
        val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime < SHAKE_COOLDOWN_MS) return
            lastShakeTime = now
            handleShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun handleShake() {
        if (!ApiLogger.isEnabled) return
        val activity = currentActivity?.get() ?: return
        if (activity is ApiLogActivity) return

        vibrate(activity)
        activity.startActivity(Intent(activity, ApiLogActivity::class.java))
    }

    @Suppress("DEPRECATION")
    private fun vibrate(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(40)
            }
        } catch (_: Exception) {
            // Vibration is best-effort; ignore if unavailable or not permitted.
        }
    }
}
