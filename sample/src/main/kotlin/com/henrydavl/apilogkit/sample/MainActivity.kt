package com.henrydavl.apilogkit.sample

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.Base64
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
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Date
import kotlin.random.Random

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
        binding.btnSlow.setOnClickListener { sendSlow() }
        binding.btnManual.setOnClickListener { addManualLog() }
        binding.btnManualPending.setOnClickListener { addManualPendingLog() }
        binding.btnEvent.setOnClickListener { logEvent() }
        binding.btnBase64.setOnClickListener { logBase64Upload() }
        binding.btnCrash.setOnClickListener { crashOnResponse() }
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

    /**
     * Deliberately slow so the pending row is visible for long enough to open:
     * the interceptor records the entry before the call leaves, so it shows up
     * dimmed and badged *Pending* and fills in ten seconds later.
     */
    private fun sendSlow() {
        val request = Request.Builder().url("https://httpbin.org/delay/10").build()
        client.newCall(request).enqueue(toast("Slow GET"))
        Toast.makeText(this, "In flight — open the inspector to watch it", Toast.LENGTH_SHORT).show()
    }

    /**
     * The manual counterpart of what the interceptor does, for networking it
     * can't see. Mirrors the iOS `beginLog` / `completeLog` pair.
     */
    private fun addManualPendingLog() {
        val url = "https://api.example.com/v1/checkout"
        val token = ApiLogger.beginLog(
            method = "POST",
            url = url,
            requestHeader = mapOf("Authorization" to "Bearer xyz"),
            requestBody = mapOf("cart" to "item123", "amount" to 5000),
        )
        Toast.makeText(this, "Pending for 6s — open the inspector", Toast.LENGTH_SHORT).show()

        binding.root.postDelayed({
            ApiLogger.completeLog(
                token,
                ApiLog(
                    responseCode = "201",
                    method = "POST",
                    url = url,
                    responseTime = "6.00",
                    size = "64",
                    date = Date(),
                    responseHeader = mapOf("Content-Type" to "application/json"),
                    responseBody = """{"order":"A-991","status":"confirmed"}""",
                    requestHeader = mapOf("Authorization" to "Bearer xyz"),
                    requestBody = mapOf("cart" to "item123", "amount" to 5000),
                ),
            )
        }, 6_000)
    }

    /**
     * The scenario crash capture exists for: a real request comes back, the
     * parsing code chokes on the payload, and the app dies before the log has
     * finished being written.
     *
     * Reproduces it faithfully by crashing *in the response callback* — the
     * interceptor has already recorded the exchange, but the write is still
     * queued on the store's background thread, so without the crash handler
     * flushing it the entry would be lost along with the process.
     *
     * Relaunch the app afterwards and open the inspector: the previous session's
     * crash is announced at the top of the list, and the response below it is
     * badged CRASH with the stack trace above its body.
     */
    private fun crashOnResponse() {
        Toast.makeText(this, "Crashing on the response — then relaunch", Toast.LENGTH_SHORT).show()
        val request = Request.Builder().url("https://httpbin.org/get?crash=now").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = runOnUiThread {
                Toast.makeText(this@MainActivity, "Request failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                // On the main thread, exactly where a mapping failure would land.
                runOnUiThread {
                    throw IllegalStateException(
                        "Expected BEGIN_OBJECT but was STRING at \$.data.outlet[0].id",
                    )
                }
            }
        })
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

    /**
     * A form-encoded upload whose single field is a JSON *string* carrying
     * base64 photos — the shape that used to render as one long line, and whose
     * inline-expanded photo could crash the inspector on low-memory phones.
     */
    private fun logBase64Upload() {
        val storePhoto = photoBase64(Color.rgb(0, 150, 136), seed = 1)
        val productPhoto = photoBase64(Color.rgb(233, 30, 99), seed = 2)
        val payload = JSONObject()
            .put("name", "Warung Kopi Demo")
            .put("kota", "JAKARTA SELATAN")
            .put("latitude", -6.2142)
            .put("longitude", 106.8372)
            .put("photo_store", storePhoto)
            .put("photo_product", productPhoto)
            .toString()

        ApiLogger.addLog(
            ApiLog(
                responseCode = "200",
                method = "POST",
                url = "https://api.example.com/v1/onboarding/outlet",
                responseTime = "1.20",
                size = "${storePhoto.length}",
                date = Date(),
                responseHeader = mapOf("Content-Type" to "application/json"),
                responseBody = JSONObject()
                    .put("code", "00")
                    .put("data", JSONObject().put("receipt_image", storePhoto))
                    .toString(),
                requestHeader = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                requestBody = mapOf("request" to payload),
            ),
        )
        Toast.makeText(this, "Upload logged (${payload.length / 1024} KB body)", Toast.LENGTH_SHORT).show()
    }

    /** A noisy JPEG, so it stays photo-sized after compression, as base64. */
    private fun photoBase64(tint: Int, seed: Int): String {
        val bitmap = Bitmap.createBitmap(720, 960, Bitmap.Config.ARGB_8888)
        val random = Random(seed)
        val pixels = IntArray(bitmap.width * bitmap.height) {
            val noise = random.nextInt(-40, 40)
            Color.rgb(
                (Color.red(tint) + noise).coerceIn(0, 255),
                (Color.green(tint) + noise).coerceIn(0, 255),
                (Color.blue(tint) + noise).coerceIn(0, 255),
            )
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        Canvas(bitmap).drawCircle(360f, 420f, 220f, Paint().apply { color = Color.WHITE })

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
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
