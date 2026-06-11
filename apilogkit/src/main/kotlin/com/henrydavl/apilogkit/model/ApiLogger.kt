package com.henrydavl.apilogkit.model

import android.app.Application
import com.henrydavl.apilogkit.ShakeDetector

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

    /**
     * Master switch — when false, [addLog]/[addEventTrackerLog] are no-ops.
     * Hosts typically gate this on their build type (e.g. debug only).
     */
    @Volatile
    var isEnabled: Boolean = true

    val isEventTrackerLogEnabled: Boolean
        get() = synchronized(lock) { eventTrackerEnabled }

    fun enableEventTrackerLog(enabled: Boolean) {
        synchronized(lock) { eventTrackerEnabled = enabled }
    }

    fun addLog(log: ApiLog) {
        if (!isEnabled) return
        synchronized(lock) { logs.add(log) }
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
    }

    /**
     * Installs a shake gesture that automatically opens the log inspector from
     * any screen — no Activity subclassing required by the host. Call once at
     * app startup (e.g. in your [Application.onCreate]).
     */
    fun enableShakeToOpen(application: Application) {
        ShakeDetector.install(application)
    }
}
