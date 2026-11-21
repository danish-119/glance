package app.screenbalance.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.screenbalance.ui.util.formatClock
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.screenbalance.engine.EffectivePolicy
import app.screenbalance.engine.WatchedApp
import app.screenbalance.model.QuietApps
import app.screenbalance.model.Schedule
import app.screenbalance.ui.LaunchableApp
import androidx.compose.foundation.lazy.LazyColumn
import app.screenbalance.ui.AppViewModel
import app.screenbalance.ui.components.AppListRow
import app.screenbalance.ui.components.AppSearchField
import app.screenbalance.ui.components.matching
import app.screenbalance.ui.components.EmptyNote
import app.screenbalance.ui.components.LoadingAppRows
import app.screenbalance.ui.components.HairlineRule
import app.screenbalance.ui.components.InfoButton
import app.screenbalance.ui.components.InkSwitch
import app.screenbalance.ui.components.NavRow
import app.screenbalance.ui.components.Panel
import app.screenbalance.ui.components.PrimaryButton
import app.screenbalance.ui.components.SecondaryButton
import app.screenbalance.ui.components.SectionHeader
import app.screenbalance.ui.components.Segmented
import app.screenbalance.ui.components.ToggleRow
import app.screenbalance.ui.theme.LocalAccents
import app.screenbalance.ui.util.formatClockMinutes
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

