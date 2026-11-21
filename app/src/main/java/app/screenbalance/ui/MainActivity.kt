package app.screenbalance.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.screenbalance.model.ThemePreference
import app.screenbalance.ui.onboarding.OnboardingFlow
import app.screenbalance.ui.theme.GlanceTheme
import app.screenbalance.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) intent?.getStringExtra(EXTRA_ROUTE)?.let(vm::openRoute)
        // Keep the splash until settings are read, so onboarding never flashes for returning users.
        splash.setKeepOnScreenCondition { vm.settings.value == null }
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val pref = settings?.theme ?: ThemePreference.SYSTEM
            val dark = isDarkTheme(pref)
            androidx.compose.runtime.SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
            }
            GlanceTheme(pref) { Root(vm) }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let(vm::openRoute)
    }

    companion object {
        /** A tab to open on launch, e.g. from the weekly summary notification. */
        const val EXTRA_ROUTE = "route"
    }

    override fun onResume() {
        super.onResume()
        // Permissions are granted in system screens; re-check whenever the user comes back.
        vm.refreshStatus()
    }
}

@Composable
private fun Root(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = s.onboardingComplete,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) },
            label = "root",
        ) { done ->
            if (done) MainShell(vm) else OnboardingFlow(vm)
        }
    }
}
