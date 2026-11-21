package app.screenbalance.ui.components

import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.screenbalance.ui.art.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Loaded app icons, so scrolling a long list doesn't decode them again. */
private val iconCache = LruCache<String, ImageBitmap>(160)

/** An app's launcher icon, loaded off the main thread. Blank while loading or if the app is gone. */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { IconSize.roundToPx() }
    val icon by produceState(iconCache.get(packageName), packageName) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                ctx.packageManager.getApplicationIcon(packageName).toBitmap(px, px).asImageBitmap()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }?.also { iconCache.put(packageName, it) }
    }
    Box(modifier.size(IconSize)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

private val IconSize = 36.dp

/**
 * Placeholder rows while the app list loads: an icon and a name bar per row, pulsing in a soft wave
 * from top to bottom. Still when the user turned animations off.
 */
@Composable
fun LoadingAppRows(rows: Int = 8) {
    val reduced = rememberReducedMotion()
    val wave = rememberInfiniteTransition(label = "loading")
    val phase by wave.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase",
    )
    val tint = MaterialTheme.colorScheme.onSurface
    Column(Modifier.semantics { contentDescription = "Loading apps" }) {
        repeat(rows) { i ->
            // Each row peaks a little after the one above it.
            val t = ((phase - i / rows.toFloat()) % 1f + 1f) % 1f
            val alpha = if (reduced) 0.08f else 0.05f + 0.09f * (1f - abs(2f * t - 1f))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(IconSize).clip(CircleShape).background(tint.copy(alpha = alpha)))
                Spacer(Modifier.width(14.dp))
                Box(
                    Modifier.fillMaxWidth(0.35f + 0.35f * ((i * 37) % 10) / 10f).height(14.dp)
                        .clip(RoundedCornerShape(7.dp)).background(tint.copy(alpha = alpha)),
                )
            }
        }
    }
}

/** One app in a list: its icon and name, then [trailing] (a time, a switch). */
@Composable
fun AppListRow(packageName: String, label: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(packageName)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}
