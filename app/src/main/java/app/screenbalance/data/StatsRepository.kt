package app.screenbalance.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.screenbalance.engine.Section
import app.screenbalance.engine.WatchedApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Local usage statistics. Only aggregates are stored: time per Instagram section, per watched app,
 * per app with a daily limit, and phone time, per day; and each finished day's summary (minutes and
 * opens per app, minutes per hour, unlocks, the longest visit), because Android keeps that detail
 * for only about a week. Everything is kept 90 days. No screen content, usernames or messages are
 * ever written.
 *
 * One table: the `section` column holds an Instagram [Section] name, "app:<WatchedApp>" for the other
 * watched apps (and Instagram Lite, recorded as "app:INSTAGRAM"), "pkg:<package>" for any other app
 * with a daily limit, or "phone" for phone time.
 */
class StatsRepository(context: Context, private val io: CoroutineDispatcher) {

    private val helper = Helper(context.applicationContext)
    private val _version = MutableStateFlow(0L)
    /** Bumped after every write so screens can reload. */
    val version: StateFlow<Long> = _version.asStateFlow()

    suspend fun addSectionTime(day: LocalDate, durations: Map<Section, Long>) =
        addTime(day, durations.mapKeys { it.key.name })

    suspend fun addAppTime(day: LocalDate, durations: Map<WatchedApp, Long>) =
        addTime(day, durations.mapKeys { APP_PREFIX + it.key.name })

    /** Any other app with a daily limit, by package. */
    suspend fun addPackageTime(day: LocalDate, durations: Map<String, Long>) =
        addTime(day, durations.mapKeys { PKG_PREFIX + it.key })

    /** Phone time: the screen on and unlocked, any app. */
    suspend fun addPhoneTime(day: LocalDate, ms: Long) = addTime(day, mapOf(PHONE_KEY to ms))

