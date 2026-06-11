package com.henrydavl.apilogkit.model

import android.app.Application
import com.henrydavl.apilogkit.ShakeDetector
import com.henrydavl.apilogkit.notification.ApiLogNotifier

/**
 * In-memory log store and public entry point — the Android counterpart of the
 * iOS `ApiLogger.shared` singleton.
 *
 * Storage is in-memory only and cleared on process death (parity with iOS).
 * All access is guarded by [lock], replacing iOS's serial dispatch queue.
 */
object ApiLogger {

    private val lock = Any()
    private val logs = ArrayList<ApiLog>()
    private val eventTrackerLog = ArrayList<ApiLog>()
    private var eventTrackerEnabled = false

    // Application captured automatically at startup (see ApiLogInitProvider) so
    // the notification can work without any host setup call.
    @Volatile
    private var appRef: Application? = null

    // Chucker-style notification; created once a context is available and enabled.
    @Volatile
    private var notifier: ApiLogNotifier? = null

    /**
     * Master switch — when false, [addLog]/[addEventTrackerLog] are no-ops.
     * Hosts typically gate this on their build type (e.g. debug only).
     */
    @Volatile
    var isEnabled: Boolean = true

    /**
     * Whether the Chucker-style log notification is shown. **Enabled by default.**
     * Set to false to suppress it (e.g. `ApiLogger.notificationsEnabled = false`);
     * set back to true to restore it. The notification still only posts when
     * [isEnabled] is true and (on Android 13+) POST_NOTIFICATIONS is granted.
     */
    @Volatile
    var notificationsEnabled: Boolean = true
        set(value) {
            field = value
            if (value) {
                appRef?.let { app -> if (notifier == null) notifier = ApiLogNotifier(app) }
            } else {
                notifier?.dismiss()
                notifier = null
            }
        }

    val isEventTrackerLogEnabled: Boolean
        get() = synchronized(lock) { eventTrackerEnabled }

    fun enableEventTrackerLog(enabled: Boolean) {
        synchronized(lock) { eventTrackerEnabled = enabled }
    }

    fun addLog(log: ApiLog) {
        if (!isEnabled) return
        synchronized(lock) { logs.add(log) }
        // Update the notification outside the lock (best-effort, never throws).
        notifier?.onTransaction(log)
    }

    fun addEventTrackerLog(log: ApiLog) {
        if (!isEnabled) return
        synchronized(lock) { eventTrackerLog.add(log) }
    }

    /** Returns a snapshot copy so callers can iterate without holding the lock. */
    fun getLogs(): List<ApiLog> = synchronized(lock) { ArrayList(logs) }

    fun getEventTrackerLogs(): List<ApiLog> = synchronized(lock) { ArrayList(eventTrackerLog) }

    fun clearLogs() {
        synchronized(lock) {
            logs.clear()
            eventTrackerLog.clear()
        }
        notifier?.dismiss()
    }

    /**
     * Installs a shake gesture that automatically opens the log inspector from
     * any screen — no Activity subclassing required by the host. Call once at
     * app startup (e.g. in your [Application.onCreate]).
     */
    fun enableShakeToOpen(application: Application) {
        ShakeDetector.install(application)
    }

    /**
     * Explicitly (re)enables the Chucker-style notification with a known
     * [Application]. Usually unnecessary — notifications are on by default and
     * the context is captured automatically at startup — but provided for hosts
     * that prefer an explicit call or toggled it off earlier.
     *
     * On Android 13+ the notification appears only if POST_NOTIFICATIONS is
     * granted (the library never crashes if it isn't).
     */
    fun enableNotifications(application: Application) {
        attachApplication(application)
        notificationsEnabled = true
    }

    /** Captures the Application context (called from ApiLogInitProvider at startup). */
    internal fun attachApplication(application: Application) {
        appRef = application
        if (notificationsEnabled && notifier == null) {
            notifier = ApiLogNotifier(application)
        }
    }
}
