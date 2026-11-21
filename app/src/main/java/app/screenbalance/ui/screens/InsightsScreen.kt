package app.screenbalance.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.screenbalance.data.AppDay
import app.screenbalance.data.Category
import app.screenbalance.data.DaySummary
import app.screenbalance.data.Insights
import app.screenbalance.ui.AppViewModel
import app.screenbalance.ui.DayUsage
import app.screenbalance.ui.Routes
import app.screenbalance.ui.SystemIntents
import app.screenbalance.ui.components.AppIcon
import app.screenbalance.ui.components.EmptyNote
import app.screenbalance.ui.components.Figure
import app.screenbalance.ui.components.InfoButton
import app.screenbalance.ui.components.KickerText
import app.screenbalance.ui.components.LoadingAppRows
import app.screenbalance.ui.components.NavRow
import app.screenbalance.ui.components.Panel
import app.screenbalance.ui.components.SectionHeader
import app.screenbalance.ui.components.Segmented
import app.screenbalance.ui.theme.LocalAccents
import app.screenbalance.ui.util.formatClockMinutes
import app.screenbalance.ui.util.formatLimit
import app.screenbalance.ui.util.formatMinutesShort
import app.screenbalance.ui.util.splitDuration
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Period { DAY, WEEK }

/**
 * Insights, in two clear views. **Week**: the daily average against the 7 days before, the seven
 * days as bars (tap one for that day), and tiles for pickups, when in the day, apps and habits.
 * **Day**: one day's total against the usual, its hours, pickups and apps. Every figure says
 * whether it is per day or a total.
 */
@Composable
fun InsightsScreen(vm: AppViewModel, padding: PaddingValues, navigate: (String) -> Unit) {
    val status by vm.status.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val goal = settings?.dailyGoalMinutes ?: 0
    val ctx = LocalContext.current
    val today = LocalDate.now()
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    var weeksBack by rememberSaveable { mutableIntStateOf(0) }
    var dayEpoch by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val day = LocalDate.ofEpochDay(dayEpoch).takeIf { !it.isAfter(today) } ?: today
    var loaded by remember { mutableStateOf(false) }
    val live = if ((period == Period.WEEK && weeksBack == 0) || (period == Period.DAY && day == today)) usage else null
    // The day view reads the 7 days ending that day, so "usual" is the 7 days before it.
    val insights by produceState<Insights?>(null, period, weeksBack, dayEpoch, status.usageAccess, live) {
        value = if (period == Period.WEEK) vm.insights(weeksBack) else vm.insightsEnding(day)
        loaded = true
    }

    Page(
        "Insights", contentPadding = padding,
        actions = {
            InfoButton(
                "Insights",
                "Week: the 7 days shown, as a daily average and day by day. Day: one day, compared with your average " +
                    "of the 7 days before it.\n\nFrom Android's usage data. Glance keeps a daily summary on this phone " +
                    "for 90 days; nothing leaves the phone. Settings → Clear usage history erases it.",
            )
        },
    ) {
        item(key = "period") {
            Segmented(listOf(Period.DAY, Period.WEEK), period, { if (it == Period.DAY) "Day" else "Week" }, { period = it })
            Spacer(Modifier.height(8.dp))
            if (period == Period.WEEK) {
                WeekNavigator(
                    label = weekLabel(today, weeksBack),
                    canGoBack = weeksBack < MAX_WEEKS_BACK && (insights?.totalMs ?: 0L) > 0,
                    canGoForward = weeksBack > 0,
                    onBack = { weeksBack++ },
                    onForward = { weeksBack-- },
                )
            } else {
                WeekNavigator(
                    label = dayLabel(day, today),
                    canGoBack = day.isAfter(today.minusDays(90)),
                    canGoForward = day.isBefore(today),
                    onBack = { dayEpoch = day.minusDays(1).toEpochDay() },
                    onForward = { dayEpoch = day.plusDays(1).toEpochDay() },
                )
            }
        }
        val i = insights
        if (i == null) {
            item(key = "empty") {
                if (!loaded) LoadingAppRows(4)
                else if (!status.usageAccess) {
                    EmptyNote("Insights need usage access", "Android's usage data is where these patterns come from. It stays on your phone.")
                    NavRow("Allow usage access", null, { SystemIntents.openUsageAccessSettings(ctx) }, trailing = "Allow")
                }
            }
            return@Page
        }
        if (period == Period.WEEK) {
            weekView(i, goal, today, vm, navigate, onDay = { d -> dayEpoch = d.toEpochDay(); period = Period.DAY })
        } else {
            dayView(i.days.last(), i, goal, vm, navigate)
        }
    }
}

