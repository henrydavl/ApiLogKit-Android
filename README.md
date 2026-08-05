# ApiLogKit (Android)

[![](https://jitpack.io/v/henrydavl/ApiLogKit-Android.svg)](https://jitpack.io/#henrydavl/ApiLogKit-Android)

An in-app API log inspector for Android, written in Jetpack Compose — the Android counterpart of
[ApiLogKit for iOS](https://github.com/henrydavl/ApiLogKit). It records HTTP request/response logs
(plus analytics events such as an EventTracker) and presents them in a debug UI with:

- 📋 Log list with URL search, status-code badges, newest-first ordering
- ⚡ **Live list** — requests appear as they happen, with a pause button so reading a log isn't
  disturbed by incoming traffic
- 🌳 Interactive JSON viewer — collapsible objects/arrays with child counts, type-colored values,
  tap-to-expand long strings (base64-safe), expand/collapse all
- 📝 Tree ⇄ pretty-JSON text toggle per body section
- 📤 Export as raw log or ready-to-run cURL command
- 📎 Copy any value, subtree, or section with toast confirmation
- 🧭 Floating scroll-to-top/bottom buttons on long payloads
- 📳 Shake to open — one-line setup, works from any screen, no boilerplate
- 🔌 Drop-in **OkHttp interceptor** for automatic capture (a Chucker replacement), plus a manual
  API that mirrors iOS
- 💾 Optional disk persistence — logs survive the app being killed, so you can export or compare
  them on a later launch

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

**Notification (Chucker-style)** — an ongoing notification summarises captured requests and opens
the inspector on tap. It is **on by default** (no setup needed — the library captures the app
context automatically at startup). Toggle it any time:

```kotlin
ApiLogger.notificationsEnabled = false   // suppress; set true to restore
```

It only posts when `ApiLogger.isEnabled` is true, and on Android 13+ only once `POST_NOTIFICATIONS`
is granted — request it from your app (the library declares the permission and never crashes if it
isn't granted).

### 3. Live updates and pause

The list is reactive. `ApiLogger` publishes its buckets as `StateFlow`s — the Android counterpart of
the Combine publishers on iOS — so requests captured while the inspector is open appear immediately;
no closing and reopening to see new traffic.

Because a busy app can push rows past you while you're reading, the toolbar has a **pause** button.
While paused the logger keeps collecting, the list simply stops refreshing, and a banner shows how
many entries are waiting:

```
⏸  Paused — 12 new                              Resume
```

Resuming folds them all in at once. Search is debounced by 250 ms, so typing filters once rather than
on every keystroke.

You can observe the same streams from your own code:

```kotlin
lifecycleScope.launch {
    ApiLogger.logsFlow.collect { logs -> /* … */ }
}
```

Both flows replay their current value on collection, so a fresh collector fills straight away.

### 4. `ApiLogKitConfig.Persistence` — keep logs across app restarts (optional)

By default logs live in memory only and are gone once the host process dies — matching iOS. Opt into
disk persistence and they are mirrored to an app-private SQLite database and read back on the next
launch, so you can still export them, or compare an old response against a later call to the same
endpoint.

**Enable it once, at startup:**

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            ApiLogKitConfig.persistence = ApiLogKitConfig.Persistence(maxEntries = 500)
        }
    }
}
```

That single line is the whole setup — no database to create, no permissions, no extra dependency.

**The parameter:**

| Parameter | Default | Meaning |
| --- | --- | --- |
| `maxEntries` | `500` | How many of the newest entries to keep on disk, counted **separately** for API logs and EventTracker logs. Older rows are pruned automatically. |

Restored entries are also held in memory, so a very large cap combined with large response bodies
costs heap on the next launch. `ApiLogKitConfig.Persistence.DEFAULT_MAX_ENTRIES` exposes the default
if you want to reference it.

**Turning it off:**

```kotlin
ApiLogKitConfig.persistence = null   // stops writing; keeps what is already stored
ApiLogger.clearLogs()                // wipes memory and disk together
```

**From Java, or without the config object:**

```java
ApiLogger.INSTANCE.enablePersistence(context, 500);
ApiLogger.INSTANCE.disablePersistence();
```

`ApiLogger.enablePersistence(context, maxEntries)` is the explicit equivalent — useful if your app
strips `ApiLogInitProvider` and so has no automatically captured `Application` context. You can also
read `ApiLogger.isPersistenceEnabled` to check the current state.

**What to expect:**

- Entries from earlier runs are labelled **EARLIER SESSION** in the list, so previous runs stay
  distinguishable from the current one. They load in the background and appear without reopening
  the inspector.
- Everything is opt-in and off by default, so upgrading changes nothing until you set the flag.
- All database work runs on a background thread and is best-effort; a failed write never surfaces to
  your app.
- It is plain `SQLiteOpenHelper`, **not Room** — a debug library shouldn't drag androidx.room and KSP
  onto your classpath.
- The database is dropped and recreated on schema upgrades. Debug logs are disposable.

> ⚠️ **Leave this off in release builds.** It writes request and response bodies — and therefore any
> auth tokens or personal data they contain — to app-private storage.

> ℹ️ There is no way to keep ApiLogKit *running* once the host app is killed. A separate process still
> belongs to the same package, so a force-stop or a swipe from recents takes it down too. Persisting
> to disk is how logs survive process death.

### 5. Optional configuration

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

The Android library is a faithful port of the iOS one, including the live list: iOS backs its store
with Combine `CurrentValueSubject`s, Android with `StateFlow`, and both replay their current value so
a freshly opened inspector fills immediately. Pause/resume, the pending-count banner and the 250 ms
search debounce behave the same on both.

Two Android-only additions: an `ApiLogInterceptor` for OkHttp (the idiomatic auto-capture path that
replaces Chucker; iOS captures manually only), and opt-in disk persistence. Storage is in-memory on
both platforms and cleared on process death unless `ApiLogKitConfig.persistence` is set. Export
formats are identical either way.

Not yet ported from iOS: the **3rd-party tracker** bucket, which captures `URLSession` traffic from
closed-source SDKs.

## License

MIT
