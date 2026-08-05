package com.henrydavl.apilogkit.persistence

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.LogEventType
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Disk-backed store that lets captured logs outlive the host process.
 *
 * Android gives no way to keep a component running once the host app is killed —
 * a separate process still belongs to the same package, so a force-stop or a
 * swipe from recents takes it down too. Surviving process death therefore means
 * *persisting*, not staying alive: entries are written to an app-private SQLite
 * database and read back on the next launch, so an export or a comparison
 * against a later call to the same endpoint is still possible tomorrow.
 *
 * Deliberately built on plain [SQLiteOpenHelper] rather than Room: ApiLogKit is
 * a debug dependency and should not drag androidx.room + KSP onto every host
 * app's classpath.
 *
 * All database work happens on a single background thread ([io]), which both
 * keeps I/O off the main thread and serialises access without extra locking.
 * Every operation is best-effort — a failed write must never take down the host
 * app for the sake of a debug log.
 */
internal class ApiLogStore(
    context: Context,
    /** Newest-N entries retained *per* [LogEventType]; older rows are pruned. */
    val maxEntries: Int,
) {

    private val helper = Helper(context.applicationContext)
    private val io: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ApiLogKit-Store").apply { isDaemon = true }
    }

    /** Inserts since the last prune; see [PRUNE_INTERVAL]. */
    private var insertsSincePrune = 0

    /** Queues [log] for insertion. Returns immediately; never throws. */
    fun insert(log: ApiLog, type: LogEventType) {
        submit {
            val values = ContentValues().apply {
                put(COL_TYPE, type.name)
                put(COL_RESPONSE_CODE, log.responseCode)
                put(COL_METHOD, log.method)
                put(COL_URL, log.url)
                put(COL_RESPONSE_TIME, log.responseTime)
                put(COL_SIZE, log.size)
                put(COL_DATE, log.date.time)
                put(COL_RESPONSE_HEADER, log.responseHeader.encodeApiMap())
                put(COL_RESPONSE_BODY, log.responseBody)
                put(COL_REQUEST_HEADER, log.requestHeader.encodeApiMap())
                put(COL_REQUEST_BODY, log.requestBody.encodeApiMap())
            }
            helper.writableDatabase.insert(TABLE, null, values)

            // Pruning is throttled rather than run per insert: the table may sit
            // slightly above maxEntries between prunes, which costs a little disk
            // but avoids a scan on every captured request.
            if (++insertsSincePrune >= PRUNE_INTERVAL) {
                insertsSincePrune = 0
                prune(helper.writableDatabase, type)
            }
        }
    }

    /**
     * Reads everything already on disk — by construction that is only entries
     * from *earlier* runs, since this is submitted before the store starts
     * accepting inserts — and hands them to [onLoaded] on the store's own
     * background thread, oldest first.
     */
    fun loadAsync(onLoaded: (api: List<ApiLog>, events: List<ApiLog>) -> Unit) {
        submit {
            val db = helper.writableDatabase
            // Trim anything left over the cap by a previous run before reading,
            // so we never pull more into memory than the host asked for.
            LogEventType.entries.forEach { prune(db, it) }
            onLoaded(read(db, LogEventType.API), read(db, LogEventType.EVENT_TRACKER))
        }
    }

    /** Queues deletion of every persisted entry. */
    fun clear() {
        submit { helper.writableDatabase.delete(TABLE, null, null) }
    }

    /** Stops accepting work and closes the database. Queued writes are dropped. */
    fun close() {
        io.shutdownNow()
        runCatching { helper.close() }
    }

    private fun submit(block: () -> Unit) {
        runCatching {
            io.execute { runCatching(block) }
        }
    }

    private fun read(db: SQLiteDatabase, type: LogEventType): List<ApiLog> {
        val logs = ArrayList<ApiLog>()
        // Ascending id == chronological, matching the in-memory list's ordering.
        db.query(
            TABLE,
            null,
            "$COL_TYPE = ?",
            arrayOf(type.name),
            null,
            null,
            "$COL_ID ASC",
        ).use { cursor ->
            val responseCode = cursor.getColumnIndexOrThrow(COL_RESPONSE_CODE)
            val method = cursor.getColumnIndexOrThrow(COL_METHOD)
            val url = cursor.getColumnIndexOrThrow(COL_URL)
            val responseTime = cursor.getColumnIndexOrThrow(COL_RESPONSE_TIME)
            val size = cursor.getColumnIndexOrThrow(COL_SIZE)
            val date = cursor.getColumnIndexOrThrow(COL_DATE)
            val responseHeader = cursor.getColumnIndexOrThrow(COL_RESPONSE_HEADER)
            val responseBody = cursor.getColumnIndexOrThrow(COL_RESPONSE_BODY)
            val requestHeader = cursor.getColumnIndexOrThrow(COL_REQUEST_HEADER)
            val requestBody = cursor.getColumnIndexOrThrow(COL_REQUEST_BODY)

            while (cursor.moveToNext()) {
                logs.add(
                    ApiLog(
                        responseCode = cursor.getString(responseCode),
                        method = cursor.getString(method),
                        url = cursor.getString(url),
                        responseTime = cursor.getString(responseTime),
                        size = cursor.getString(size),
                        date = Date(cursor.getLong(date)),
                        responseHeader = cursor.getString(responseHeader).decodeApiMap(),
                        responseBody = cursor.getString(responseBody),
                        requestHeader = cursor.getString(requestHeader).decodeApiMap(),
                        requestBody = cursor.getString(requestBody).decodeApiMap(),
                        fromPreviousSession = true,
                    ),
                )
            }
        }
        return logs
    }

    /** Keeps only the newest [maxEntries] rows of [type]. */
    private fun prune(db: SQLiteDatabase, type: LogEventType) {
        db.delete(
            TABLE,
            "$COL_TYPE = ? AND $COL_ID NOT IN " +
                "(SELECT $COL_ID FROM $TABLE WHERE $COL_TYPE = ? ORDER BY $COL_ID DESC LIMIT ?)",
            arrayOf(type.name, type.name, maxEntries.toString()),
        )
    }

    private class Helper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_TYPE TEXT NOT NULL,
                    $COL_RESPONSE_CODE TEXT NOT NULL,
                    $COL_METHOD TEXT NOT NULL,
                    $COL_URL TEXT NOT NULL,
                    $COL_RESPONSE_TIME TEXT NOT NULL,
                    $COL_SIZE TEXT NOT NULL,
                    $COL_DATE INTEGER NOT NULL,
                    $COL_RESPONSE_HEADER TEXT NOT NULL,
                    $COL_RESPONSE_BODY TEXT NOT NULL,
                    $COL_REQUEST_HEADER TEXT NOT NULL,
                    $COL_REQUEST_BODY TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX idx_apilog_type_id ON $TABLE ($COL_TYPE, $COL_ID)")
            // Supports looking back at earlier calls to the same endpoint.
            db.execSQL("CREATE INDEX idx_apilog_url ON $TABLE ($COL_URL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Debug logs are disposable: recreate rather than carry migrations.
            db.execSQL("DROP TABLE IF EXISTS $TABLE")
            onCreate(db)
        }

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            onUpgrade(db, oldVersion, newVersion)
    }

    private companion object {
        const val DB_NAME = "apilogkit-logs.db"
        const val DB_VERSION = 1
        const val TABLE = "api_logs"
        const val PRUNE_INTERVAL = 25

        const val COL_ID = "id"
        const val COL_TYPE = "type"
        const val COL_RESPONSE_CODE = "response_code"
        const val COL_METHOD = "method"
        const val COL_URL = "url"
        const val COL_RESPONSE_TIME = "response_time"
        const val COL_SIZE = "size"
        const val COL_DATE = "date"
        const val COL_RESPONSE_HEADER = "response_header"
        const val COL_RESPONSE_BODY = "response_body"
        const val COL_REQUEST_HEADER = "request_header"
        const val COL_REQUEST_BODY = "request_body"
    }
}

