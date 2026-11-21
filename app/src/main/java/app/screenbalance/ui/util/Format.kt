package app.screenbalance.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val clock: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun formatClock(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    clock.withLocale(Locale.getDefault()).format(Instant.ofEpochMilli(epochMs).atZone(zone))

fun formatClockMinutes(minuteOfDay: Int): String {
    val m = minuteOfDay.coerceIn(0, 1440) % 1440
    return clock.withLocale(Locale.getDefault()).format(java.time.LocalTime.of(m / 60, m % 60))
}

/** "1 h 05 min", "12 min", "45 s". */
fun formatRemaining(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s >= 3600 -> "%d h %02d min".format(s / 3600, (s % 3600) / 60)
        s >= 60 -> "${(s + 59) / 60} min"
        else -> "$s s"
    }
}

/** Duration as a (value, unit) pair for large figures: (1, "h 12 min") or (42, "min"). */
fun splitDuration(ms: Long): Pair<String, String> {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    return if (minutes >= 60) "${minutes / 60}" to "h ${"%02d".format(minutes % 60)} min" else "$minutes" to "min"
}

fun formatMinutesShort(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

/** A limit in minutes as hours where it helps: "2 h 30 min", "2 h", "45 min". */
fun formatLimit(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}
