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

/**
 * Opens the log inspector on a device shake, from any screen, with no host
 * boilerplate — the Android counterpart of the iOS `ShakeDetector`.
 *
 * Detection uses a **windowed** algorithm (the approach popularised by Square's
 * Seismic) rather than a single-sample threshold: the accelerometer is sampled
 * quickly and a shake is reported only when most samples over a short window
 * exceed a gentle threshold. This makes a deliberate shake fire *consistently*
 * while ignoring incidental bumps — a single fast jerk between slow samples can
 * no longer be missed, and a lone noisy spike can no longer false-trigger.
 *
 * Where iOS swizzles `UIApplication.sendEvent`, here we track the foreground
 * Activity via [Application.ActivityLifecycleCallbacks] and listen to the
 * accelerometer only while an Activity is resumed (to save battery).
 */
internal object ShakeDetector : SensorEventListener {

    /**
     * Per-sample acceleration magnitude threshold, in m/s². ~13 ≈ 1.3g (gravity
     * at rest is ~9.81). The window check below — not this value — is what keeps
     * accidental triggers out, so this can stay gentle for reliable shakes.
     */
    private const val ACCELERATION_THRESHOLD = 13

    private var installed = false
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    // WeakReference so the static singleton never pins an Activity in memory.
    private var currentActivity: WeakReference<Activity>? = null

    private val queue = SampleQueue()

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
        queue.clear()
        // GAME delay (~20ms) gives enough samples per window to detect a shake
        // reliably; NORMAL (~200ms) is too coarse and misses peaks.
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun stopListening() {
        sensorManager?.unregisterListener(this)
        queue.clear()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val accelerating = isAccelerating(event)
        queue.add(event.timestamp, accelerating)

        if (queue.isShaking) {
            queue.clear()
            handleShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun isAccelerating(event: SensorEvent): Boolean {
        val ax = event.values[0].toDouble()
        val ay = event.values[1].toDouble()
        val az = event.values[2].toDouble()
        val magnitudeSquared = ax * ax + ay * ay + az * az
        return magnitudeSquared > ACCELERATION_THRESHOLD.toDouble() * ACCELERATION_THRESHOLD
    }

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

    /**
     * A short sliding window of recent samples. A shake is reported when the
     * window spans at least [MIN_WINDOW_SIZE] and at least ~75% of its samples
     * were "accelerating". Timestamps are the sensor event's nanosecond clock.
     */
    private class SampleQueue {
        private data class Sample(val timestamp: Long, val accelerating: Boolean)

        private val samples = ArrayDeque<Sample>()
        private var acceleratingCount = 0

        fun add(timestamp: Long, accelerating: Boolean) {
            purge(timestamp - MAX_WINDOW_SIZE)
            samples.addLast(Sample(timestamp, accelerating))
            if (accelerating) acceleratingCount++
        }

        fun clear() {
            samples.clear()
            acceleratingCount = 0
        }

        val isShaking: Boolean
            get() {
                val oldest = samples.firstOrNull() ?: return false
                val newest = samples.lastOrNull() ?: return false
                if (newest.timestamp - oldest.timestamp < MIN_WINDOW_SIZE) return false
                // acceleratingCount >= 75% of the window (size/2 + size/4).
                return acceleratingCount >= (samples.size shr 1) + (samples.size shr 2)
            }

        private fun purge(cutoff: Long) {
            while (samples.size >= MIN_QUEUE_SIZE && samples.first().timestamp < cutoff) {
                val removed = samples.removeFirst()
                if (removed.accelerating) acceleratingCount--
            }
        }

        private companion object {
            const val MAX_WINDOW_SIZE = 500_000_000L // 0.5s in nanoseconds
            const val MIN_WINDOW_SIZE = 250_000_000L // 0.25s in nanoseconds
            const val MIN_QUEUE_SIZE = 4
        }
    }
}
