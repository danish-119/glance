package app.screenbalance.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import app.screenbalance.engine.WatchedApp
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads apps' foreground time from Android's UsageStats (optional permission). This is the same
 * source Digital Wellbeing uses, so totals match what the system reports.
 */
class AppUsage(private val context: Context) {

    private val usm get() = context.getSystemService(UsageStatsManager::class.java)

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Still the only way to read the usage-access app op without a privileged permission.
            @Suppress("DEPRECATION")
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Watched apps installed on this phone (needs the `<queries>` entries in the manifest). */
    fun installedApps(): Set<WatchedApp> =
        WatchedApp.entries.filterTo(LinkedHashSet()) { app -> app.packages.any(::isInstalled) }

    private fun isInstalled(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Foreground ms per package for [day], limited to [packages] (null = every app), or null if the
     * permission is missing. Packages with no use that day are absent.
     */
    fun foregroundByPackage(
        day: LocalDate,
        packages: Set<String>?,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMs: Long = System.currentTimeMillis(),
    ): Map<String, Long>? {
        if (!hasAccess()) return null
        if (day == LocalDate.now(zone)) DemoMode.today(context)?.let { demo ->
            return if (packages == null) demo else demo.filterKeys { it in packages }
        }
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), nowMs)
        if (end <= start) return emptyMap()
        return try {
            sumForeground(start, end, packages)
        } catch (_: SecurityException) {
            null
        }
    }

    private class Track {
        val resumed = HashSet<String>()
        var since = -1L
        var sawAny = false
        var total = 0L
    }

    /** One pass over the day's events; each package's foreground time is tracked separately. */
    private fun sumForeground(start: Long, end: Long, packages: Set<String>?): Map<String, Long> {
        val events = usm?.queryEvents(start, end) ?: return emptyMap()
        val e = UsageEvents.Event()
        val tracks = HashMap<String, Track>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            if (packages != null && pkg !in packages) continue
            val t = tracks.getOrPut(pkg) { Track() }
            // An app has several activities; it is in the foreground while any of them is resumed.
            val cls = e.className ?: ""
            when (e.eventType) {
                RESUMED -> {
                    if (t.resumed.isEmpty()) t.since = e.timeStamp
                    t.resumed += cls
                    t.sawAny = true
                }
                PAUSED, STOPPED -> {
                    if (!t.sawAny && t.resumed.isEmpty()) {
                        t.total += e.timeStamp - start // Already open when the window started.
                    } else if (t.resumed.remove(cls) && t.resumed.isEmpty() && t.since >= 0) {
                        t.total += e.timeStamp - t.since
                        t.since = -1
                    }
                    t.sawAny = true
                }
            }
        }
        return tracks.mapValues { (_, t) ->
            val open = if (t.resumed.isNotEmpty() && t.since >= 0) end - t.since else 0L
            (t.total + open).coerceAtLeast(0)
        }
    }

    private companion object {
        // UsageEvents.Event.ACTIVITY_RESUMED / ACTIVITY_PAUSED / ACTIVITY_STOPPED (1, 2, 23).
        const val RESUMED = 1
        const val PAUSED = 2
        const val STOPPED = 23
    }
}
