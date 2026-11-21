package app.screenbalance.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One app on one day: time in front and times opened. */
data class AppDay(val ms: Long, val opens: Int)

/**
 * One day of phone use, summarised: per app, per hour of the day, unlocks and the longest visit.
 * Finished days are saved (90 days) because Android keeps the detail for only about a week.
 */
data class DaySummary(
    val day: LocalDate,
    val apps: Map<String, AppDay>,
    /** ms per hour of the day, 24 entries. */
    val hours: List<Long>,
    /** Null where Android doesn't report unlocks (before Android 9). */
    val unlocks: Int?,
    /** Minute of the day of the first unlock after 4:00; null if none. */
    val firstUnlockMinute: Int?,
    val longestPkg: String?,
    val longestMs: Long,
) {
    val totalMs get() = apps.values.sumOf { it.ms }
    val opens get() = apps.values.sumOf { it.opens }

    companion object {
        fun empty(day: LocalDate) = DaySummary(day, emptyMap(), List(24) { 0L }, null, null, null, 0L)
    }
}

/** Android's own app categories, plus a fallback. */
enum class Category(val label: String) {
    SOCIAL("Social"), VIDEO("Video"), GAME("Games"), AUDIO("Music & audio"), NEWS("News & reading"),
    PRODUCTIVITY("Productivity"), IMAGE("Photos"), MAPS("Maps & travel"), OTHER("Other"),
}

/**
 * Daily usage history. Today is read live from Android; a finished day comes from Glance's own
 * store, or from Android (then saved) while Android still has it.
 */
