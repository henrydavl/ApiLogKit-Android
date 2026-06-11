package com.henrydavl.apilogkit.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.henrydavl.apilogkit.R
import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.ui.ApiLogActivity
import java.util.concurrent.atomic.AtomicInteger

/**
 * A Chucker-style ongoing notification that summarises captured requests and
 * opens the inspector on tap. Android-only (iOS has no equivalent) and opt-in
 * via [com.henrydavl.apilogkit.model.ApiLogger.enableNotifications].
 *
 * Posting is best-effort: on Android 13+ it silently no-ops unless
 * POST_NOTIFICATIONS is granted, and never throws.
 */
internal class ApiLogNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    // Recent request summaries shown as InboxStyle lines (oldest..newest).
    private val recent = ArrayDeque<String>()
    private val total = AtomicInteger(0)

    fun onTransaction(log: ApiLog) {
        val line = summarize(log)
        synchronized(recent) {
            recent.addLast(line)
            while (recent.size > MAX_LINES) recent.removeFirst()
        }
        show(total.incrementAndGet())
    }

    fun dismiss() {
        total.set(0)
        synchronized(recent) { recent.clear() }
        runCatching { manager.cancel(NOTIFICATION_ID) }
    }

    private fun show(count: Int) {
        if (!canPost()) return
        createChannel() // lazy: only when we actually post (never in production)

        val intent = Intent(appContext, ApiLogActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val lines = synchronized(recent) { recent.toList() }
        val inbox = NotificationCompat.InboxStyle().setSummaryText("$count requests")
        // Newest first.
        lines.asReversed().forEach { inbox.addLine(it) }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_apilogkit_notification)
            .setContentTitle("API Logs")
            .setContentText(lines.lastOrNull().orEmpty())
            .setStyle(inbox)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun summarize(log: ApiLog): String =
        "${log.responseCode}  ${log.method}  ${log.url}"

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return manager.areNotificationsEnabled()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "API Logs",
                NotificationManager.IMPORTANCE_LOW, // silent, no heads-up
            ).apply {
                description = "ApiLogKit network log inspector"
                setShowBadge(false)
            }
            appContext.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private companion object {
        const val CHANNEL_ID = "apilogkit.logs"
        const val NOTIFICATION_ID = 0x10C
        const val MAX_LINES = 8
    }
}
