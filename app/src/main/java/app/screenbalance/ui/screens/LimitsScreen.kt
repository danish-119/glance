package app.screenbalance.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.screenbalance.ui.AppViewModel
import app.screenbalance.ui.LaunchableApp
import app.screenbalance.ui.components.AppListRow
import app.screenbalance.ui.components.AppSearchField
import app.screenbalance.ui.components.matching
import app.screenbalance.ui.components.InfoButton
import app.screenbalance.ui.components.LoadingAppRows
import app.screenbalance.ui.components.HairlineRule
import app.screenbalance.ui.components.ScrollPicker
import app.screenbalance.ui.theme.LocalAccents
import app.screenbalance.ui.util.formatLimit

/**
 * Daily limits for every app on the phone that can be closed, alphabetically, each one the same:
 * tap an app to set its limit with a ruler.
 */
@Composable
fun LimitsScreen(vm: AppViewModel, padding: PaddingValues) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val policy by vm.policy.collectAsStateWithLifecycle()
    val phoneApps by vm.phoneApps.collectAsStateWithLifecycle()
    val s = settings ?: return
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadPhoneApps() }
    val apps = phoneApps.all

    Page(
        "Daily limits", contentPadding = padding,
        actions = {
            InfoButton(
                "Daily limits",
                "How much of each app you want per day. Tap an app to set its limit. When its time is used up, it closes " +
                    "until tomorrow and its notifications wait.\n\nPhone, messages, maps and the other essentials are never limited.",
            )
        },
    ) {
        policy?.takeIf { it.locked }?.let { p -> item(key = "locked") { LockedBanner(p) } }
        if (apps.isEmpty()) {
            item(key = "loading") { LoadingAppRows() }
        } else {
            item(key = "search") {
                AppSearchField(query, { query = it })
                Spacer(Modifier.height(4.dp))
            }
        }
        items(apps.matching(query), key = { it.packageName }) { a ->
            if (a.packageName in phoneApps.alwaysOpen) {
                AppListRow(a.packageName, a.label) {
                    InfoButton(
                        a.label,
                        "Can't set an app timer: timers aren't available for important system apps, so calling, " +
                            "messaging and finding your way always work.",
                    )
                }
                HairlineRule()
                return@items
            }
            val limit = s.limitForPackage(a.packageName)
            AppListRow(a.packageName, a.label, Modifier.clickable(role = Role.Button) { editing = a.packageName }) {
                Text(
                    if (limit > 0) formatLimit(limit) else "No limit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (limit > 0) LocalAccents.current.ember else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HairlineRule()
        }
    }

    editing?.let { pkg ->
        val app = apps.firstOrNull { it.packageName == pkg } ?: LaunchableApp(pkg, pkg)
        LimitSheet(
            app = app,
            minutes = s.limitForPackage(pkg),
            onChange = { v -> vm.update { it.withPackageLimit(pkg, v) } },
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LimitSheet(app: LaunchableApp, minutes: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
            AppListRow(app.packageName, app.label) {}
            Spacer(Modifier.height(8.dp))
            ScrollPicker(
                "Per day", minutes, onChange, 0..240, 5,
                { if (it == 0) "No limit" else formatLimit(it) },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