@Composable
fun RulesScreen(vm: AppViewModel, padding: PaddingValues) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val policy by vm.policy.collectAsStateWithLifecycle()
    val s = settings ?: return
    val rules = s.rules
    var editing by remember { mutableStateOf<Schedule?>(null) }
    val status by vm.status.collectAsStateWithLifecycle()
    val apps = status.installedApps.toList()
    val phoneApps by vm.phoneApps.collectAsStateWithLifecycle()
    var askNotificationAccess by rememberSaveable { mutableStateOf(false) }
    var choosingQuietApps by rememberSaveable { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { vm.loadPhoneApps() }

    if (askNotificationAccess) {
        ConfirmDialog(
            title = "Allow notification access?",
            body = "To hold back an app's notifications while it's closed (quiet hours, focus sessions, its daily limit), " +
                "Glance needs Android's notification access. It only checks which app a notification comes from; " +
                "those are snoozed until the app opens again. Glance never reads what a notification says, and nothing is stored or sent.",
            confirm = "Continue",
            onConfirm = {
                askNotificationAccess = false
                app.screenbalance.ui.SystemIntents.openNotificationAccessSettings(ctx)
            },
            onDismiss = { askNotificationAccess = false },
        )
    }

    Page("Rules", contentPadding = padding) {
        policy?.takeIf { it.locked }?.let { p -> item(key = "locked") { LockedBanner(p) } }

        val sectioned = apps.filter { it.shortVideoLabel != null }
        if (sectioned.isNotEmpty()) item(key = "rules") {
            SectionHeader("Closed in apps") {
                InfoButton(
                    "Closed in apps",
                    "Messages, profiles, search and notifications always stay open.\n\n" +
                        "Instagram Reels: the Reels tab and swiping through Reels; Instagram goes to your profile instead. " +
                        "A Reel a friend sends you still plays, and swiping away brings you back to it.\n" +
                        "Instagram Explore: the grid under the search tab. Searching by name still works.\n" +
                        "Instagram Home feed: the Home tab, its feed and the stories row; Instagram opens on your profile.\n" +
                        "YouTube Shorts, Facebook Reels and Video, Snapchat Spotlight: the app moves to another tab.\n\n" +
                        "TikTok, X and Reddit are their feeds, so they're controlled as whole apps: daily limit, quiet hours, focus.",
                )
            }
            sectioned.forEachIndexed { i, a ->
                if (i > 0) HairlineRule()
                when (a) {
                    WatchedApp.INSTAGRAM -> {
                        ToggleRow("Instagram Reels", null, rules.blockReels,
                            { v -> vm.updateRules { it.copy(blockReels = v) } })
                        HairlineRule()
                        ToggleRow("Instagram Explore", null, rules.blockExplore,
                            { v -> vm.updateRules { it.copy(blockExplore = v) } })
                        HairlineRule()
                        ToggleRow("Instagram Home feed", null, rules.blockHomeFeed,
                            { v -> vm.updateRules { it.copy(blockHomeFeed = v) } })
                    }
                    WatchedApp.YOUTUBE -> ToggleRow("YouTube Shorts", null, rules.blockShorts,
                        { v -> vm.updateRules { it.copy(blockShorts = v) } })
                    WatchedApp.FACEBOOK -> ToggleRow("Facebook Reels and Video", null, rules.blockFacebookVideo,
                        { v -> vm.updateRules { it.copy(blockFacebookVideo = v) } })
                    WatchedApp.SNAPCHAT -> ToggleRow("Snapchat Spotlight", null, rules.blockSpotlight,
                        { v -> vm.updateRules { it.copy(blockSpotlight = v) } })
                    WatchedApp.TIKTOK, WatchedApp.X, WatchedApp.REDDIT -> Unit
                }
            }
        }

        item(key = "phone") {
            SectionHeader("Reminder") {
                InfoButton(
                    "Reminder",
                    "A reminder appears after that many minutes in the same app, and again each time that much " +
                        "more passes. It starts over when you switch apps, go home or lock the phone. Nothing is locked.",
                )
            }
            Text("Remind me after this long in one app", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Segmented(
                REMINDER_OPTIONS, s.reminderEveryMinutes,
                { if (it == 0) "Off" else "$it min" },
                { v -> vm.update { it.copy(reminderEveryMinutes = v) } },
            )
        }

        item(key = "sched-h") {
            SectionHeader("Quiet hours") {
                InfoButton(
                    "Quiet hours",
                    "During quiet hours and focus sessions the apps you choose are closed, and every app's notifications wait " +
                        "until they end. Phone, messages, contacts, maps, camera, clock and Settings always work, and calls and " +
                        "messages always come through. Every setting stays changeable meanwhile.",
                )
                TextButton(onClick = { editing = newSchedule() }) { Text("Add") }
            }
        }
        if (s.schedules.isEmpty()) {
            item(key = "sched-empty") {
                Panel {
                    EmptyNote("No quiet hours yet", "For example 10 PM to 7 AM.")
                    SecondaryButton("Add quiet hours", { editing = newSchedule() })
                }
            }
        } else {
            items(s.schedules, key = { it.id }) { sch ->
                ScheduleCard(
                    sch,
                    onToggle = { on -> vm.update { st -> st.copy(schedules = st.schedules.map { if (it.id == sch.id) it.copy(enabled = on) else it }) } },
                    onEdit = { editing = sch },
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        item(key = "keep-open") {
            val q = s.quietApps
            NavRow("Apps that close", null, { choosingQuietApps = true },
                trailing = when {
                    !q.allApps -> if (q.apps.size == 1) "1 app" else "${q.apps.size} apps"
                    q.exceptApps.isEmpty() -> "All apps"
                    else -> "All but ${q.exceptApps.size}"
                })
        }

        if (!status.notificationAccess) item(key = "quiet") {
            NavRow("Allow notification access", null, { askNotificationAccess = true }, trailing = "Off")
        }

        item(key = "strict") {
            SectionHeader("Strict mode") {
                InfoButton(
                    "Strict mode",
                    "Decide now, for the moments you'd give in. While it applies, you can still make rules stricter, but " +
                        "loosening anything (a limit, quiet hours, strict mode itself) waits until it ends, a focus session " +
                        "can't be ended early, and Glance's switch in Accessibility settings is guarded.\n\n" +
                        "Emergency access on the lock screen always works, and calls, messages, maps and the camera are never closed.",
                )
            }
            ToggleRow("During focus sessions", null, s.strict.focusSessions,
                { v -> vm.update { it.copy(strict = it.strict.copy(focusSessions = v)) } })
            HairlineRule()
            ToggleRow("During quiet hours", null, s.strict.quietHours,
                { v -> vm.update { it.copy(strict = it.strict.copy(quietHours = v)) } })
        }
    }

    editing?.let { sch ->
        ScheduleEditor(
            initial = sch,
            isNew = s.schedules.none { it.id == sch.id },
            onDismiss = { editing = null },
            onSave = { saved ->
                vm.update { st ->
                    val exists = st.schedules.any { it.id == saved.id }
                    st.copy(schedules = if (exists) st.schedules.map { if (it.id == saved.id) saved else it } else st.schedules + saved)
                }
                editing = null
            },
            onDelete = { id ->
                vm.update { st -> st.copy(schedules = st.schedules.filterNot { it.id == id }) }
                editing = null
            },
        )
    }

    if (choosingQuietApps) {
        QuietAppsSheet(
            quiet = s.quietApps,
            apps = phoneApps.all,
            alwaysOpen = phoneApps.alwaysOpen,
            onChange = { change -> vm.update { it.copy(quietApps = change(it.quietApps)) } },
            onDismiss = { choosingQuietApps = false },
        )
    }
}

private val REMINDER_OPTIONS = listOf(0, 5, 10, 15, 30)

/** Which apps quiet hours and focus sessions close: chosen ones, or all; each with its own switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietAppsSheet(
    quiet: QuietApps,
    apps: List<LaunchableApp>,
    alwaysOpen: Set<String>,
    onChange: ((QuietApps) -> QuietApps) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Apps that close", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "During quiet hours and focus sessions. Phone, messages, contacts, maps, camera, clock and Settings always stay open.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Segmented(
                listOf(false, true), quiet.allApps,
                { all -> if (all) "All apps" else "Chosen apps" },
                { all -> onChange { it.copy(allApps = all) } },
            )
            Spacer(Modifier.height(8.dp))
            if (apps.isNotEmpty()) AppSearchField(query, { query = it })
        }
        if (apps.isEmpty()) {
            Column(Modifier.padding(horizontal = 24.dp)) { LoadingAppRows() }
        } else {
            LazyColumn(Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
                items(apps.matching(query), key = { it.packageName }) { a ->
                    if (a.packageName in alwaysOpen) {
                        AppListRow(a.packageName, a.label) {
                            InfoButton(
                                a.label,
                                "Important system apps always stay open during quiet hours and focus sessions, so you " +
                                    "can call, message and find your way.",
                            )
                        }
                        HairlineRule()
                        return@items
                    }
                    val on = quiet.closes(a.packageName)
                    AppListRow(a.packageName, a.label, Modifier.toggleable(on, role = Role.Switch) { v ->
                        onChange { it.withApp(a.packageName, v) }
                    }) { InkSwitch(on) }
                    HairlineRule()
                }
            }
        }
    }
}

private fun newSchedule() = Schedule(
    id = UUID.randomUUID().toString(), name = "Night", days = (1..7).toSet(),
    startMinute = 22 * 60, endMinute = 7 * 60,
)

private fun daysLabel(days: Set<Int>): String = when (days) {
    (1..7).toSet() -> "Every day"
    (1..5).toSet() -> "Weekdays"
    setOf(6, 7) -> "Weekends"
    else -> days.sorted().joinToString(" ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

@Composable
private fun ScheduleCard(s: Schedule, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    Panel(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatClockMinutes(s.startMinute)} – ${formatClockMinutes(s.endMinute)} · ${daysLabel(s.days)}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.toggleable(s.enabled, role = Role.Switch, onValueChange = onToggle).padding(8.dp)) {
                InkSwitch(s.enabled)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleEditor(
    initial: Schedule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit,
    onDelete: (String) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var days by remember { mutableStateOf(initial.days) }
    var start by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(initial.startMinute) }
    var end by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(initial.endMinute) }
    var picking by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isNew) "New quiet hours" else "Edit quiet hours", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (!isNew) IconButton(onClick = { onDelete(initial.id) }) { Icon(Icons.Outlined.DeleteOutline, "Delete schedule") }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = LocalAccents.current.rule,
                ),
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (1..7).forEach { d ->
                    val on = d in days
                    val label = DayOfWeek.of(d).getDisplayName(TextStyle.NARROW, Locale.getDefault())
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else LocalAccents.current.rule, CircleShape)
                            .toggleable(on, role = Role.Checkbox, onValueChange = { days = if (it) days + d else days - d }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField("From", start, Modifier.weight(1f)) { picking = "start" }
                TimeField("Until", end, Modifier.weight(1f)) { picking = "end" }
            }
            if (end <= start) {
                Spacer(Modifier.height(8.dp))
                Text("Runs overnight, ending the next morning.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                "Save", enabled = days.isNotEmpty() && name.isNotBlank() && start != end,
                onClick = { onSave(initial.copy(name = name.trim(), days = days, startMinute = start, endMinute = end)) },
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    picking?.let { which ->
        TimeDialog(
            initialMinute = if (which == "start") start else end,
            onDismiss = { picking = null },
            onConfirm = { m -> if (which == "start") start = m else end = m; picking = null },
        )
    }
}

@Composable
private fun TimeField(label: String, minute: Int, modifier: Modifier, onClick: () -> Unit) {
    Panel(modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(formatClockMinutes(minute), style = MaterialTheme.typography.headlineMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialMinute / 60, initialMinute % 60)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            TimePicker(
                state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectorColor = MaterialTheme.colorScheme.primary,
                    timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
                    timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
                    periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    )
}

/** While strict mode applies: what is on, until when, and that only loosening waits. */
@Composable
fun LockedBanner(policy: EffectivePolicy) {
    val what = if (policy.source == EffectivePolicy.Source.FOCUS_SESSION) "Focus session" else "Quiet hours"
    val until = policy.until
    Spacer(Modifier.height(16.dp))
    Panel(color = MaterialTheme.colorScheme.inverseSurface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                (if (until != null) "$what until ${formatClock(until)}. " else "$what. ") +
                    "Strict mode: you can make rules stricter now; loosening them waits until it ends.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}
