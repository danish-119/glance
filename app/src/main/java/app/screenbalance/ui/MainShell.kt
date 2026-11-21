package app.screenbalance.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.ShieldMoon
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.screenbalance.ui.components.HairlineRule
import app.screenbalance.ui.screens.AppUsageScreen
import app.screenbalance.ui.screens.InsightsScreen
import app.screenbalance.ui.screens.LimitsScreen
import app.screenbalance.ui.screens.PauseAppsScreen
import app.screenbalance.ui.screens.PrivacyScreen
import app.screenbalance.ui.screens.RulesScreen
import app.screenbalance.ui.screens.SettingsScreen
import app.screenbalance.ui.screens.TodayScreen

object Routes {
    const val TODAY = "today"
    const val LIMITS = "limits"
    const val PAUSE = "pause"
    const val RULES = "rules"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    /** One app's screen time: "app/<package>". */
    const val APP = "app"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Today", Icons.Outlined.WbTwilight),
    Tab(Routes.INSIGHTS, "Insights", Icons.Outlined.Insights),
    Tab(Routes.LIMITS, "Limits", Icons.Outlined.Timer),
    Tab(Routes.PAUSE, "Pause", Icons.Outlined.PauseCircle),
    Tab(Routes.RULES, "Rules", Icons.Outlined.ShieldMoon),
)

@Composable
fun MainShell(vm: AppViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        vm.route.collect { r ->
            if (r == null) return@collect
            vm.routeOpened()
            if (tabs.any { it.route == r }) nav.navigate(r) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
        }
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (showBar) BottomBar(nav, entry?.destination) },
    ) { padding ->
        // The graph below is built once; read the bar's height through state so every tab sees its real height.
        val bottom by rememberUpdatedState(padding.calculateBottomPadding())
        NavHost(
            navController = nav,
            startDestination = Routes.TODAY,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
        ) {
            composable(Routes.TODAY) { TodayScreen(vm, PaddingValues(bottom = bottom), nav::navigate) }
            composable(Routes.INSIGHTS) { InsightsScreen(vm, PaddingValues(bottom = bottom), nav::navigate) }
            composable(Routes.LIMITS) { LimitsScreen(vm, PaddingValues(bottom = bottom)) }
            composable(Routes.PAUSE) { PauseAppsScreen(vm, PaddingValues(bottom = bottom)) }
            composable(Routes.RULES) { RulesScreen(vm, PaddingValues(bottom = bottom)) }
            composable(
                Routes.SETTINGS,
                enterTransition = { slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280)) },
                popExitTransition = { slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(220)) },
            ) { SettingsScreen(vm, onBack = nav::popBackStack, navigate = nav::navigate) }
            composable(Routes.PRIVACY) { PrivacyScreen(onBack = nav::popBackStack) }
            composable(
                "${Routes.APP}/{pkg}",
                enterTransition = { slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280)) },
                popExitTransition = { slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(220)) },
            ) { e ->
                AppUsageScreen(
                    vm, e.arguments?.getString("pkg").orEmpty(), onBack = nav::popBackStack,
                    onOpenLimits = {
                        nav.navigate(Routes.LIMITS) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, destination: androidx.navigation.NavDestination?) {
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        HairlineRule()
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = androidx.compose.ui.unit.Dp.Hairline,
        ) {
            tabs.forEach { tab ->
                val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        nav.navigate(tab.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}
