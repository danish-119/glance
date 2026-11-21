package app.screenbalance.ui.intervention

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.screenbalance.ui.art.rememberReducedMotion
import app.screenbalance.ui.theme.LocalAccents
import kotlinx.coroutines.delay

/** How long the pause before opening an app lasts. */
const val PAUSE_SECONDS = 10

/**
 * The pause before opening an app (optional, in Rules): a breathing circle, a large countdown and a
 * line worth thinking about. After [PAUSE_SECONDS] the app can be opened; "Not now" leaves it.
 */
@Composable
fun PauseScreen(appLabel: String, onOpen: () -> Unit, onLeave: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val line = remember { MOTIVATION.random() }
    var left by remember { mutableIntStateOf(PAUSE_SECONDS) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1_000)
            left--
        }
    }

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
            Text(
                "About to open $appLabel",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Breathing(left)
            Spacer(Modifier.height(36.dp))
            Text(
                line,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(40.dp))
            OutlinedButton(
                onClick = onOpen,
                enabled = left == 0,
                shape = CircleShape,
                border = BorderStroke(1.dp, LocalAccents.current.rule),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.onSurface),
                modifier = Modifier.heightIn(min = 52.dp).widthIn(min = 180.dp),
            ) {
                Text(
                    if (left > 0) "Open $appLabel in $left" else "Open $appLabel",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = onLeave,
                colors = ButtonDefaults.textButtonColors(contentColor = LocalAccents.current.ember),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Not now", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/** A slow breathing circle with the seconds left in it (still when motion is reduced). */
@Composable
private fun Breathing(left: Int) {
    val accent = LocalAccents.current.ember
    val scale = if (rememberReducedMotion()) 1f else {
        rememberInfiniteTransition(label = "breathe").animateFloat(
            initialValue = 0.82f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(4_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "scale",
        ).value
    }
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(132.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(accent.copy(alpha = 0.14f), CircleShape),
        )
        AnimatedContent(
            targetState = left,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "count",
        ) { n ->
            Text(
                if (n > 0) "$n" else "✓",
                style = MaterialTheme.typography.displayMedium,
                color = accent,
            )
        }
    }
}
