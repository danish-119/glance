package app.screenbalance.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.screenbalance.BuildConfig
import app.screenbalance.model.AppSettings
import app.screenbalance.model.ThemePreference
import app.screenbalance.ui.AppViewModel
import app.screenbalance.ui.Routes
import app.screenbalance.ui.SystemIntents
import app.screenbalance.ui.components.InfoButton
import app.screenbalance.ui.components.HairlineRule
import app.screenbalance.ui.components.NavRow
import app.screenbalance.ui.components.SectionHeader
import app.screenbalance.ui.components.Segmented
import app.screenbalance.ui.theme.LocalAccents

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, navigate: (String) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val policy by vm.policy.collectAsStateWithLifecycle()
    val s = settings ?: return
    val ctx = LocalContext.current
    val locked = policy?.locked == true
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Page("Settings", onBack = onBack) {
        policy?.takeIf { it.locked }?.let { p -> item(key = "locked") { LockedBanner(p) } }

        item(key = "look") {
            SectionHeader("Appearance")
            Segmented(
                ThemePreference.entries, s.theme,
                { when (it) { ThemePreference.SYSTEM -> "System"; ThemePreference.LIGHT -> "Light"; ThemePreference.DARK -> "Dark" } },
                { v -> vm.update(allowWhenLocked = true) { it.copy(theme = v) } },
            )
        }

        item(key = "summary") {
            SectionHeader("Weekly summary")
            val askNotify = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            ) { granted -> vm.update(allowWhenLocked = true) { it.copy(weeklySummary = granted) } }
            app.screenbalance.ui.components.ToggleRow(
                "Monday morning summary", "Last week's screen time, compared with the week before", s.weeklySummary,
                { v ->
                    if (v && android.os.Build.VERSION.SDK_INT >= 33 &&
                        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else vm.update(allowWhenLocked = true) { it.copy(weeklySummary = v) }
                },
            )
        }

        item(key = "perms") {
            SectionHeader("Permissions")
            NavRow("Accessibility service", "Required for blocking", { SystemIntents.openAccessibilitySettings(ctx) },
                Icons.Outlined.Accessibility, trailing = if (status.protectionActive) "On" else "Off")
            HairlineRule()
            NavRow("Usage access", "System screen-time totals", { SystemIntents.openUsageAccessSettings(ctx) },
                Icons.Outlined.BarChart, trailing = if (status.usageAccess) "On" else "Off")
            HairlineRule()
            NavRow("Notification access", "Hold back notifications of closed apps", { SystemIntents.openNotificationAccessSettings(ctx) },
                Icons.Outlined.Notifications, trailing = if (status.notificationAccess) "On" else "Off")
        }

        item(key = "more") {
            SectionHeader("Support")
            NavRow("Privacy and limitations", null, { navigate(Routes.PRIVACY) }, Icons.Outlined.Policy)
            HairlineRule()
            NavRow("Clear usage history", "Removes all stored statistics", { confirmClear = true }, Icons.Outlined.DeleteSweep)
        }

        item(key = "version") {
            Spacer(Modifier.height(28.dp))
            Text(
                "Glance ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = LocalAccents.current.faint,
            )
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear usage history?",
            body = "This removes every stored minute of usage history. Your rules and schedules stay.",
            confirm = "Clear",
            onConfirm = { vm.clearStats(); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }
}
