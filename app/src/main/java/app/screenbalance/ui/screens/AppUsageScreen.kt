package app.screenbalance.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.screenbalance.ui.AppViewModel
import app.screenbalance.ui.DayUsage
import app.screenbalance.ui.components.AppIcon
import app.screenbalance.ui.components.Figure
import app.screenbalance.ui.components.HairlineRule
import app.screenbalance.ui.components.KickerText
import app.screenbalance.ui.components.NavRow
import app.screenbalance.ui.util.formatLimit
import app.screenbalance.ui.util.formatMinutesShort
import app.screenbalance.ui.util.splitDuration
import java.time.LocalDate

/**
 * One app's screen time: the chosen day, a week of daily bars (earlier weeks with the arrows),
 * the week's total and daily average, and its daily limit.
 */
@Composable
fun AppUsageScreen(vm: AppViewModel, pkg: String, onBack: () -> Unit, onOpenLimits: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val phoneApps by vm.phoneApps.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadPhoneApps() }
    val label = remember(pkg) { vm.labelOf(pkg) }

    val today = LocalDate.now()
    var weeksBack by rememberSaveable { mutableIntStateOf(0) }
    var selectedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val end = weekEnd(today, weeksBack)
    val shownDay = selectedEpochDay?.let(LocalDate::ofEpochDay)?.takeIf { !it.isAfter(today) } ?: end
    // Reloads as today's usage changes (the live week only).
    val week by produceState<List<DayUsage>?>(null, pkg, end, if (weeksBack == 0) usage else null) {
        value = vm.week(end, pkg)
    }
    val days = week.orEmpty()
    val dayMs = days.firstOrNull { it.day == shownDay }?.totalMs ?: 0L
    val elapsed = days.count { !it.day.isAfter(today) }.coerceAtLeast(1)
    val totalMs = days.sumOf { it.totalMs }

    Page(label, onBack = onBack) {
        item(key = "head") {
            AppIcon(pkg)
            Spacer(Modifier.height(24.dp))
            KickerText(if (shownDay == today) "Screen time today" else "Screen time · ${dayLabel(shownDay, today)}")
            Spacer(Modifier.height(4.dp))
            val (v, unit) = splitDuration(dayMs)
            Figure(v, unit, big = true)
        }

        item(key = "week") {
            Spacer(Modifier.height(24.dp))
            WeekNavigator(
                label = weekLabel(today, weeksBack),
                canGoBack = weeksBack < MAX_WEEKS_BACK && totalMs > 0,
                canGoForward = weeksBack > 0,
                onBack = { weeksBack++; selectedEpochDay = weekEnd(today, weeksBack).toEpochDay() },
                onForward = { weeksBack--; selectedEpochDay = weekEnd(today, weeksBack).toEpochDay() },
            )
            Spacer(Modifier.height(8.dp))
            WeekChart(days, shownDay) { selectedEpochDay = it.toEpochDay() }
            if (week != null && totalMs == 0L && weeksBack > 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Nothing recorded these days. History builds up from when Glance was installed.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "totals") {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    KickerText("These 7 days")
                    Text(formatMinutesShort(totalMs), style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    KickerText("Daily average")
                    Text(formatMinutesShort(totalMs / elapsed), style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.height(20.dp))
            HairlineRule()
        }

        item(key = "limit") {
            val s = settings ?: return@item
            if (pkg in phoneApps.alwaysOpen) return@item
            val limit = s.limitForPackage(pkg)
            NavRow(
                "Daily limit",
                if (limit > 0) "${formatLimit(limit)} a day" else "Set how much of $label you want per day",
                onOpenLimits,
                trailing = if (limit > 0) formatLimit(limit) else "None",
            )
        }
    }
}
