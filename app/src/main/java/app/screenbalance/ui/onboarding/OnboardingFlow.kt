package app.screenbalance.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.screenbalance.model.AppSettings
import app.screenbalance.ui.AppViewModel
import app.screenbalance.ui.SystemIntents
import app.screenbalance.ui.art.AnimatedMark
import app.screenbalance.ui.art.FeedThatStops
import app.screenbalance.ui.art.ProtectionPulse
import app.screenbalance.ui.components.EditorialTitle
import app.screenbalance.ui.components.HairlineRule
import app.screenbalance.ui.components.KickerText
import app.screenbalance.ui.components.PrimaryButton
import app.screenbalance.ui.components.QuietButton
import app.screenbalance.ui.components.SecondaryButton
import app.screenbalance.ui.theme.LocalAccents

private enum class Step { WELCOME, HOW, DISCLOSURE, ENABLE, USAGE, NOTIFICATIONS }

@Composable
fun OnboardingFlow(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val steps = remember { Step.entries.toList() }
    var index by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val step = steps[index]
    fun next() { if (index < steps.lastIndex) index++ else vm.completeOnboarding() }
    BackHandler(enabled = index > 0) { index-- }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Progress(index, steps.size)
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(tween(360)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(360))) togetherWith
                    (slideOutHorizontally(tween(240)) { if (forward) -it / 6 else it / 6 } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { s ->
            when (s) {
                Step.WELCOME -> Welcome(::next)
                Step.HOW -> HowItWorks(::next)
                Step.DISCLOSURE -> Disclosure(
                    accepted = (settings?.disclosureVersionAccepted ?: 0) >= AppSettings.CURRENT_DISCLOSURE_VERSION,
                    onAgree = { vm.acceptDisclosure(); next() },
                    onDecline = { index = steps.indexOf(Step.USAGE) },
                )
                Step.ENABLE -> EnableService(status.serviceEnabled, status.protectionActive, ::next)
                Step.USAGE -> UsageAccess(status.usageAccess, ::next)
                Step.NOTIFICATIONS -> NotificationAccess(status.notificationAccess, ::next)
            }
        }
    }
}