/**
 * Serialises a header/body map as a JSON *array of pairs* rather than a JSON
 * object. [ApiLog]'s maps are ordered (`LinkedHashMap`) and the detail screen
 * renders them in order, but a JSON object's key order is not guaranteed to
 * survive a round trip across org.json implementations. An array does.
 */
internal fun Map<String, Any?>.encodeApiMap(): String {
    val array = JSONArray()
    for ((key, value) in this) {
        array.put(JSONObject().put("k", key).put("v", value.toJsonValue()))
    }
    return array.toString()
}

/**
 * Coerces to something org.json round-trips faithfully. Values reaching here can
 * be anything the host handed to `ApiLogger.addLog`, so unknown types fall back
 * to their string form instead of being written as an opaque `toString()` by the
 * serialiser.
 */
private fun Any?.toJsonValue(): Any = when (this) {
    null -> JSONObject.NULL
    is String, is Boolean, is Int, is Long, is Double, is Float,
    is JSONObject, is JSONArray,
    -> this
    else -> toString()
}

internal fun String.decodeApiMap(): Map<String, Any?> {
    val map = LinkedHashMap<String, Any?>()
    runCatching {
        val array = JSONArray(this)
        for (i in 0 until array.length()) {
            val pair = array.getJSONObject(i)
            val value = pair.get("v")
            map[pair.getString("k")] = if (value == JSONObject.NULL) null else value
        }
    }
    return map
}
