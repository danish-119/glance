package app.screenbalance.data

import android.content.Context
import app.screenbalance.service.SystemApps
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Screen time per app from Android's UsageStats, counted the same way everywhere (Today, the
 * widget, app history): apps with a launcher icon, not home screens and not Glance itself.
 * Nothing is copied: past days are read from Android, which keeps them for about a week.
 */
class ScreenTime(private val context: Context, private val usage: AppUsage) {

    private val homes by lazy { SystemApps(context).homes }
    private val checked = ConcurrentHashMap<String, Boolean>()

    fun isApp(pkg: String): Boolean = checked.getOrPut(pkg) {
        pkg != context.packageName && pkg !in homes && context.packageManager.getLaunchIntentForPackage(pkg) != null
    }

    /** Foreground ms per app on [day], or null without usage access. Empty once Android has dropped the day. */
    fun byApp(day: LocalDate): Map<String, Long>? {
        if (day == LocalDate.now()) DemoMode.today(context)?.let { return it.filterKeys(::isApp) }
        if (day != LocalDate.now()) return usage.foregroundByPackage(day, null)?.filterKeys(::isApp)
        // Today is read by both widgets and Today at each unlock and screen-off: one read serves them all for a few seconds.
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(this) {
            val cached = todayCache
            if (cached != null && cached.day == day && now - cached.at < TODAY_TTL_MS) return cached.value
        }
        val value = usage.foregroundByPackage(day, null)?.filterKeys(::isApp)
        synchronized(this) { todayCache = Cached(day, now, value) }
        return value
    }

    /** Drops the shared copy of today's totals, so the next read is fresh (the widget's refresh button). */
    fun forget() {
        synchronized(this) { todayCache = null }
    }

    private class Cached(val day: LocalDate, val at: Long, val value: Map<String, Long>?)
    private var todayCache: Cached? = null

    private companion object {
        const val TODAY_TTL_MS = 10_000L
    }
}
