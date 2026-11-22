package app.screenbalance.ui.intervention

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.screenbalance.ui.art.rememberReducedMotion
import app.screenbalance.ui.theme.LocalAccents
import app.screenbalance.ui.util.formatLimit

/** Lines for the reminders and the pause; one is picked each time so they never read like boilerplate. */
internal val MOTIVATION = listOf(
    "Your time is the one thing you can't scroll back.",
    "Is this the best use of the next ten minutes?",
    "Nothing in there is going anywhere.",
    "Ten seconds now can save you an hour later.",
    "What did you pick up your phone to do?",
    "You can put it down and do the thing you've been putting off.",
    "The feed is designed to keep you. You decide.",
    "A quick check rarely stays quick.",
    "Look up for a minute. What's around you?",
    "Rest your eyes. Stretch. Breathe.",
)

/**
 * The reminder ("20 min on YouTube"), never a block. Unlike the full-screen pause before opening,
 * it is a card that rises from the bottom over the dimmed app, which stays visible behind it:
 * a time check, not a gate. "Keep going" closes it; "Take a break" goes home.
 */
@Composable
fun ReminderScreen(minutes: Int, appLabel: String, onKeepGoing: () -> Unit, onBreak: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accents = LocalAccents.current
    val line = remember { MOTIVATION.random() }
    val reduced = rememberReducedMotion()
    val visible = remember { MutableTransitionState(reduced).apply { targetState = true } }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))) {
        AnimatedVisibility(
            visibleState = visible,
            enter = slideInVertically(tween(320)) { it / 2 } + fadeIn(tween(220)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(scheme.surface)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeBadge(minutes)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                        Text("Time check", style = MaterialTheme.typography.labelMedium, color = accents.ember)
                        Spacer(Modifier.height(2.dp))
                        Text(reminderHeadline(minutes, appLabel), style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(line, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onKeepGoing,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, accents.rule),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text("Keep going", style = MaterialTheme.typography.labelLarge) }
                    Button(
                        onClick = onBreak,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = accents.ember, contentColor = Color.White),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text("Take a break", style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

/** A closed ember ring with the time in it: "20 min", or "1h" past the hour. */
@Composable
private fun TimeBadge(minutes: Int) {
    val ember = LocalAccents.current.ember
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(56.dp)) {
            val w = 4.dp.toPx()
            drawCircle(ember.copy(alpha = 0.14f))
            drawArc(ember, -90f, 360f, useCenter = false, style = Stroke(w, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (minutes < 60) "$minutes" else "${minutes / 60}h",
                style = MaterialTheme.typography.titleMedium,
                color = ember,
            )
            if (minutes < 60) Text("min", style = MaterialTheme.typography.labelSmall, color = ember)
        }
    }
}

/** "20 min on YouTube" / "1 h 20 min on YouTube". */
internal fun reminderHeadline(minutes: Int, app: String) = "${formatLimit(minutes)} on $app"