    private suspend fun addTime(day: LocalDate, durations: Map<String, Long>) = withContext(io) {
        if (durations.isEmpty()) return@withContext
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for ((key, ms) in durations) {
                if (ms <= 0) continue
                // UPSERT syntax needs SQLite 3.24 (API 30+); update-then-insert works everywhere.
                db.execSQL("UPDATE section_time SET ms = ms + ? WHERE day = ? AND section = ?", arrayOf<Any>(ms, day.toString(), key))
                db.execSQL(
                    "INSERT OR IGNORE INTO section_time(day, section, ms) VALUES(?, ?, ?)",
                    arrayOf<Any>(day.toString(), key, ms),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        _version.value++
    }

    /** Per-day stats for the inclusive range, oldest first, including days with no data. */
    suspend fun days(from: LocalDate, to: LocalDate): List<DayStats> = withContext(io) {
        val db = helper.readableDatabase
        val sections = HashMap<String, MutableMap<Section, Long>>()
        val apps = HashMap<String, MutableMap<WatchedApp, Long>>()
        val packages = HashMap<String, MutableMap<String, Long>>()
        val phone = HashMap<String, Long>()
        db.rawQuery(
            "SELECT day, section, ms FROM section_time WHERE day BETWEEN ? AND ?",
            arrayOf(from.toString(), to.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val day = c.getString(0)
                val key = c.getString(1)
                if (key == PHONE_KEY) {
                    phone[day] = c.getLong(2)
                } else if (key.startsWith(APP_PREFIX)) {
                    val app = runCatching { WatchedApp.valueOf(key.removePrefix(APP_PREFIX)) }.getOrNull() ?: continue
                    apps.getOrPut(day) { mutableMapOf() }[app] = c.getLong(2)
                } else if (key.startsWith(PKG_PREFIX)) {
                    packages.getOrPut(day) { mutableMapOf() }[key.removePrefix(PKG_PREFIX)] = c.getLong(2)
                } else {
                    val section = runCatching { Section.valueOf(key) }.getOrNull() ?: continue
                    sections.getOrPut(day) { mutableMapOf() }[section] = c.getLong(2)
                }
            }
        }
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { d ->
            val k = d.toString()
            DayStats(d, sections[k].orEmpty(), apps[k].orEmpty(), packages[k].orEmpty(), phone[k] ?: 0L)
        }.toList()
    }

    /** A finished day saved by [saveDay], or null if that day was never saved. */
    suspend fun savedDay(day: LocalDate): DaySummary? = withContext(io) {
        val db = helper.readableDatabase
        val key = arrayOf(day.toString())
        val info = db.rawQuery(
            "SELECT hours, unlocks, first_unlock, longest_pkg, longest_ms FROM day_info WHERE day = ?", key,
        ).use { c ->
            if (!c.moveToFirst()) return@withContext null
            val hours = c.getString(0).split(',').map { it.toLongOrNull() ?: 0L }.let { h -> List(24) { h.getOrElse(it) { 0L } } }
            DaySummary(
                day, emptyMap(), hours,
                unlocks = c.getInt(1).takeIf { it >= 0 },
                firstUnlockMinute = c.getInt(2).takeIf { it >= 0 },
                longestPkg = c.getString(3)?.takeIf { it.isNotEmpty() },
                longestMs = c.getLong(4),
            )
        }
        val apps = HashMap<String, AppDay>()
        db.rawQuery("SELECT pkg, ms, opens FROM app_day WHERE day = ?", key).use { c ->
            while (c.moveToNext()) apps[c.getString(0)] = AppDay(c.getLong(1), c.getInt(2))
        }
        info.copy(apps = apps)
    }

    /** Saves a finished day's summary, replacing what was saved for it. */
    suspend fun saveDay(d: DaySummary) = withContext(io) {
        val db = helper.writableDatabase
        val key = arrayOf(d.day.toString())
        db.beginTransaction()
        try {
            db.delete("app_day", "day = ?", key)
            db.delete("day_info", "day = ?", key)
            for ((pkg, a) in d.apps) {
                db.execSQL("INSERT INTO app_day(day, pkg, ms, opens) VALUES(?, ?, ?, ?)", arrayOf<Any>(d.day.toString(), pkg, a.ms, a.opens))
            }
            db.execSQL(
                "INSERT INTO day_info(day, hours, unlocks, first_unlock, longest_pkg, longest_ms) VALUES(?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    d.day.toString(), d.hours.joinToString(","), d.unlocks ?: -1, d.firstUnlockMinute ?: -1,
                    d.longestPkg ?: "", d.longestMs,
                ),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun pruneOlderThan(day: LocalDate) = withContext(io) {
        val db = helper.writableDatabase
        for (table in listOf("section_time", "app_day", "day_info")) db.delete(table, "day < ?", arrayOf(day.toString()))
    }

    suspend fun clearAll() = withContext(io) {
        val db = helper.writableDatabase
        for (table in listOf("section_time", "app_day", "day_info")) db.delete(table, null, null)
        _version.value++
    }

    private companion object {
        const val APP_PREFIX = "app:"
        const val PKG_PREFIX = "pkg:"
        const val PHONE_KEY = "phone"
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "stats.db", null, 3) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE section_time(day TEXT NOT NULL, section TEXT NOT NULL, ms INTEGER NOT NULL, " +
                    "PRIMARY KEY(day, section))",
            )
            createHistory(db)
        }

        /** v2 stopped counting blocks: delete what earlier versions recorded. v3 keeps daily summaries. */
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) db.execSQL("DROP TABLE IF EXISTS block_event")
            if (oldVersion < 3) createHistory(db)
        }

        /** Finished days: time and opens per app, and per day the hours (24 comma-separated ms), unlocks, longest visit. */
        private fun createHistory(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS app_day(day TEXT NOT NULL, pkg TEXT NOT NULL, ms INTEGER NOT NULL, " +
                    "opens INTEGER NOT NULL, PRIMARY KEY(day, pkg))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS day_info(day TEXT NOT NULL PRIMARY KEY, hours TEXT NOT NULL, " +
                    "unlocks INTEGER NOT NULL, first_unlock INTEGER NOT NULL, longest_pkg TEXT NOT NULL, longest_ms INTEGER NOT NULL)",
            )
        }
    }
}

data class DayStats(
    val day: LocalDate,
    /** Instagram (main app) time per section. */
    val sectionMs: Map<Section, Long>,
    /** Other watched apps, and Instagram Lite under [WatchedApp.INSTAGRAM]. */
    val appMs: Map<WatchedApp, Long> = emptyMap(),
    /** Other apps with a daily limit, by package. */
    val packageMs: Map<String, Long> = emptyMap(),
    /** Phone time: screen on and unlocked, any app. */
    val phoneMs: Long = 0L,
) {
    /** Time Glance measured itself in [app] (all its packages). */
    fun trackedFor(app: WatchedApp): Long =
        (appMs[app] ?: 0L) + if (app == WatchedApp.INSTAGRAM) sectionMs.values.sum() else 0L
}
