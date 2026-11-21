package app.screenbalance.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.screenbalance.engine.EffectivePolicy
import app.screenbalance.model.AppSettings
import app.screenbalance.ui.AppToday
import app.screenbalance.ui.DayDetail
import app.screenbalance.ui.DayUsage
import app.screenbalance.ui.AppViewModel
import app.screenbalance.ui.Routes
import app.screenbalance.ui.SystemIntents
import app.screenbalance.ui.art.ProtectionPulse
import app.screenbalance.ui.components.Figure
import app.screenbalance.ui.components.AppListRow
import app.screenbalance.ui.components.HairlineRule
import app.screenbalance.ui.components.NavRow
import app.screenbalance.ui.components.KickerText
import app.screenbalance.ui.components.Panel
import app.screenbalance.ui.components.PrimaryButton
import app.screenbalance.ui.components.SecondaryButton
import app.screenbalance.ui.components.Segmented
import app.screenbalance.ui.theme.LocalAccents
import app.screenbalance.ui.util.formatLimit
import app.screenbalance.ui.util.formatMinutesShort
import app.screenbalance.ui.util.formatClock
import app.screenbalance.ui.util.formatRemaining
import app.screenbalance.ui.util.splitDuration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(vm: AppViewModel, padding: PaddingValues, navigate: (String) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val policy by vm.policy.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val phoneToday by vm.phoneTodayMs.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val s = settings ?: return
    val ctx = LocalContext.current
    var showDisclosure by rememberSaveable { mutableStateOf(false) }
    var choosingGoal by rememberSaveable { mutableStateOf(false) }
    val runtime by vm.runtime.collectAsStateWithLifecycle()
    val streak by produceState(0, s.dailyGoalMinutes, LocalDate.now()) { value = vm.goalStreak(s.dailyGoalMinutes) }

    // With usage access, the total of the apps listed below; without it, Glance's own screen-on measurement.
    val todayMs = usage.todayMs ?: maxOf(phoneToday, usage.week.lastOrNull()?.totalMs ?: 0L)

    // The week shown (0 = the past 7 days) and the day chosen in it (null = today).
    val today = LocalDate.now()
    var weeksBack by rememberSaveable { mutableIntStateOf(0) }
    var selectedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val shownDay = selectedEpochDay?.let(LocalDate::ofEpochDay)?.takeIf { !it.isAfter(today) } ?: today
    val pastWeek by produceState<List<DayUsage>?>(null, weeksBack, today) {
        value = if (weeksBack == 0) null else vm.week(weekEnd(today, weeksBack))
    }
    val week = if (weeksBack == 0) usage.week else pastWeek.orEmpty()
    val pastDetail by produceState<DayDetail?>(null, shownDay) {
        value = null
        if (shownDay != today) value = vm.dayDetail(shownDay)
    }
    val detail = if (shownDay == today) DayDetail(today, todayMs, usage.apps) else pastDetail
    val dayApps = detail?.apps.orEmpty()

    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

    Page(
        title = "$greeting.",
        contentPadding = padding,
        actions = { IconButton(onClick = { navigate(Routes.SETTINGS) }) { Icon(Icons.Outlined.Settings, "Settings") } },
    ) {
        item(key = "date") {
            KickerText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())))
            Spacer(Modifier.height(20.dp))
        }

        item(key = "status") {
            StatusBlock(
                active = status.protectionActive,
                enabled = status.serviceEnabled,
                modeLabel = modeLabel(policy),
                onFix = {
                    if (s.disclosureVersionAccepted < AppSettings.CURRENT_DISCLOSURE_VERSION) showDisclosure = true
                    else SystemIntents.openAccessibilitySettings(ctx)
                },
            )
        }

        item(key = "emergency") {
            val until = runtime?.emergencyUntilMs ?: 0L
            if (until > now) {
                Spacer(Modifier.height(16.dp))
                Panel(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("Emergency access", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "Every app is open for ${formatRemaining(until - now)} more.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        item(key = "active") {
            val p = policy
            AnimatedVisibility(
                visible = p != null && p.source != EffectivePolicy.Source.NORMAL,
                enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically(),
            ) {
                if (p != null) ActivePanel(p, now, onEndSession = vm::endFocus, onEditQuietHours = { navigate(Routes.RULES) })
            }
        }

        item(key = "figure") {
            Spacer(Modifier.height(32.dp))
            KickerText(if (shownDay == today) "Screen time today" else "Screen time · ${dayLabel(shownDay, today)}")
            Spacer(Modifier.height(4.dp))
            val (v, unit) = splitDuration(detail?.totalMs ?: 0L)
            Figure(v, unit, big = true)
            GoalLine(s.dailyGoalMinutes, detail?.totalMs ?: 0L, shownDay == today, streak, onSet = { choosingGoal = true })
        }

        item(key = "week") {
            Spacer(Modifier.height(24.dp))
            WeekNavigator(
                label = weekLabel(today, weeksBack),
                canGoBack = weeksBack < MAX_WEEKS_BACK && week.any { it.totalMs > 0 },
                canGoForward = weeksBack > 0,
                onBack = { weeksBack++; selectedEpochDay = weekEnd(today, weeksBack).toEpochDay() },
                onForward = { weeksBack--; selectedEpochDay = weekEnd(today, weeksBack).toEpochDay() },
            )
            Spacer(Modifier.height(8.dp))
            WeekChart(week, shownDay) { selectedEpochDay = it.toEpochDay() }
        }

        item(key = "apps-h") {
            Spacer(Modifier.height(32.dp))
            KickerText(if (shownDay == today) "Apps" else "Apps · ${dayLabel(shownDay, today)}")
            Spacer(Modifier.height(4.dp))
            if (!status.usageAccess) {
                NavRow("See every app", "Allow usage access for the screen time of all your apps",
                    { SystemIntents.openUsageAccessSettings(ctx) }, trailing = "Allow")
            } else if (detail != null && dayApps.isEmpty()) {
                Text(
                    when {
                        shownDay == today -> "Nothing yet today."
                        // Glance's history starts when it was installed (plus the week Android still had then).
                        shownDay.isBefore(today.minusDays(7)) -> "Nothing recorded on this day. History builds up from when Glance was installed."
                        else -> "Nothing recorded on this day."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(dayApps, key = { "app-${it.packageName}" }) { a ->
            AppRow(a, onClick = { navigate("${Routes.APP}/${a.packageName}") })
            HairlineRule()
        }

        item(key = "focus") {
            val p = policy
            if (p == null || p.source == EffectivePolicy.Source.NORMAL) {
                Spacer(Modifier.height(28.dp))
                FocusStarter(onStart = vm::startFocus)
            }
        }
    }

    if (choosingGoal) {
        GoalSheet(s.dailyGoalMinutes, onChange = { v -> vm.update(allowWhenLocked = true) { it.copy(dailyGoalMinutes = v) } }, onDismiss = { choosingGoal = false })
    }

    if (showDisclosure) {
        ConfirmDialog(
            title = "Accessibility disclosure",
            body = ACCESSIBILITY_DISCLOSURE,
            confirm = "I agree",
            dismiss = "Not now",
            onConfirm = {
                showDisclosure = false
                vm.acceptDisclosure()
                SystemIntents.openAccessibilitySettings(ctx)
            },
            onDismiss = { showDisclosure = false },
        )
    }
}

private fun modeLabel(p: EffectivePolicy?): String = when (p?.source) {
    EffectivePolicy.Source.FOCUS_SESSION -> "Focus session: your chosen apps closed"
    EffectivePolicy.Source.SCHEDULE -> "Quiet hours: your chosen apps closed"
    else -> "Short videos and endless feeds closed"
}

/** One app: its icon, its screen time that day, and (today) how much of its own limit that is. Tap for its history. */
@Composable
private fun AppRow(a: AppToday, onClick: () -> Unit) {
    val limit = a.limitMinutes
    Column(
        Modifier.fillMaxWidth().clickable(onClickLabel = "See ${a.label}'s screen time", onClick = onClick)
            .padding(bottom = if (limit > 0) 10.dp else 0.dp),
    ) {
        AppListRow(a.packageName, a.label) {
            Text(
                formatMinutesShort(a.usedMs) + if (limit > 0) " of ${formatLimit(limit)}" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (limit > 0 && a.usedMs >= limit * 60_000L) LocalAccents.current.ember
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (limit > 0) {
            ProgressLine((a.usedMs / (limit * 60_000f)).coerceIn(0f, 1f), "${a.label}: ${formatMinutesShort(a.usedMs)} of ${formatLimit(limit)}")
        }
    }
}

@Composable
private fun StatusBlock(active: Boolean, enabled: Boolean, modeLabel: String, onFix: () -> Unit) {
    if (active) {
        // Two lines so it never crowds or clips on narrow screens or with large text.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProtectionPulse(true)
            Spacer(Modifier.width(6.dp))
            Text("Protection on", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            modeLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 37.dp),
        )
        return
    }
    Panel(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProtectionPulse(false)
            Spacer(Modifier.width(6.dp))
            Text(if (enabled) "Protection is starting" else "Protection is off", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (enabled) "Android has the service switched on but hasn't started it yet. If this doesn't change in a few seconds, switch it off and on again."
            else "Nothing is being blocked. Turn on Glance's accessibility service to start.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(if (enabled) "Open Accessibility settings" else "Turn on protection", onFix)
    }
}

@Composable
private fun ActivePanel(p: EffectivePolicy, now: Long, onEndSession: () -> Unit, onEditQuietHours: () -> Unit) {
    val until = p.until ?: return
    Spacer(Modifier.height(16.dp))
    Panel(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.inverseSurface) {
        val fg = MaterialTheme.colorScheme.inverseOnSurface
        KickerText(
            when (p.source) {
                EffectivePolicy.Source.FOCUS_SESSION -> "Focus session"
                else -> "Quiet hours"
            },
            color = LocalAccents.current.ember,
        )
        Spacer(Modifier.height(8.dp))
        Text(formatRemaining(until - now), style = MaterialTheme.typography.displaySmall, color = fg)
        Text(
            "left · until ${formatClock(until)} · your chosen apps closed",
            style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.7f),
        )
        if (p.locked) {
            Spacer(Modifier.height(12.dp))
            Text("Rules can only get stricter until it ends.", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = fg.copy(alpha = 0.7f))
        } else if (p.source == EffectivePolicy.Source.SCHEDULE) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.TextButton(onClick = onEditQuietHours) {
                Text("Change or turn off quiet hours", color = fg)
            }
        } else {
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.TextButton(onClick = onEndSession) {
                Text("End now", color = fg)
            }
        }
    }
}

/** A thin progress line; ember once full. */
@Composable
private fun ProgressLine(fraction: Float, description: String) {
    val animated by animateFloatAsState(fraction, tween(900), label = "progress")
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(LocalAccents.current.rule)
            .semantics { contentDescription = description },
    ) {
        Box(
            Modifier.fillMaxWidth(animated).height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(if (fraction >= 1f) LocalAccents.current.ember else MaterialTheme.colorScheme.onSurface),
        )
    }
}

@Composable
internal fun FocusStarter(onStart: (Int) -> Unit) {
    val options = listOf(25, 50, 90, 120)
    var minutes by rememberSaveable { mutableIntStateOf(50) }
    Panel {
        Text("Start a focus session", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Your chosen apps close and notifications wait until it's over. You can end it early any time.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Segmented(options, minutes, { "$it min" }, { minutes = it })
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Start $minutes-minute session", { onStart(minutes) })
        }
    }
}

/** The daily goal under the figure: how much is left (or how far over), the streak, or a way to set one. */
@Composable
private fun GoalLine(goalMinutes: Int, usedMs: Long, isToday: Boolean, streak: Int, onSet: () -> Unit) {
    if (goalMinutes <= 0) {
        if (isToday) androidx.compose.material3.TextButton(onClick = onSet, contentPadding = PaddingValues(0.dp)) {
            Text("Set a daily goal", style = MaterialTheme.typography.labelLarge)
        }
        return
    }
    val goalMs = goalMinutes * 60_000L
    Spacer(Modifier.height(8.dp))
    ProgressLine((usedMs / goalMs.toFloat()).coerceIn(0f, 1f), "${formatMinutesShort(usedMs)} of a ${formatLimit(goalMinutes)} goal")
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            when {
                usedMs > goalMs -> "${formatMinutesShort(usedMs - goalMs)} over your ${formatLimit(goalMinutes)} goal"
                isToday -> "${formatMinutesShort(goalMs - usedMs)} left of your ${formatLimit(goalMinutes)} goal"
                else -> "Within your ${formatLimit(goalMinutes)} goal"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (usedMs > goalMs) LocalAccents.current.ember else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.TextButton(onClick = onSet) { Text("Change") }
    }
    if (isToday && streak > 0) {
        Text(
            if (streak == 1) "Yesterday was within your goal." else "$streak days in a row within your goal.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GoalSheet(minutes: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    val sheet = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
            Text("Daily screen-time goal", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "A target, not a limit: nothing closes. Today shows how much is left, and Insights how many days you made it.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            app.screenbalance.ui.components.ScrollPicker(
                "Per day", minutes, onChange, 0..600, 15,
                { if (it == 0) "No goal" else formatLimit(it) },
                majorEvery = 60,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