class UsageHistory(
    private val context: Context,
    private val usage: AppUsage,
    private val screenTime: ScreenTime,
    private val stats: StatsRepository,
) {
    private val usm get() = context.getSystemService(UsageStatsManager::class.java)

    fun hasAccess() = usage.hasAccess()

    /** [day]'s summary, or null when nothing is known (no usage access and nothing saved). */
    suspend fun day(day: LocalDate, today: LocalDate = LocalDate.now()): DaySummary? {
        if (DemoMode.active(context)) stats.savedDay(day)?.let { return it }
        if (!day.isBefore(today)) return if (day == today) read(day) else null
        stats.savedDay(day)?.let { return it.copy(apps = it.apps.filterKeys(::countable)) }
        val read = read(day) ?: return null
        // Older than Android keeps and nothing found: unknown rather than "no use", so not saved.
        if (read.apps.isNotEmpty() || !day.isBefore(today.minusDays(ANDROID_KEEPS_DAYS))) stats.saveDay(read)
        return read
    }

    /** [count] days ending [end], oldest first; unknown days are empty. */
    suspend fun days(end: LocalDate, count: Int, today: LocalDate = LocalDate.now()): List<DaySummary> =
        (count - 1 downTo 0).map { back ->
            val d = end.minusDays(back.toLong())
            day(d, today) ?: DaySummary.empty(d)
        }

    /** Saves the finished days Android still has and Glance hasn't saved yet. */
    suspend fun saveRecent(today: LocalDate = LocalDate.now()) {
        if (!usage.hasAccess()) return
        for (back in 1..ANDROID_KEEPS_DAYS) day(today.minusDays(back), today)
    }

    private val categories = HashMap<String, Category>()

    fun categoryOf(pkg: String): Category = synchronized(categories) {
        categories.getOrPut(pkg) {
            KNOWN[pkg] ?: try {
                val info = context.packageManager.getApplicationInfo(pkg, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) when (info.category) {
                    ApplicationInfo.CATEGORY_SOCIAL -> Category.SOCIAL
                    ApplicationInfo.CATEGORY_VIDEO -> Category.VIDEO
                    ApplicationInfo.CATEGORY_GAME -> Category.GAME
                    ApplicationInfo.CATEGORY_AUDIO -> Category.AUDIO
                    ApplicationInfo.CATEGORY_NEWS -> Category.NEWS
                    ApplicationInfo.CATEGORY_PRODUCTIVITY -> Category.PRODUCTIVITY
                    ApplicationInfo.CATEGORY_IMAGE -> Category.IMAGE
                    ApplicationInfo.CATEGORY_MAPS -> Category.MAPS
                    else -> Category.OTHER
                } else Category.OTHER
            } catch (_: PackageManager.NameNotFoundException) {
                Category.OTHER
            }
        }
    }

    /** An app counts if it has a launcher icon, or it was uninstalled since. */
    private fun countable(pkg: String): Boolean = screenTime.isApp(pkg) || !installed(pkg)

    private fun installed(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Reads [day] from Android's events; null without usage access. */
    private fun read(day: LocalDate, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): DaySummary? {
        if (!usage.hasAccess()) return null
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), now)
        if (end <= start) return DaySummary.empty(day)
        val scan = try { scan(start, end) } catch (_: SecurityException) { return null }
        val sessions = scan.sessions.filter { screenTime.isApp(it.pkg) }
        val hours = LongArray(24)
        for (s in sessions) {
            var t = s.start
            while (t < s.end) {
                val z = Instant.ofEpochMilli(t).atZone(zone)
                val nextHour = z.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
                val piece = minOf(nextHour, s.end) - t
                hours[z.hour] += piece
                t += piece
            }
        }
        val apps = sessions.groupBy { it.pkg }.mapValues { (_, l) -> AppDay(l.sumOf { it.end - it.start }, l.size) }
        val first = scan.unlocks.map { Instant.ofEpochMilli(it).atZone(zone).let { z -> z.hour * 60 + z.minute } }
            .filter { it >= 4 * 60 }.minOrNull()
        val longest = sessions.maxByOrNull { it.end - it.start }
        return DaySummary(
            day, apps, hours.toList(),
            unlocks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) scan.unlocks.size else null,
            firstUnlockMinute = first,
            longestPkg = longest?.pkg, longestMs = longest?.let { it.end - it.start } ?: 0L,
        )
    }

    private class Session(val pkg: String, val start: Long, var end: Long)
    private class Scan(val sessions: List<Session>, val unlocks: List<Long>)

    /**
     * One pass over the events: each app's stretches in front (an app is in front while any of its
     * activities is resumed; stretches under 2 s apart are one visit) and the unlock times.
     */
    private fun scan(start: Long, end: Long): Scan {
        val events = usm?.queryEvents(start, end) ?: return Scan(emptyList(), emptyList())
        val e = UsageEvents.Event()
        val resumed = HashMap<String, HashSet<String>>()
        val since = HashMap<String, Long>()
        val seen = HashSet<String>()
        val sessions = ArrayList<Session>()
        val last = HashMap<String, Session>()
        val unlocks = ArrayList<Long>()
        fun close(pkg: String, from: Long, to: Long) {
            if (to <= from) return
            val prev = last[pkg]
            if (prev != null && from - prev.end < MERGE_MS) {
                prev.end = to
            } else {
                val s = Session(pkg, from, to)
                last[pkg] = s
                sessions += s
            }
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == KEYGUARD_HIDDEN) {
                unlocks += e.timeStamp
                continue
            }
            val pkg = e.packageName ?: continue
            val cls = e.className ?: ""
            val set = resumed.getOrPut(pkg) { HashSet() }
            when (e.eventType) {
                RESUMED -> {
                    if (set.isEmpty()) since[pkg] = e.timeStamp
                    set += cls
                    seen += pkg
                }
                PAUSED, STOPPED -> {
                    if (pkg !in seen && set.isEmpty()) {
                        if (e.eventType == PAUSED) close(pkg, start, e.timeStamp) // Already open at midnight.
                    } else if (set.remove(cls) && set.isEmpty()) {
                        since.remove(pkg)?.let { close(pkg, it, e.timeStamp) }
                    }
                    seen += pkg
                }
            }
        }
        for ((pkg, set) in resumed) if (set.isNotEmpty()) since[pkg]?.let { close(pkg, it, end) }
        return Scan(sessions, unlocks)
    }

    private companion object {
        // UsageEvents.Event.ACTIVITY_RESUMED / ACTIVITY_PAUSED / ACTIVITY_STOPPED / KEYGUARD_HIDDEN.
        const val RESUMED = 1
        const val PAUSED = 2
        const val STOPPED = 23
        const val KEYGUARD_HIDDEN = 18
        const val MERGE_MS = 2_000L
        /** Days of detail Android reliably keeps. */
        const val ANDROID_KEEPS_DAYS = 7L

        /** Popular apps that don't declare a category. */
        val KNOWN = mapOf(
            "com.instagram.android" to Category.SOCIAL, "com.instagram.lite" to Category.SOCIAL,
            "com.facebook.katana" to Category.SOCIAL, "com.snapchat.android" to Category.SOCIAL,
            "com.twitter.android" to Category.SOCIAL, "com.reddit.frontpage" to Category.SOCIAL,
            "com.whatsapp" to Category.SOCIAL, "com.zhiliaoapp.musically" to Category.VIDEO,
            "com.ss.android.ugc.trill" to Category.VIDEO, "com.google.android.youtube" to Category.VIDEO,
        )
    }
}

