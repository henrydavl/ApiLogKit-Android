package com.henrydavl.apilogkit.model

import android.app.Application
import android.content.Context
import com.henrydavl.apilogkit.ApiLogKitConfig
import com.henrydavl.apilogkit.ShakeDetector
import com.henrydavl.apilogkit.notification.ApiLogNotifier
import com.henrydavl.apilogkit.persistence.ApiLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Log store and public entry point — the Android counterpart of the iOS
 * `ApiLogger.shared` singleton.
 *
 * Storage is in-memory by default and cleared on process death (parity with
 * iOS). Hosts that need logs to outlive the process can opt into disk
 * persistence via [ApiLogKitConfig.persistence] / [enablePersistence]; entries
 * are then mirrored to an app-private SQLite database and read back on the next
 * launch. All in-memory access is guarded by [lock], replacing iOS's serial
 * dispatch queue.
 *
 * Storage is reactive: [logsFlow] and [eventTrackerLogsFlow] emit on every
 * change, so an open inspector updates live instead of showing the snapshot it
 * was opened with. These are the Android counterpart of iOS's Combine
 * publishers, and hosts can collect them too.
 */
object ApiLogger {

    private val lock = Any()

    // Backing storage lives inside StateFlows so observers (e.g. the log list)
    // can react to new entries in real time — the Android counterpart of iOS's
    // Combine `CurrentValueSubject`. All mutation is read-modify-write, hence
    // [lock]; StateFlow itself is safe to *read* from any thread.
    private val _logsFlow = MutableStateFlow<List<ApiLog>>(emptyList())
    private val _eventTrackerLogsFlow = MutableStateFlow<List<ApiLog>>(emptyList())

    private var eventTrackerEnabled = false

    /**
     * Emits the full API log list whenever it changes, replaying the current
     * value to new collectors so a freshly opened inspector fills immediately.
     * Mirrors iOS's `logsPublisher`.
     */
    val logsFlow: StateFlow<List<ApiLog>> = _logsFlow.asStateFlow()

    /** Emits the full EventTracker log list whenever it changes. */
    val eventTrackerLogsFlow: StateFlow<List<ApiLog>> = _eventTrackerLogsFlow.asStateFlow()

    // Application captured automatically at startup (see ApiLogInitProvider) so
    // the notification can work without any host setup call.
    @Volatile
    private var appRef: Application? = null

    // Chucker-style notification; created once a context is available and enabled.
    @Volatile
    private var notifier: ApiLogNotifier? = null

    // Disk persistence; null unless the host opted in.
    @Volatile
    private var store: ApiLogStore? = null

    // Set when persistence is requested before the Application context exists,
    // so the request can be honoured once ApiLogInitProvider attaches it.
    @Volatile
    private var pendingPersistence: ApiLogKitConfig.Persistence? = null

    // Previously persisted entries are merged into memory at most once per
    // process, so an enable/disable/enable cycle cannot duplicate them.
    private var hasRestored = false

    // Set when the logs are cleared before a queued restore has run; guarded by [lock].
    private var restoreCancelled = false

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

    /** True when captured logs are being mirrored to disk. */
    val isPersistenceEnabled: Boolean
        get() = store != null

    fun addLog(log: ApiLog) {
        if (!isEnabled) return
        synchronized(lock) { _logsFlow.value = _logsFlow.value + log }
        // Persist and update the notification outside the lock (both are
        // best-effort and never throw; the store writes on its own thread).
        store?.insert(log, LogEventType.API)
        notifier?.onTransaction(log)
    }

    fun addEventTrackerLog(log: ApiLog) {
        if (!isEnabled) return
        synchronized(lock) { _eventTrackerLogsFlow.value = _eventTrackerLogsFlow.value + log }
        store?.insert(log, LogEventType.EVENT_TRACKER)
    }

    /**
     * Current API logs. The flow's value is already an immutable snapshot, so
     * callers can iterate it freely; prefer [logsFlow] to observe changes.
     */
    fun getLogs(): List<ApiLog> = _logsFlow.value

    fun getEventTrackerLogs(): List<ApiLog> = _eventTrackerLogsFlow.value

    /** Empties the in-memory logs and, when persistence is on, the stored ones too. */
    fun clearLogs() {
        synchronized(lock) {
            _logsFlow.value = emptyList()
            _eventTrackerLogsFlow.value = emptyList()
            // A restore queued at startup may not have run yet; without this it
            // would repopulate the list moments after the user cleared it, with
            // entries whose rows are about to be deleted anyway.
            restoreCancelled = true
        }
        store?.clear()
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

    /**
     * Turns on disk persistence with an explicit [Context] — the imperative
     * equivalent of setting [ApiLogKitConfig.persistence], mirroring how
     * [enableNotifications] complements [notificationsEnabled]. Useful for hosts
     * that removed `ApiLogInitProvider` and so have no auto-captured context.
     *
     * Call it at startup. Entries stored by earlier runs are read back on a
     * background thread and merged into the list, flagged as
     * [ApiLog.fromPreviousSession].
     */
    @JvmOverloads
    fun enablePersistence(
        context: Context,
        maxEntries: Int = ApiLogKitConfig.Persistence.DEFAULT_MAX_ENTRIES,
    ) {
        openStore(context, maxEntries)
    }

    /**
     * Stops mirroring new logs to disk. Already-stored entries are kept — use
     * [clearLogs] to remove them.
     */
    fun disablePersistence() {
        synchronized(lock) {
            store?.close()
            store = null
        }
    }

    /** Applies a declarative [ApiLogKitConfig.persistence] change. */
    internal fun applyPersistence(config: ApiLogKitConfig.Persistence?) {
        if (config == null) {
            pendingPersistence = null
            disablePersistence()
            return
        }
        val application = appRef
        if (application == null) {
            // ApiLogInitProvider has not run yet (or was removed); honour the
            // request as soon as a context arrives.
            pendingPersistence = config
            return
        }
        openStore(application, config.maxEntries)
    }

    private fun openStore(context: Context, maxEntries: Int) {
        synchronized(lock) {
            val existing = store
            if (existing != null) {
                if (existing.maxEntries == maxEntries) return
                // Reopen so the new retention cap takes effect.
                existing.close()
                store = null
            }

            val opened = ApiLogStore(context, maxEntries)
            if (!hasRestored) {
                hasRestored = true
                // Queued before the store is published below, so nothing captured
                // in this process can be read back and mislabelled as older.
                opened.loadAsync { restoredApi, restoredEvents ->
                    synchronized(lock) {
                        if (!restoreCancelled) {
                            // Prepended: restored entries predate anything captured
                            // in this process, and the list is kept chronological.
                            // Emitting here means an already-open inspector picks
                            // the restored entries up without being reopened.
                            _logsFlow.value = restoredApi + _logsFlow.value
                            _eventTrackerLogsFlow.value = restoredEvents + _eventTrackerLogsFlow.value
                        }
                    }
                }
            }
            store = opened
        }
    }

    /** Captures the Application context (called from ApiLogInitProvider at startup). */
    internal fun attachApplication(application: Application) {
        appRef = application
        if (notificationsEnabled && notifier == null) {
            notifier = ApiLogNotifier(application)
        }
        pendingPersistence?.let { config ->
            pendingPersistence = null
            openStore(application, config.maxEntries)
        }
    }
}
