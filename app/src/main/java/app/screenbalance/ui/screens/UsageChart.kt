package app.screenbalance.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.screenbalance.ui.DayUsage
import app.screenbalance.ui.theme.LocalAccents
import app.screenbalance.ui.util.formatMinutesShort
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** How far back the week arrows go: Glance's own stats are kept 90 days. */
const val MAX_WEEKS_BACK = 12

/** The last day of the 7-day window [weeksBack] windows before the one ending [today]. */
fun weekEnd(today: LocalDate, weeksBack: Int): LocalDate = today.minusDays(7L * weeksBack)

/** "Past 7 days", or the window's dates: "8 – 14 Nov". */
fun weekLabel(today: LocalDate, weeksBack: Int): String {
    if (weeksBack == 0) return "Past 7 days"
    val end = weekEnd(today, weeksBack)
    val start = end.minusDays(6)
    val month = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    return if (start.month == end.month) "${start.dayOfMonth} – ${end.format(month)}"
    else "${start.format(month)} – ${end.format(month)}"
}

/** "Today", "Yesterday", or "Tuesday 18 Nov". */
fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault()))
}

/** ‹ Week label › : steps through earlier 7-day windows. */
@Composable
fun WeekNavigator(label: String, canGoBack: Boolean, canGoForward: Boolean, onBack: () -> Unit, onForward: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Earlier week")
        }
        Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Later week")
        }
    }
}

/**
 * Seven daily bars, one hue; the selected day is ember, the daily goal a dashed line. Tap a bar to choose that day.
 * Days after today (none in practice) and days with no use have no bar.
 */
@Composable
internal fun WeekChart(week: List<DayUsage>, selected: LocalDate?, goalMs: Long = 0L, onSelect: (LocalDate) -> Unit) {
    val maxMs = maxOf(week.maxOfOrNull { it.totalMs } ?: 0L, 30 * 60_000L, goalMs + goalMs / 8).toFloat()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val ink = MaterialTheme.colorScheme.onSurface
    val ember = LocalAccents.current.ember
    val rule = LocalAccents.current.rule
    val selectedIndex = week.indexOfFirst { it.day == selected }

    Column {
        Box(Modifier.fillMaxWidth().height(160.dp)) {
            Canvas(Modifier.fillMaxWidth().height(160.dp)) {
                drawLine(rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                if (week.isEmpty()) return@Canvas
                val slot = size.width / week.size
                val barW = (slot * 0.42f).coerceAtMost(28.dp.toPx())
                val r = 4.dp.toPx()
                if (selectedIndex >= 0) {
                    drawRect(ink.copy(alpha = 0.06f), Offset(slot * selectedIndex, 0f), Size(slot, size.height))
                }
                week.forEachIndexed { i, d ->
                    val h = (size.height * (d.totalMs / maxMs) * grow.value).coerceAtLeast(if (d.totalMs > 0) 2.dp.toPx() else 0f)
                    if (h <= 0f) return@forEachIndexed
                    val x = slot * i + (slot - barW) / 2
                    // Rounded data end, square at the baseline.
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = x, top = size.height - h, right = x + barW, bottom = size.height,
                                topLeftCornerRadius = CornerRadius(r, r), topRightCornerRadius = CornerRadius(r, r),
                            ),
                        )
                    }
                    drawPath(path, if (i == selectedIndex) ember else ink.copy(alpha = 0.78f))
                }
                // The daily goal, if any: a dashed line across.
                if (goalMs > 0) {
                    val y = size.height - size.height * (goalMs / maxMs)
                    drawLine(
                        ink.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    )
                }
            }
            // Hit targets: full-height columns, much larger than the bars.
            Row(Modifier.fillMaxWidth().height(160.dp)) {
                week.forEach { d ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(selected = d.day == selected, role = Role.Tab, onClick = { onSelect(d.day) })
                            .semantics {
                                contentDescription = "${d.day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}: " +
                                    formatMinutesShort(d.totalMs)
                            },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            week.forEach { d ->
                Text(
                    d.day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (d.day == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
