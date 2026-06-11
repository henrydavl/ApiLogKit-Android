package com.henrydavl.apilogkit.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.henrydavl.apilogkit.ApiLogInspector
import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogger
import com.henrydavl.apilogkit.sample.databinding.ActivityMainBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Date

/**
 * Plain XML/View Activity (no Compose) — demonstrates that an XML-based host can
 * fully drive ApiLogKit: auto-capture via the interceptor, the manual API, the
 * EventTracker tab, shake-to-open, and the XML Developer Options screen.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val client get() = (application as SampleApp).httpClient

    // Required on Android 13+ for the Chucker-style log notification to appear.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()

        binding.btnGet.setOnClickListener { sendGet() }
        binding.btnPost.setOnClickListener { sendPost() }
        binding.btnManual.setOnClickListener { addManualLog() }
        binding.btnEvent.setOnClickListener { logEvent() }
        binding.btnOpen.setOnClickListener { ApiLogInspector.launch(this) }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun sendGet() {
        val request = Request.Builder().url("https://httpbin.org/get?source=apilogkit").build()
        client.newCall(request).enqueue(toast("GET"))
    }

    private fun sendPost() {
        val body = """{"product":"item123","amount":5000}"""
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://httpbin.org/post")
            .header("X-Demo", "ApiLogKit")
            .post(body)
            .build()
        client.newCall(request).enqueue(toast("POST"))
    }

    private fun addManualLog() {
        ApiLogger.addLog(
            ApiLog(
                responseCode = "200",
                method = "POST",
                url = "https://api.example.com/v1/login",
                responseTime = "0.42",
                size = "2048",
                date = Date(),
                responseHeader = mapOf("Content-Type" to "application/json"),
                responseBody = """{"token":"abc123","user":{"id":7,"name":"Henry"}}""",
                requestHeader = mapOf("Authorization" to "Bearer xyz"),
                requestBody = mapOf("username" to "henry", "password" to "secret"),
            ),
        )
        Toast.makeText(this, "Manual log added — shake or Open inspector", Toast.LENGTH_SHORT).show()
    }

    private fun logEvent() {
        ApiLogger.addEventTrackerLog(
            ApiLog.event(
                eventName = "purchase_completed",
                requestBody = mapOf("product" to "item123", "amount" to 5000),
                responseBody = """{"status":"success"}""",
            ),
        )
        Toast.makeText(this, "Event logged — see EventTracker tab", Toast.LENGTH_SHORT).show()
    }

    private fun toast(label: String) = object : Callback {
        override fun onFailure(call: Call, e: IOException) = runOnUiThread {
            Toast.makeText(this@MainActivity, "$label failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        override fun onResponse(call: Call, response: Response) {
            response.close()
            runOnUiThread {
                Toast.makeText(this@MainActivity, "$label captured — shake to inspect", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