/** What the Insights tab shows for a 7-day window. Pure arithmetic over [DaySummary]s. */
data class Insights(
    val days: List<DaySummary>,
    /** The 7 days before, for comparison (days with no data are empty). */
    val previous: List<DaySummary>,
    /** Weekly totals, oldest first: the four 7-day windows ending with this one. */
    val weeklyTotals: List<Long>,
    /** Days of [days] that have started, for averages. */
    val daysCounted: Int,
) {
    val totalMs = days.sumOf { it.totalMs }
    val dailyAvgMs = totalMs / daysCounted.coerceAtLeast(1)
    private val previousCounted = previous.count { it.totalMs > 0 }
    /** The 7 days before, per day; null when Glance has none of them. */
    val previousDailyAvgMs: Long? = if (previousCounted == 0) null else previous.sumOf { it.totalMs } / previousCounted
    val perHourAvg: List<Long> = (0..23).map { h -> days.sumOf { it.hours[h] } / daysCounted.coerceAtLeast(1) }
    val unlocksPerDay: Float? = days.mapNotNull { it.unlocks }.takeIf { it.isNotEmpty() }?.let { it.sum() / daysCounted.toFloat() }
    val firstUnlockMinute: Int? = days.mapNotNull { it.firstUnlockMinute }.takeIf { it.isNotEmpty() }?.average()?.toInt()
    val opensPerDay = days.sumOf { it.opens } / daysCounted.coerceAtLeast(1).toFloat()
    val averageVisitMs = days.sumOf { it.opens }.let { if (it == 0) 0L else totalMs / it }
    val longest: DaySummary? = days.filter { it.longestPkg != null }.maxByOrNull { it.longestMs }
    /** 23:00–05:00, per day. */
    val lateNightPerDayMs = days.sumOf { d -> d.hours[23] + (0..4).sumOf { d.hours[it] } } / daysCounted.coerceAtLeast(1)
    val apps: List<Pair<String, AppDay>> = days.flatMap { it.apps.entries }.groupBy({ it.key }, { it.value })
        .map { (pkg, l) -> pkg to AppDay(l.sumOf { it.ms }, l.sumOf { it.opens }) }.sortedByDescending { it.second.ms }
    val busiest = days.filter { it.totalMs > 0 }.maxByOrNull { it.totalMs }
    val quietest = days.take(daysCounted).filter { it.totalMs > 0 }.minByOrNull { it.totalMs }

    /** Morning 5–12, afternoon 12–17, evening 17–22, night 22–5: share of screen time. */
    fun partsOfDay(): List<Pair<String, Long>> {
        val h = perHourAvg
        fun sum(r: IntRange) = r.sumOf { h[it] }
        return listOf(
            "Morning" to sum(5..11), "Afternoon" to sum(12..16), "Evening" to sum(17..21),
            "Night" to sum(22..23) + sum(0..4),
        )
    }
}
