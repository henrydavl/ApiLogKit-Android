package com.henrydavl.apilogkit.persistence

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.henrydavl.apilogkit.model.ApiLog
import com.henrydavl.apilogkit.model.ApiLogState
import com.henrydavl.apilogkit.model.CrashReport
import com.henrydavl.apilogkit.model.LogEventType
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
 * app for the sake of a debug log. The one exception is
 * [recordCrashBlocking], which has to block: see its documentation.
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
            val db = helper.writableDatabase
            db.insert(TABLE, null, log.toValues(type))

            // Pruning is throttled rather than run per insert: the table may sit
            // slightly above maxEntries between prunes, which costs a little disk
            // but avoids a scan on every captured request.
            if (++insertsSincePrune >= PRUNE_INTERVAL) {
                insertsSincePrune = 0
                prune(db, type)
            }
        }
    }

    /**
     * Queues an in-place update of the row [log] was first inserted as — the
     * counterpart of [insert] for an entry that was stored while still pending
     * and has now completed.
     *
     * Falls back to an insert when no row matches, which happens when the
     * pending row has already been pruned out from under us, or when persistence
     * was switched on between the request going out and the response landing.
     */
    fun update(log: ApiLog, type: LogEventType) {
        submit {
            val db = helper.writableDatabase
            val updated = db.update(
                TABLE,
                log.toValues(type),
                "$COL_SESSION = ? AND $COL_LOG_ID = ?",
                arrayOf(SESSION_ID, log.id.toString()),
            )
            if (updated == 0) db.insert(TABLE, null, log.toValues(type))
        }
    }

    /**
     * Writes [report] and tags [suspectLogIds] with it — **blocking** the calling
     * thread until it is on disk, or [CRASH_FLUSH_TIMEOUT_MS] elapses.
     *
     * Blocking is the whole point. This is called from the uncaught-exception
     * handler, and the process is about to be killed: the usual fire-and-forget
     * write would be queued behind the very inserts we are trying to save and
     * then die with the queue. Because [io] is single-threaded and FIFO, awaiting
     * *this* task also guarantees every insert queued before it has landed — so
     * the response that caused the crash is on disk, not just the crash.
     *
     * The timeout bounds the wait: the app is already dying, and an export is
     * worth a second or two of delay but not an indefinite hang in a crash
     * handler.
     */
    fun recordCrashBlocking(report: CrashReport, suspectLogIds: List<Long>) {
        val future = runCatching {
            io.submit {
                runCatching {
                    val db = helper.writableDatabase
                    val crashId = db.insert(CRASH_TABLE, null, report.toValues())
                    if (crashId != -1L && suspectLogIds.isNotEmpty()) {
                        tagSuspects(db, crashId, suspectLogIds)
                    }
                    pruneCrashes(db)
                }
            }
        }.getOrNull() ?: return

        // get() rethrows anything the task threw; it can't, but a timeout can.
        runCatching { future.get(CRASH_FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
    }

    /**
     * Reads everything already on disk — by construction that is only entries
     * from *earlier* runs, since this is submitted before the store starts
     * accepting inserts — and hands them to [onLoaded] on the store's own
     * background thread, oldest first, together with the most recent crash so a
     * caller can surface it even if the entry it blamed has since been pruned.
     */
    fun loadAsync(onLoaded: (api: List<ApiLog>, events: List<ApiLog>, lastCrash: CrashReport?) -> Unit) {
        submit {
            val db = helper.writableDatabase
            // Trim anything left over the cap by a previous run before reading,
            // so we never pull more into memory than the host asked for.
            LogEventType.entries.forEach { prune(db, it) }
            val crashes = readCrashes(db)
            onLoaded(
                read(db, LogEventType.API, crashes),
                read(db, LogEventType.EVENT_TRACKER, crashes),
                // Highest id == most recent, matching the insertion order.
                crashes.entries.maxByOrNull { it.key }?.value,
            )
        }
    }

    /** Queues deletion of every persisted entry. */
    fun clear() {
        submit {
            val db = helper.writableDatabase
            db.delete(TABLE, null, null)
            db.delete(CRASH_TABLE, null, null)
        }
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

    private fun ApiLog.toValues(type: LogEventType): ContentValues = ContentValues().apply {
        put(COL_TYPE, type.name)
        put(COL_SESSION, SESSION_ID)
        put(COL_LOG_ID, id)
        put(COL_STATE, state.name)
        put(COL_RESPONSE_CODE, responseCode)
        put(COL_METHOD, method)
        put(COL_URL, url)
        put(COL_RESPONSE_TIME, responseTime)
        put(COL_SIZE, size)
        put(COL_DATE, date.time)
        put(COL_RESPONSE_HEADER, responseHeader.encodeApiMap())
        put(COL_RESPONSE_BODY, responseBody)
        put(COL_REQUEST_HEADER, requestHeader.encodeApiMap())
        put(COL_REQUEST_BODY, requestBody.encodeApiMap())
    }

    private fun CrashReport.toValues(): ContentValues = ContentValues().apply {
        put(COL_CRASH_SESSION, SESSION_ID)
        put(COL_CRASH_DATE, date.time)
        put(COL_CRASH_THREAD, threadName)
        put(COL_CRASH_EXCEPTION, exceptionClass)
        put(COL_CRASH_MESSAGE, message)
        put(COL_CRASH_STACK, stackTrace)
    }

    /** Points the given entries at [crashId] so a later run can pair them up. */
    private fun tagSuspects(db: SQLiteDatabase, crashId: Long, suspectLogIds: List<Long>) {
        val placeholders = suspectLogIds.joinToString(",") { "?" }
        val args = ArrayList<String>(suspectLogIds.size + 1)
        args.add(SESSION_ID)
        suspectLogIds.forEach { args.add(it.toString()) }
        db.update(
            TABLE,
            ContentValues().apply { put(COL_CRASH_ID, crashId) },
            "$COL_SESSION = ? AND $COL_LOG_ID IN ($placeholders)",
            args.toTypedArray(),
        )
    }

    private fun read(
        db: SQLiteDatabase,
        type: LogEventType,
        crashes: Map<Long, CrashReport>,
    ): List<ApiLog> {
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
            val state = cursor.getColumnIndexOrThrow(COL_STATE)
            val crashId = cursor.getColumnIndexOrThrow(COL_CRASH_ID)

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
                        state = cursor.getString(state).toLogState(),
                        // A dangling id (its crash row pruned away) reads as null
                        // rather than failing the whole restore.
                        crash = if (cursor.isNull(crashId)) null else crashes[cursor.getLong(crashId)],
                    ),
                )
            }
        }
        return logs
    }

    private fun readCrashes(db: SQLiteDatabase): Map<Long, CrashReport> {
        val crashes = LinkedHashMap<Long, CrashReport>()
        db.query(CRASH_TABLE, null, null, null, null, null, "$COL_CRASH_PK ASC").use { cursor ->
            val id = cursor.getColumnIndexOrThrow(COL_CRASH_PK)
            val date = cursor.getColumnIndexOrThrow(COL_CRASH_DATE)
            val thread = cursor.getColumnIndexOrThrow(COL_CRASH_THREAD)
            val exception = cursor.getColumnIndexOrThrow(COL_CRASH_EXCEPTION)
            val message = cursor.getColumnIndexOrThrow(COL_CRASH_MESSAGE)
            val stack = cursor.getColumnIndexOrThrow(COL_CRASH_STACK)

            while (cursor.moveToNext()) {
                crashes[cursor.getLong(id)] = CrashReport(
                    date = Date(cursor.getLong(date)),
                    threadName = cursor.getString(thread),
                    exceptionClass = cursor.getString(exception),
                    message = if (cursor.isNull(message)) null else cursor.getString(message),
                    stackTrace = cursor.getString(stack),
                )
            }
        }
        return crashes
    }

    /**
     * Keeps only the newest [maxEntries] rows of [type].
     *
     * Crash-linked rows need no protection from this: they are by definition the
     * newest entries at the moment the process died, so they are the last rows
     * pruning would reach.
     */
    private fun prune(db: SQLiteDatabase, type: LogEventType) {
        db.delete(
            TABLE,
            "$COL_TYPE = ? AND $COL_ID NOT IN " +
                "(SELECT $COL_ID FROM $TABLE WHERE $COL_TYPE = ? ORDER BY $COL_ID DESC LIMIT ?)",
            arrayOf(type.name, type.name, maxEntries.toString()),
        )
    }

    /** Keeps only the newest [MAX_CRASHES] crash reports. */
    private fun pruneCrashes(db: SQLiteDatabase) {
        db.delete(
            CRASH_TABLE,
            "$COL_CRASH_PK NOT IN " +
                "(SELECT $COL_CRASH_PK FROM $CRASH_TABLE ORDER BY $COL_CRASH_PK DESC LIMIT ?)",
            arrayOf(MAX_CRASHES.toString()),
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
                    $COL_SESSION TEXT NOT NULL,
                    $COL_LOG_ID INTEGER NOT NULL,
                    $COL_STATE TEXT NOT NULL,
                    $COL_RESPONSE_CODE TEXT NOT NULL,
                    $COL_METHOD TEXT NOT NULL,
                    $COL_URL TEXT NOT NULL,
                    $COL_RESPONSE_TIME TEXT NOT NULL,
                    $COL_SIZE TEXT NOT NULL,
                    $COL_DATE INTEGER NOT NULL,
                    $COL_RESPONSE_HEADER TEXT NOT NULL,
                    $COL_RESPONSE_BODY TEXT NOT NULL,
                    $COL_REQUEST_HEADER TEXT NOT NULL,
                    $COL_REQUEST_BODY TEXT NOT NULL,
                    $COL_CRASH_ID INTEGER
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX idx_apilog_type_id ON $TABLE ($COL_TYPE, $COL_ID)")
            // Supports looking back at earlier calls to the same endpoint.
            db.execSQL("CREATE INDEX idx_apilog_url ON $TABLE ($COL_URL)")
            // Completing a pending entry updates it by (session, log id), which
            // runs on every captured response — so it gets its own index.
            db.execSQL("CREATE INDEX idx_apilog_identity ON $TABLE ($COL_SESSION, $COL_LOG_ID)")

            db.execSQL(
                """
                CREATE TABLE $CRASH_TABLE (
                    $COL_CRASH_PK INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_CRASH_SESSION TEXT NOT NULL,
                    $COL_CRASH_DATE INTEGER NOT NULL,
                    $COL_CRASH_THREAD TEXT NOT NULL,
                    $COL_CRASH_EXCEPTION TEXT NOT NULL,
                    $COL_CRASH_MESSAGE TEXT,
                    $COL_CRASH_STACK TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Debug logs are disposable: recreate rather than carry migrations.
            db.execSQL("DROP TABLE IF EXISTS $TABLE")
            db.execSQL("DROP TABLE IF EXISTS $CRASH_TABLE")
            onCreate(db)
        }

        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            onUpgrade(db, oldVersion, newVersion)
    }

    private companion object {
        const val DB_NAME = "apilogkit-logs.db"
        const val DB_VERSION = 2
        const val TABLE = "api_logs"
        const val CRASH_TABLE = "api_log_crashes"
        const val PRUNE_INTERVAL = 25
        const val MAX_CRASHES = 20

        /**
         * How long the crash handler may block waiting for the queue to drain.
         * Generous enough for a backlog of large bodies, short enough that a
         * wedged write cannot turn a crash into a hang.
         */
        const val CRASH_FLUSH_TIMEOUT_MS = 2_000L

        /**
         * Identifies this *process*, not this store instance, so an entry stored
         * while pending can still be matched and updated after the store has
         * been reopened with a different retention cap. Paired with
         * [ApiLog.id] — a plain counter that only has to be unique within one
         * process — it identifies a row across runs.
         */
        val SESSION_ID: String = UUID.randomUUID().toString()

        const val COL_ID = "id"
        const val COL_TYPE = "type"
        const val COL_SESSION = "session"
        const val COL_LOG_ID = "log_id"
        const val COL_STATE = "state"
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
        const val COL_CRASH_ID = "crash_id"

        const val COL_CRASH_PK = "id"
        const val COL_CRASH_SESSION = "session"
        const val COL_CRASH_DATE = "date"
        const val COL_CRASH_THREAD = "thread"
        const val COL_CRASH_EXCEPTION = "exception"
        const val COL_CRASH_MESSAGE = "message"
        const val COL_CRASH_STACK = "stacktrace"
    }
}

/**
 * Restores a persisted [ApiLogState], defaulting to finished for an unreadable
 * value — a restored entry stuck on "pending" can never complete, so finished is
 * the safer guess.
 */
internal fun String?.toLogState(): ApiLogState =
    ApiLogState.entries.firstOrNull { it.name == this } ?: ApiLogState.FINISHED

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
