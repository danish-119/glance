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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.screenbalance.ui.art.ProtectionPulse
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
 * A full-screen nudge, never a block: the reminder ("20 min on YouTube").
 * "Keep going" closes it; the second button takes a break.
 */
@Composable
fun NudgeScreen(
    headline: String,
    detail: String?,
    breakLabel: String,
    onKeepGoing: () -> Unit,
    onBreak: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val line = remember { MOTIVATION.random() }
    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProtectionPulse(active = true, size = 18.dp)
            Spacer(Modifier.height(28.dp))
            Text(
                headline,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (detail != null) {
                Spacer(Modifier.height(8.dp))
                Text(detail, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(32.dp))
            Text(
                line,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            OutlinedButton(
                onClick = onBreak,
                shape = CircleShape,
                border = BorderStroke(1.dp, LocalAccents.current.ember),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalAccents.current.ember),
                modifier = Modifier.heightIn(min = 52.dp).widthIn(min = 180.dp),
            ) { Text(breakLabel, style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = onKeepGoing,
                colors = ButtonDefaults.textButtonColors(contentColor = scheme.onSurfaceVariant),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Keep going", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** "20 min on your phone" / "1 h 20 min on your phone". */
internal fun reminderHeadline(minutes: Int, app: String) = "${formatLimit(minutes)} on $app"
