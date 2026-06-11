# ApiLogKit (Android)

An in-app API log inspector for Android, written in Jetpack Compose — the Android counterpart of
[ApiLogKit for iOS](https://github.com/henrydavl/ApiLogKit). It records HTTP request/response logs
(plus analytics events such as an EventTracker) and presents them in a debug UI with:

- 📋 Log list with URL search, status-code badges, newest-first ordering
- 🌳 Interactive JSON viewer — collapsible objects/arrays with child counts, type-colored values,
  tap-to-expand long strings (base64-safe), expand/collapse all
- 📝 Tree ⇄ pretty-JSON text toggle per body section
- 📤 Export as raw log or ready-to-run cURL command
- 📎 Copy any value, subtree, or section with toast confirmation
- 🧭 Floating scroll-to-top/bottom buttons on long payloads
- 📳 Shake to open — one-line setup, works from any screen, no boilerplate
- 🔌 Drop-in **OkHttp interceptor** for automatic capture (a Chucker replacement), plus a manual
  API that mirrors iOS

It is designed to be the **same inspector on both platforms**: the data model, export formats, and
UX match the iOS library so behavior is consistent across Android and iOS.

Requires **Android 7.0 (API 24)+**. Built with Jetpack Compose, but **fully consumable from
XML/View-based apps** — the inspector launches as its own Activity (your app needs no Compose), and
the Developer Options hook lets you plug in a plain XML screen.

## Installation

The library lives in the `:apilogkit` module. Consume it as a project module, or publish it (it is
JitPack-friendly):

```kotlin
// settings.gradle.kts — dependencyResolutionManagement { repositories { ... } }
maven { url = uri("https://jitpack.io") }
```

```kotlin
// app/build.gradle.kts — NOTE the multi-module coordinate:
// group is com.github.<user>.<repo>, artifact is the module name (apilogkit).
dependencies {
    implementation("com.github.henrydavl.ApiLogKit-Android:apilogkit:<tag>")

    // Only if you use the OkHttp interceptor (ApiLogKit declares OkHttp as compileOnly):
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

## Usage

### 1. Record logs

**Automatically, via the OkHttp interceptor (recommended — replaces Chucker):**

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(ApiLogInterceptor())
    .build()
```

Every request/response through that client is captured. Capture is skipped entirely when
`ApiLogger.isEnabled` is false, so it's safe to leave installed and gate on build type.

**Manually (mirrors the iOS API):**

```kotlin
// Gate recording (e.g. debug builds only). Defaults to true.
ApiLogger.isEnabled = BuildConfig.DEBUG

ApiLogger.addLog(
    ApiLog(
        responseCode = "200",
        method = "POST",
        url = "https://api.example.com/v1/login",
        responseTime = "0.42",
        size = "2048",
        date = Date(),
        responseHeader = mapOf("Content-Type" to "application/json"),
        responseBody = bodyString,
        requestHeader = requestHeaders,
        requestBody = requestParameters,
    )
)
```

### 2. Open the inspector

**Shake to open (recommended)** — call once at startup (e.g. in your `Application.onCreate`) and the
inspector appears on any shake, from any screen, with no further setup:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiLogger.isEnabled = BuildConfig.DEBUG
        ApiLogger.enableShakeToOpen(this)
    }
}
```

**Manually** — launch it yourself from anywhere (e.g. a debug button), no Compose required:

```kotlin
ApiLogInspector.launch(context)
```

### 3. Optional configuration

```kotlin
// Locale for row timestamps (defaults to the system locale).
ApiLogKitConfig.dateLocale = Locale("id", "ID")

// Plug in your own Developer Options screen — works with plain XML Activities,
// since dev options are often still built in XML.
ApiLogKitConfig.developerOptions = ApiLogKitConfig.DeveloperOptions(
    label = "Developer Options",
) { ctx ->
    ctx.startActivity(Intent(ctx, MyDevOptionsActivity::class.java))
}

// Track analytics events in a separate "EventTracker" tab.
ApiLogger.enableEventTrackerLog(true)
ApiLogger.addEventTrackerLog(
    ApiLog.event(
        eventName = "purchase_completed",
        requestBody = mapOf("product" to "item123", "amount" to 5000),
        responseBody = "{\"status\":\"success\"}",
    )
)
```

## Sample app

The `:sample` module is intentionally a **plain XML/View app (no Compose)** to demonstrate that a
non-Compose host can fully drive ApiLogKit — auto-capture, the manual API, the EventTracker tab,
shake-to-open, and an XML Developer Options screen. Run it with:

```bash
./gradlew :sample:installDebug
```

## How it compares to iOS

The Android library is a faithful port of the iOS one. The core difference: iOS captures logs
manually only, while Android additionally ships an `ApiLogInterceptor` for OkHttp (the idiomatic
auto-capture path that replaces Chucker). Storage is in-memory on both platforms and cleared onprocess death.

## License

MIT
