package app.screenbalance.ui.intervention

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.screenbalance.engine.BlockReason
import app.screenbalance.service.InterventionCopy
import app.screenbalance.ui.art.AnimatedLock
import app.screenbalance.ui.theme.LocalAccents
import app.screenbalance.ui.util.formatClock
import app.screenbalance.ui.util.formatRemaining
import kotlinx.coroutines.delay

data class InterventionState(
    val reason: BlockReason,
    /** Lock-screen text; unused for a [coverOnly] cover and the pause screen. */
    val copy: InterventionCopy.Copy? = null,
    /** When the closure ends, shown as a live countdown. */
    val until: Long? = null,
    /** A plain cover with nothing on it: hides a screen for the moment an app is moved off it. */
    val coverOnly: Boolean = false,
    /** Pause other audio while showing. Off when the user's own Reel should keep playing. */
    val pauseAudio: Boolean = true,
    /** The pause and the reminders: the app about to open, or the app in front ("now on YouTube"). */
    val appLabel: String? = null,
    /** The reminder: minutes in the app in front ([appLabel]) since it was opened. */
    val minutes: Int = 0,
    /** Emergency passes left today; null = not offered (section covers, the Settings guard). */
    val emergencyLeft: Int? = null,
)

class InterventionActions(
    /** OK on the lock screen (or the system back button); "Open" / "Keep going" on the pause and reminders. */
    val onDismiss: () -> Unit,
    /** "Not now" / "Take a break". */
    val onLeave: () -> Unit = onDismiss,
    /** Emergency access confirmed on the lock screen. */
    val onEmergency: () -> Unit = {},
)

/**
 * The lock screen, shown only when a whole app is closed (daily limit, quiet hours, focus) and for
 * the Settings guard: a lock, one line, when it ends, and OK. It appears
 * instantly, so the screen behind it is never visible. Also hosts the blank cover, the pause before
 * opening an app ([PauseScreen]) and the reminder ([NudgeScreen]).
 */
@Composable
fun InterventionScreen(state: InterventionState, actions: InterventionActions) {
    val scheme = MaterialTheme.colorScheme
    when (state.reason) {
        BlockReason.OPEN_PAUSE -> {
            PauseScreen(state.appLabel.orEmpty(), onOpen = actions.onDismiss, onLeave = actions.onLeave)
            return
        }
        BlockReason.USE_REMINDER -> {
            NudgeScreen(
                headline = reminderHeadline(state.minutes, state.appLabel.orEmpty()),
                detail = null,
                breakLabel = "Take a break",
                onKeepGoing = actions.onDismiss,
                onBreak = actions.onLeave,
            )
            return
        }
        else -> Unit
    }
    val copy = state.copy
    if (state.coverOnly || copy == null) {
        Box(Modifier.fillMaxSize().background(scheme.background))
        return
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedLock(size = 76.dp)
            Spacer(Modifier.height(32.dp))
            Text(
                copy.eyebrow,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                state.until?.let { untilText(it) } ?: copy.line,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            OutlinedButton(
                onClick = actions.onDismiss,
                shape = CircleShape,
                border = BorderStroke(1.dp, LocalAccents.current.rule),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface),
                modifier = Modifier.heightIn(min = 52.dp).width(160.dp),
            ) { Text("OK", style = MaterialTheme.typography.labelLarge) }
            state.emergencyLeft?.let { left -> EmergencyAccess(left, actions.onEmergency) }
        }
    }
}

/**
 * Always reachable from the lock screen: every app opens for a few minutes, a few times a day,
 * strict mode or not. Two taps, so it isn't hit by accident. Calls and messages never need it.
 */
@Composable
private fun EmergencyAccess(left: Int, onConfirm: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Spacer(Modifier.height(20.dp))
    if (left <= 0) {
        Text(
            "No emergency access left today. Calls, messages, maps and the camera always work.",
            style = MaterialTheme.typography.bodySmall, color = muted, textAlign = TextAlign.Center,
        )
        return
    }
    if (!asking) {
        androidx.compose.material3.TextButton(onClick = { asking = true }) {
            Text("Emergency access", style = MaterialTheme.typography.labelLarge, color = muted)
        }
        return
    }
    Text(
        "Every app opens for ${app.screenbalance.model.RuntimeState.EMERGENCY_MINUTES} minutes. " +
            "$left of ${app.screenbalance.model.RuntimeState.EMERGENCY_PER_DAY} left today.",
        style = MaterialTheme.typography.bodyMedium, color = muted, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onConfirm,
        shape = CircleShape,
        border = BorderStroke(1.dp, LocalAccents.current.ember),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text("Open for ${app.screenbalance.model.RuntimeState.EMERGENCY_MINUTES} minutes", style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun untilText(until: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) {
        while (now < until) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    return "Until ${formatClock(until)} · ${formatRemaining(until - now)} left"
}
