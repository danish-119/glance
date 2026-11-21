package app.screenbalance.service

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phone time today: how long the phone has been unlocked with the screen on. Measured from
 * screen-on / unlock / screen-off broadcasts only; which apps were used is not part of it.
 */
internal class PhoneUse(private val zone: () -> ZoneId = ZoneId::systemDefault) {
    /** Wall-clock ms the current unlocked stretch started, or -1 while locked / screen off. */
    private var unlockedSince = -1L

    /** Today's already-recorded phone time (loaded from, and written to, the stats store). */
    var storedTodayMs = 0L
    private var storedDay: LocalDate? = null

    val isUnlocked get() = unlockedSince >= 0

    fun onUnlocked(now: Long) {
        if (unlockedSince >= 0) return
        unlockedSince = now
    }

    /** Ends the current stretch; returns the ms to record for today (0 if none). */
    fun onScreenOff(now: Long): Long {
        if (unlockedSince < 0) return 0
        val ms = (now - unlockedSince).coerceAtLeast(0)
        unlockedSince = -1
        return ms
    }

    /** Phone time today, including the stretch in progress. */
    fun todayMs(now: Long): Long {
        val today = dayOf(now)
        if (storedDay != today) {
            storedDay = today
            storedTodayMs = 0
        }
        // A stretch that began before midnight only counts from midnight.
        val running = if (unlockedSince >= 0) now - maxOf(unlockedSince, startOf(today)) else 0
        return storedTodayMs + running.coerceAtLeast(0)
    }

    fun setStoredToday(day: LocalDate, ms: Long) {
        storedDay = day
        storedTodayMs = ms
    }

    private fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()
    private fun startOf(day: LocalDate) = day.atStartOfDay(zone()).toInstant().toEpochMilli()
}
