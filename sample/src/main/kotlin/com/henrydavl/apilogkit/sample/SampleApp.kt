package com.henrydavl.apilogkit.sample

import android.app.Application
import android.content.Intent
import com.henrydavl.apilogkit.ApiLogKitConfig
import com.henrydavl.apilogkit.model.ApiLogger
import okhttp3.OkHttpClient

class SampleApp : Application() {

    // A single shared client with the interceptor installed — auto-capture.
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(com.henrydavl.apilogkit.interceptor.ApiLogInterceptor())
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // Gate recording on debug builds, then enable shake-to-open.
        // The Chucker-style notification is ON by default (no call needed);
        // toggle it with ApiLogger.notificationsEnabled = false.
        ApiLogger.isEnabled = BuildConfig.DEBUG
        ApiLogger.enableShakeToOpen(this)

        // Enable the separate analytics ("EventTracker") tab.
        ApiLogger.enableEventTrackerLog(true)

        // Plug the host's own XML-based Developer Options screen into the menu.
        ApiLogKitConfig.developerOptions = ApiLogKitConfig.DeveloperOptions(
            label = "Developer Options",
        ) { context ->
            context.startActivity(Intent(context, DevOptionsActivity::class.java))
        }
    }
}