@Composable
private fun Progress(index: Int, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 16.dp)
            .semantics { contentDescription = "Step ${index + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { i ->
            val color by animateColorAsState(
                when {
                    i < index -> MaterialTheme.colorScheme.onSurface
                    i == index -> LocalAccents.current.ember
                    else -> LocalAccents.current.rule
                },
                label = "seg",
            )
            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

@Composable
private fun StepPage(
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 24.dp), content = content)
        Column(Modifier.padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = footer)
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) = StepPage(footer = { PrimaryButton("Get started", onNext) }) {
    Spacer(Modifier.height(40.dp))
    AnimatedMark(72.dp)
    Spacer(Modifier.height(48.dp))
    EditorialTitle("Use your phone", "on purpose.", large = true)
    Spacer(Modifier.height(20.dp))
    Text(
        "Glance sits quietly next to your apps. Messages, friends and the things you came for stay. " +
            "Reels, Shorts and endless feeds don't, and every app keeps to the time you give it.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(28.dp))
    Text(
        "No account. No login. Nothing leaves your phone.",
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = FontStyle.Italic,
        color = LocalAccents.current.faint,
    )
}

@Composable
private fun HowItWorks(onNext: () -> Unit) = StepPage(footer = { PrimaryButton("Continue", onNext) }) {
    FeedThatStops(Modifier.fillMaxWidth().height(230.dp))
    Spacer(Modifier.height(32.dp))
    EditorialTitle("How it works")
    Spacer(Modifier.height(20.dp))
    Numbered("01", "It closes the endless parts", "Reels, Shorts, Spotlight and Explore: the app moves to your profile, the search box or another tab. Messages are never touched.")
    Numbered("02", "It keeps time", "Give any app a daily limit. Quiet hours and focus sessions close the apps you choose and hold notifications; calls and messages always come through.")
    Numbered("03", "It's honest about limits", "Apps change often. If a screen can't be recognised, Glance lets it through rather than block something you need.")
}

@Composable
private fun Numbered(n: String, title: String, body: String) {
    Row(Modifier.padding(vertical = 10.dp)) {
        Text(n, style = MaterialTheme.typography.headlineSmall, color = LocalAccents.current.ember, modifier = Modifier.width(44.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Google Play "prominent disclosure": shown before the user is sent to Accessibility settings,
 * describes the data accessed and its use, and requires an affirmative choice.
 */
@Composable
private fun Disclosure(accepted: Boolean, onAgree: () -> Unit, onDecline: () -> Unit) = StepPage(
    footer = {
        PrimaryButton(if (accepted) "Continue" else "I agree", onAgree)
        QuietButton("Not now", onDecline, Modifier.align(Alignment.CenterHorizontally))
    },
) {
    KickerText("Accessibility service disclosure", color = LocalAccents.current.ember)
    Spacer(Modifier.height(14.dp))
    EditorialTitle("Before you turn it on")
    Spacer(Modifier.height(16.dp))
    Text(
        "Glance uses Android's Accessibility Service API. This is the only way an app can tell which app, and which " +
            "part of it, is on screen, which is what lets it close Reels while keeping your messages open.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(24.dp))
    DisclosureBlock(
        "What it reads",
        "In Instagram, YouTube, Facebook and Snapchat: the layout of the screen while the app is open " +
            "(which screen and buttons are showing, which tab you tap, short labels such as \"You've completely caught up\"). " +
            "For every other app, including TikTok, X and Reddit: only its name when it comes to the front. During a focus session or quiet hours it also " +
            "checks whether Settings is showing Glance's own switch.",
    )
    DisclosureBlock(
        "What it does with it",
        "Decides on the spot whether the screen or app is one you chose to close, and if so moves you somewhere allowed. " +
            "Only totals are kept: minutes per app per day.",
    )
    DisclosureBlock(
        "What it never does",
        "Read or store your messages, posts, comments or passwords. Send anything anywhere: Glance has no internet " +
            "access at all. Show ads or share data with anyone.",
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "You can switch the service off at any time in Android's Accessibility settings.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DisclosureBlock(title: String, body: String) {
    HairlineRule()
    Column(Modifier.padding(vertical = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EnableService(enabled: Boolean, running: Boolean, onNext: () -> Unit) {
    val ctx = LocalContext.current
    StepPage(
        footer = {
            if (enabled) {
                PrimaryButton("Continue", onNext)
            } else {
                PrimaryButton("Open Accessibility settings", { SystemIntents.openAccessibilitySettings(ctx) })
                QuietButton("I'll do this later", onNext, Modifier.align(Alignment.CenterHorizontally))
            }
        },
    ) {
        EditorialTitle("Turn on", "Glance.")
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProtectionPulse(active = enabled)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    enabled && running -> "Protection is on"
                    enabled -> "Switched on, starting…"
                    else -> "Not switched on yet"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(24.dp))
        Numbered("1", "Open Accessibility settings", "Use the button below.")
        Numbered("2", "Find \"Glance\"", "It's usually under Installed apps or Downloaded apps.")
        Numbered("3", "Switch it on and confirm", "Android shows a standard warning for every accessibility app. Come back here when you're done.")
        Spacer(Modifier.height(12.dp))
        Text(
            "Switch greyed out? On Android 13 and later, apps installed outside the Play Store need one extra step: " +
                "App info → ⋮ → Allow restricted settings.",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = LocalAccents.current.faint,
        )
        QuietButton("Open App info", { SystemIntents.openAppDetails(ctx) })
    }
}

@Composable
private fun NotificationAccess(granted: Boolean, onNext: () -> Unit) {
    val ctx = LocalContext.current
    StepPage(
        footer = {
            if (granted) PrimaryButton("Finish", onNext) else {
                PrimaryButton("Allow notification access", { SystemIntents.openNotificationAccessSettings(ctx) })
                QuietButton("Skip", onNext, Modifier.align(Alignment.CenterHorizontally))
            }
        },
    ) {
        KickerText("Recommended")
        Spacer(Modifier.height(14.dp))
        EditorialTitle("Quiet when", "it's closed.")
        Spacer(Modifier.height(16.dp))
        Text(
            "When an app is closed (quiet hours, a focus session, or its daily limit used up), its notifications " +
                "are held back and shown when it opens again. During quiet hours and focus sessions every app's notifications wait. Calls and messages always come through.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "This needs Android's notification access. Glance only checks which app a notification comes from. " +
                "It never reads what a notification says, and nothing is stored or sent.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (granted) {
            Spacer(Modifier.height(20.dp))
            Text("Notification access granted.", style = MaterialTheme.typography.titleMedium, color = LocalAccents.current.moss)
        }
    }
}

@Composable
private fun UsageAccess(granted: Boolean, onNext: () -> Unit) {
    val ctx = LocalContext.current
    StepPage(
        footer = {
            if (granted) PrimaryButton("Continue", onNext) else {
                PrimaryButton("Allow usage access", { SystemIntents.openUsageAccessSettings(ctx) })
                QuietButton("Skip", onNext, Modifier.align(Alignment.CenterHorizontally))
            }
        },
    ) {
        KickerText("Optional")
        Spacer(Modifier.height(14.dp))
        EditorialTitle("Know your", "real numbers.")
        Spacer(Modifier.height(16.dp))
        Text(
            "Usage access lets Glance read the same screen-time figures Android's Digital Wellbeing shows, " +
                "so each app's daily time and limit are exact.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Without it, Glance still measures time itself while it's running.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (granted) {
            Spacer(Modifier.height(20.dp))
            Text("Usage access granted.", style = MaterialTheme.typography.titleMedium, color = LocalAccents.current.moss)
        }
    }
}

