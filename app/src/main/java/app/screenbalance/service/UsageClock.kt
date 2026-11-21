package app.screenbalance.service

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Attributes foreground time to keys (Instagram sections, or whole apps). Time accrues only between
 * observations that are at most [maxGapMs] apart, so a missed "left the app" signal can't inflate
 * totals. Buffered in memory and handed to [write] in batches to keep disk writes rare.
 */
internal class UsageClock<K>(
    private val write: suspend (LocalDate, Map<K, Long>) -> Unit,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val maxGapMs: Long = 15_000L,
) {
    private var current: K? = null
    private var lastTickAt = -1L
    private val pending = HashMap<LocalDate, MutableMap<K, Long>>()

    fun observe(key: K, now: Long) {
        accrue(now)
        current = key
        lastTickAt = now
    }

    /** Called on heartbeats while the app stays in front. */
    fun tick(now: Long) {
        if (current == null) return
        accrue(now)
        lastTickAt = now
    }

    fun leave(now: Long) {
        accrue(now)
        current = null
        lastTickAt = -1
    }

    /** Today's time not yet written, for keys matching [filter]. */
    fun pendingToday(now: Long, filter: (K) -> Boolean = { true }): Long =
        pending[dayOf(now)].orEmpty().filterKeys(filter).values.sum()

    suspend fun flush() {
        if (pending.isEmpty()) return
        val snapshot = pending.mapValues { it.value.toMap() }
        pending.clear()
        for ((day, map) in snapshot) write(day, map)
    }

    private fun accrue(now: Long) {
        val k = current ?: return
        if (lastTickAt < 0) return
        val gap = now - lastTickAt
        if (gap <= 0 || gap > maxGapMs) return
        val m = pending.getOrPut(dayOf(now)) { mutableMapOf() }
        m[k] = (m[k] ?: 0L) + gap
    }

    private fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()
}