// ------------------------------------------------------------------------------------------ week

private fun LazyListScope.weekView(
    i: Insights, goal: Int, today: LocalDate, vm: AppViewModel, navigate: (String) -> Unit, onDay: (LocalDate) -> Unit,
) {
    if (i.totalMs == 0L) {
        item(key = "nothing") { EmptyNote("Nothing recorded", "History builds up from when Glance was installed.") }
        return
    }
    item(key = "hero") {
        Spacer(Modifier.height(12.dp))
        KickerText("Daily average")
        val (v, unit) = splitDuration(i.dailyAvgMs)
        Figure(v, unit, big = true)
        i.previousDailyAvgMs?.takeIf { it > 0 }?.let { prev ->
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { DeltaChip(i.dailyAvgMs, prev, "vs the 7 days before") }
        }
    }
    item(key = "bars") {
        SectionHeader("Day by day")
        val week = i.days.map { DayUsage(it.day, it.totalMs) }
        // Today (when in view) in ember, like everywhere else.
        WeekChart(week, today.takeIf { t -> week.any { it.day == t } }, goalMs = goal * 60_000L) { d -> onDay(d) }
        Spacer(Modifier.height(6.dp))
        Text(
            if (goal > 0) "Dashed line: your ${formatLimit(goal)} goal. Tap a day to open it." else "Tap a day to open it.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item(key = "tiles") {
        SectionHeader("At a glance")
        val counted = i.days.take(i.daysCounted).filter { it.totalMs > 0 }
        TileGrid(
            listOfNotNull(
                Tile(Icons.Outlined.Timer, formatMinutesShort(i.totalMs), "total, these 7 days"),
                if (goal > 0) Tile(Icons.Outlined.Flag, "${counted.count { it.totalMs <= goal * 60_000L }} of ${counted.size}", "days within your goal") else null,
                i.unlocksPerDay?.let { Tile(Icons.Outlined.LockOpen, "${it.roundToInt()}", "unlocks per day") },
                Tile(Icons.Outlined.OpenInNew, "${i.opensPerDay.roundToInt()}", "apps opened per day"),
                Tile(Icons.Outlined.Timelapse, visit(i.averageVisitMs), "average visit"),
                i.firstUnlockMinute?.let { Tile(Icons.Outlined.WbSunny, formatClockMinutes(it), "first pickup, on average") },
            ),
        )
    }
    item(key = "when") {
        SectionHeader("When in the day · per day")
        HourChart(i.perHourAvg)
        Spacer(Modifier.height(14.dp))
        PartsOfDay(i.partsOfDay())
    }
    appsSection(i.apps.take(6), i.totalMs, i.daysCounted, perDay = true, vm, navigate)
    item(key = "kinds") {
        val cats = remember(i) {
            i.apps.groupBy { vm.categoryOf(it.first) }.map { (c, l) -> c to l.sumOf { it.second.ms } }
                .filter { it.second > 0 }.sortedByDescending { it.second }
        }
        if (cats.size > 1 || cats.firstOrNull()?.first != Category.OTHER) {
            SectionHeader("Kinds of apps · share of the 7 days")
            cats.forEach { (c, ms) -> LabeledBar(c.label, "${(ms * 100f / i.totalMs).roundToInt()}%", ms / i.totalMs.toFloat()) }
        }
    }
    item(key = "habits") {
        val counted = i.days.take(i.daysCounted)
        val weekdays = counted.filter { it.day.dayOfWeek.value <= 5 && it.totalMs > 0 }
        val weekend = counted.filter { it.day.dayOfWeek.value >= 6 && it.totalMs > 0 }
        SectionHeader("Habits")
        TileGrid(
            listOfNotNull(
                i.busiest?.let { Tile(Icons.Outlined.WbTwilight, formatMinutesShort(it.totalMs), "busiest day · ${dayShort(it.day, today)}") },
                i.quietest?.takeIf { it != i.busiest }?.let { Tile(Icons.Outlined.Nightlight, formatMinutesShort(it.totalMs), "quietest day · ${dayShort(it.day, today)}") },
                if (weekdays.isNotEmpty()) Tile(Icons.Outlined.LightMode, formatMinutesShort(weekdays.sumOf { it.totalMs } / weekdays.size), "per weekday") else null,
                if (weekend.isNotEmpty()) Tile(Icons.Outlined.WbSunny, formatMinutesShort(weekend.sumOf { it.totalMs } / weekend.size), "per weekend day") else null,
                Tile(Icons.Outlined.Bedtime, formatMinutesShort(i.lateNightPerDayMs), "per day after 11 PM"),
                i.longest?.let { d -> Tile(Icons.Outlined.Timelapse, formatMinutesShort(d.longestMs), "longest visit · ${vm.labelOf(d.longestPkg!!)}") },
            ),
        )
    }
    item(key = "trend") {
        if (i.weeklyTotals.count { it > 0 } >= 2) {
            SectionHeader("Last 4 weeks · per day")
            WeeksChart(i.weeklyTotals, i.daysCounted)
        }
    }
}

// ------------------------------------------------------------------------------------------- day

private fun LazyListScope.dayView(d: DaySummary, i: Insights, goal: Int, vm: AppViewModel, navigate: (String) -> Unit) {
    if (d.totalMs == 0L) {
        item(key = "nothing-day") { EmptyNote("Nothing recorded", "No screen time was recorded on this day.") }
        return
    }
    item(key = "hero-day") {
        Spacer(Modifier.height(12.dp))
        KickerText("Screen time this day")
        val (v, unit) = splitDuration(d.totalMs)
        Figure(v, unit, big = true)
        i.previousDailyAvgMs?.takeIf { it > 0 }?.let { usual ->
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { DeltaChip(d.totalMs, usual, "vs your usual day") }
        }
        if (goal > 0) {
            Spacer(Modifier.height(16.dp))
            GoalBar(d.totalMs, goal)
        }
    }
    item(key = "when-day") {
        SectionHeader("When in the day")
        HourChart(d.hours, perDayLabel = false)
    }
    item(key = "tiles-day") {
        SectionHeader("Pickups")
        TileGrid(
            listOfNotNull(
                d.unlocks?.let { Tile(Icons.Outlined.LockOpen, "$it", "unlocks") },
                Tile(Icons.Outlined.OpenInNew, "${d.opens}", "apps opened"),
                d.firstUnlockMinute?.let { Tile(Icons.Outlined.WbSunny, formatClockMinutes(it), "first pickup") },
                d.longestPkg?.let { Tile(Icons.Outlined.Timelapse, formatMinutesShort(d.longestMs), "longest visit · ${vm.labelOf(it)}") },
            ),
        )
    }
    appsSection(d.apps.entries.sortedByDescending { it.value.ms }.take(8).map { it.key to it.value }, d.totalMs, 1, perDay = false, vm, navigate)
}

// -------------------------------------------------------------------------------------- pieces

private fun LazyListScope.appsSection(
    apps: List<Pair<String, AppDay>>, totalMs: Long, days: Int, perDay: Boolean, vm: AppViewModel, navigate: (String) -> Unit,
) {
    if (apps.isEmpty() || totalMs <= 0) return
    item(key = "apps-h") { SectionHeader(if (perDay) "Most used apps · per day" else "Apps this day") }
    val max = apps.first().second.ms.coerceAtLeast(1)
    apps.forEach { (pkg, a) ->
        item(key = "app-$pkg") {
            Row(
                Modifier.fillMaxWidth().clickable { navigate("${Routes.APP}/$pkg") }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(pkg)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(vm.labelOf(pkg), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(formatMinutesShort(a.ms / days), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Bar(a.ms / max.toFloat(), "${vm.labelOf(pkg)}: ${(a.ms * 100f / totalMs).roundToInt()}%")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(a.ms * 100f / totalMs).roundToInt()}% of screen time",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private class Tile(val icon: ImageVector, val value: String, val label: String)

/** Two tiles per row: an icon, a big value, and what it measures. */
@Composable
private fun TileGrid(tiles: List<Tile>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    Panel(Modifier.weight(1f), contentPadding = PaddingValues(14.dp)) {
                        Icon(t.icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(t.value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                        Text(
                            t.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** "↓ 12%" in moss when less (good), "↑ 8%" in ember when more, then what it's compared with. */
@Composable
private fun DeltaChip(value: Long, base: Long, against: String) {
    val pct = ((value - base) * 100f / base).roundToInt()
    val color = when {
        pct < 0 -> LocalAccents.current.moss
        pct > 0 -> LocalAccents.current.ember
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        when {
            pct == 0 -> "= same"
            pct < 0 -> "↓ ${abs(pct)}%"
            else -> "↑ $pct%"
        },
        style = MaterialTheme.typography.labelLarge, color = color,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
    Spacer(Modifier.width(8.dp))
    Text(against, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun GoalBar(usedMs: Long, goalMinutes: Int) {
    val goalMs = goalMinutes * 60_000L
    val over = usedMs > goalMs
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Goal ${formatLimit(goalMinutes)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (over) "${formatMinutesShort(usedMs - goalMs)} over" else "within",
            style = MaterialTheme.typography.bodyMedium,
            color = if (over) LocalAccents.current.ember else LocalAccents.current.moss,
        )
    }
    Spacer(Modifier.height(6.dp))
    Bar(usedMs / goalMs.toFloat(), "Goal", if (over) LocalAccents.current.ember else MaterialTheme.colorScheme.onSurface)
}

/** Morning, afternoon, evening, night: four tiles with an icon, share and time; the biggest in ember. */
@Composable
private fun PartsOfDay(parts: List<Pair<String, Long>>) {
    val sum = parts.sumOf { it.second }.coerceAtLeast(1)
    val icons = listOf(Icons.Outlined.WbTwilight, Icons.Outlined.WbSunny, Icons.Outlined.Nightlight, Icons.Outlined.DarkMode)
    val peak = parts.indices.maxByOrNull { parts[it].second }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEachIndexed { idx, (name, ms) ->
            val hot = idx == peak
            Panel(
                Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
                color = if (hot) LocalAccents.current.ember.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icons[idx], null, tint = if (hot) LocalAccents.current.ember else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("${(ms * 100f / sum).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
                    Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1)
                    Text(formatMinutesShort(ms), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LabeledBar(label: String, value: String, fraction: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Bar(fraction, "$label: $value")
    }
}

@Composable
private fun Bar(fraction: Float, description: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Box(
        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(LocalAccents.current.rule)
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

/** 24 bars, one per hour; tap one to read it. The busiest hour is ember. */
@Composable
private fun HourChart(perHour: List<Long>, perDayLabel: Boolean = true) {
    val peak = perHour.indices.maxByOrNull { perHour[it] } ?: 0
    var selected by remember(perHour) { mutableIntStateOf(peak) }
    val maxMs = (perHour.maxOrNull() ?: 0L).coerceAtLeast(60_000L).toFloat()
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val ink = MaterialTheme.colorScheme.onSurface
    val ember = LocalAccents.current.ember
    val rule = LocalAccents.current.rule
    Column {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(hourRange(selected) + if (selected == peak) " · busiest" else "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(formatMinutesShort(perHour[selected]) + if (perDayLabel) " a day" else "", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(110.dp)) {
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                drawLine(rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                val slot = size.width / 24
                val barW = slot - 2.dp.toPx() // 2 dp surface gap between bars.
                val r = 2.dp.toPx()
                perHour.forEachIndexed { h, ms ->
                    val bh = (size.height * (ms / maxMs) * grow.value).coerceAtLeast(if (ms > 0) 2.dp.toPx() else 0f)
                    if (bh <= 0f) return@forEachIndexed
                    val x = slot * h + 1.dp.toPx()
                    val path = Path().apply {
                        addRoundRect(RoundRect(x, size.height - bh, x + barW, size.height, topLeftCornerRadius = CornerRadius(r, r), topRightCornerRadius = CornerRadius(r, r)))
                    }
                    drawPath(path, if (h == selected) ember else ink.copy(alpha = 0.78f))
                }
            }
            Row(Modifier.fillMaxWidth().height(110.dp)) {
                perHour.forEachIndexed { h, ms ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .selectable(h == selected, role = Role.Tab, onClick = { selected = h })
                            .semantics { contentDescription = "${hourRange(h)}: ${formatMinutesShort(ms)}" },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf(0, 6, 12, 18).forEach { h ->
                Text(formatClockMinutes(h * 60), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun hourRange(h: Int) = "${formatClockMinutes(h * 60)} – ${formatClockMinutes(((h + 1) % 24) * 60)}"

private fun visit(ms: Long) = if (ms < 60_000) "< 1m" else formatMinutesShort(ms)

private fun dayShort(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "today"
    today.minusDays(1) -> "yesterday"
    else -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
}

/** Four weekly averages per day, oldest first, as columns; this window is ember. */
@Composable
private fun WeeksChart(totals: List<Long>, daysInLast: Int) {
    val perDay = totals.mapIndexed { idx, ms -> if (idx == totals.lastIndex) ms / daysInLast.coerceAtLeast(1) else ms / 7 }
    val max = (perDay.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    val labels = listOf("3 wk ago", "2 wk ago", "Last wk", "This wk")
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        perDay.forEachIndexed { idx, ms ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(if (ms > 0) formatMinutesShort(ms) else "–", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth(0.6f).fillMaxHeight((ms / max * 0.75f).coerceAtLeast(0.01f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (idx == perDay.lastIndex) LocalAccents.current.ember else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)),
                )
                Spacer(Modifier.height(6.dp))
                Text(labels.getOrElse(idx) { "" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
