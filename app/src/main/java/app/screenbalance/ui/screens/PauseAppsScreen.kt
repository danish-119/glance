package app.screenbalance.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.screenbalance.ui.AppViewModel
import app.screenbalance.ui.components.AppListRow
import app.screenbalance.ui.components.AppSearchField
import app.screenbalance.ui.components.matching
import app.screenbalance.ui.components.InfoButton
import app.screenbalance.ui.components.LoadingAppRows
import app.screenbalance.ui.components.HairlineRule
import app.screenbalance.ui.components.InkSwitch
import app.screenbalance.ui.components.Segmented
import app.screenbalance.ui.components.ToggleRow

/**
 * The 10-second pause before an app opens: on or off, for all apps or chosen ones, then every app
 * on the phone with its own switch.
 */
@Composable
fun PauseAppsScreen(vm: AppViewModel, padding: PaddingValues) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val policy by vm.policy.collectAsStateWithLifecycle()
    val phoneApps by vm.phoneApps.collectAsStateWithLifecycle()
    val s = settings ?: return
    LaunchedEffect(Unit) { vm.loadPhoneApps() }
    val pause = s.openPause
    var query by rememberSaveable { mutableStateOf("") }

    Page(
        "Pause before opening", contentPadding = padding,
        actions = {
            InfoButton(
                "Pause before opening",
                "Each time you open one of these apps, a 10-second pause with a short thought comes first; then open it, " +
                    "or put it away. Open it and you won't be asked again for a minute.\n\nThe home screen, the keyboard " +
                    "and the phone dialer are never paused.",
            )
        },
    ) {
        policy?.takeIf { it.locked }?.let { p -> item(key = "locked") { LockedBanner(p) } }
        item(key = "intro") {
            ToggleRow("10-second pause", null, pause.enabled,
                { v -> vm.update { it.copy(openPause = it.openPause.copy(enabled = v)) } })
        }
        if (!pause.enabled) return@Page
        item(key = "mode") {
            Segmented(
                listOf(false, true), pause.allApps,
                { all -> if (all) "All apps" else "Chosen apps" },
                { all -> vm.update { it.copy(openPause = it.openPause.copy(allApps = all)) } },
            )
            Spacer(Modifier.height(8.dp))
        }
        val apps = phoneApps.all
        if (apps.isEmpty()) {
            item(key = "loading") { LoadingAppRows() }
        } else {
            item(key = "search") {
                AppSearchField(query, { query = it })
                Spacer(Modifier.height(4.dp))
            }
        }
        items(apps.matching(query), key = { it.packageName }) { a ->
            if (a.packageName in phoneApps.neverPause) {
                AppListRow(a.packageName, a.label) {
                    InfoButton(
                        a.label,
                        "The pause isn't available for the home screen, keyboards and the phone dialer, so you can " +
                            "always get around and make a call.",
                    )
                }
                HairlineRule()
                return@items
            }
            val on = pause.appliesTo(a.packageName)
            AppListRow(
                a.packageName, a.label,
                Modifier.toggleable(on, role = Role.Switch) { v ->
                    vm.update { it.copy(openPause = it.openPause.withApp(a.packageName, v)) }
                },
            ) { InkSwitch(on) }
            HairlineRule()
        }
    }
}
