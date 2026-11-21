package app.screenbalance.ui.art

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.screenbalance.ui.theme.LocalAccents
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** True when the user turned animations off in system settings. All art then renders still. */
@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

private fun DrawScope.pill(color: Color, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) {
    if (w <= 0f) return
    drawRoundRect(color.copy(alpha = color.alpha * alpha), Offset(x, y), Size(w, h), CornerRadius(h / 2, h / 2))
}

/** The mark's "g" (drawn as a path, no font needed), in a 512-unit grid; its full stop sits at (331, 316). */
private const val MARK_G =
    "M219.41 416Q191.36 416 175.13 404.75Q158.89 393.49 158.89 377.62Q158.89 370.98 160.73 365.63Q162.58 360.28 168.67 353.27Q174.76 346.26 187.3 335.19Q191.73 331.13 191.73 327.62Q191.73 324.12 187.3 322.27Q174.02 316.74 171.44 308.07Q168.85 299.39 177.71 292.01L204.28 269.14Q208.7 265.45 203.91 262.86Q188.78 255.48 179.92 239.99Q171.07 224.49 171.07 206.77Q171.07 189.8 178.45 176.15Q185.83 162.49 198.37 154.38Q210.92 146.26 226.42 146.26Q235.27 146.26 243.39 149.21Q248.56 151.06 249.66 145.89Q252.99 131.87 262.03 123.93Q271.07 116 279.92 116Q288.41 116 293.76 120.98Q299.11 125.96 299.11 133.34Q299.11 139.62 295.42 143.31Q291.73 147 286.2 147Q281.4 147 279.18 145.15Q276.97 143.31 275.13 141.46Q273.28 139.62 269.22 139.62Q260.37 139.62 258.15 149.21Q257.04 152.9 258.34 155.48Q259.63 158.07 262.21 160.65Q271.07 169.14 276.05 181.31Q281.03 193.49 281.03 208.25Q281.03 225.96 273.83 239.99Q266.64 254.01 254.28 262.13Q241.92 270.24 226.42 270.24Q219.77 270.24 214.98 273.01Q210.18 275.78 205.38 280.94L199.11 287.96Q194.31 293.86 196.34 299.21Q198.37 304.56 206.49 304.56H238.23Q264.79 304.56 280.29 317.66Q295.79 330.76 295.79 353.64Q295.79 371.72 285.46 385.74Q275.13 399.76 257.97 407.88Q240.81 416 219.41 416ZM226.05 261.02Q239.33 261.02 247.08 246.63Q254.83 232.24 254.83 207.51Q254.83 183.53 246.9 168.95Q238.96 154.38 226.05 154.38Q213.13 154.38 205.2 168.95Q197.27 183.53 197.27 207.51Q197.27 232.24 205.01 246.63Q212.76 261.02 226.05 261.02ZM223.83 407.88Q237.12 407.88 248.19 401.98Q259.26 396.07 265.72 385.56Q272.17 375.04 272.17 361.76Q272.17 345.15 262.39 336.11Q252.62 327.07 234.9 327.07H226.42Q214.98 327.07 209.26 329.28Q203.54 331.5 198.37 337.03Q186.93 349.21 183.98 356.59Q181.03 363.97 181.03 371.72Q181.03 387.96 192.28 397.92Q203.54 407.88 223.83 407.88Z"

/**
 * The Glance mark, animated: the g fades up into place, then the full stop drops and settles.
 * [tile] draws the ink tile behind it (logo use); without it the mark sits on the page.
 */
@Composable
fun AnimatedMark(size: Dp, modifier: Modifier = Modifier, tile: Boolean = true, replayKey: Any = Unit) {
    val reduced = rememberReducedMotion()
    val g = remember(replayKey) { Animatable(if (reduced) 1f else 0f) }
    val dot = remember(replayKey) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(replayKey) {
        if (reduced) return@LaunchedEffect
        launch { g.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        delay(380)
        dot.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    val ink = MaterialTheme.colorScheme.primary
    val paper = MaterialTheme.colorScheme.onPrimary
    val ember = LocalAccents.current.ember
    val glyph = remember { PathParser().parsePathString(MARK_G).toPath() }
    Canvas(modifier.size(size).semantics { contentDescription = "Glance" }) {
        val u = this.size.width / 512f
        if (tile) drawRoundRect(ink, cornerRadius = CornerRadius(116 * u, 116 * u))
        val letter = (if (tile) paper else ink).copy(alpha = g.value)
        withTransform({ translate(top = 20 * u * (1 - g.value)); scale(u, u, Offset.Zero) }) {
            drawPath(glyph, letter)
            drawPath(glyph, letter, style = Stroke(10f, join = StrokeJoin.Round))
        }
        val d = dot.value
        drawCircle(ember, radius = 22 * u * d.coerceIn(0f, 1.2f), center = Offset(331 * u, (316 - 50 * (1 - d)) * u))
    }
}

/**
 * Onboarding hero: a feed scrolls past, slows, and stops on a full stop.
 * Loops gently so the idea reads at a glance.
 */
@Composable
fun FeedThatStops(modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val t = rememberInfiniteTransition(label = "feed")
    val progress by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 5200
                0f at 0 using LinearEasing
                0.78f at 2600 using FastOutSlowInEasing // fast scroll, then slowing
                1f at 3400
                1f at 5200 // rest on the stop
            },
            RepeatMode.Restart,
        ),
        label = "p",
    )
    val p = if (reduced) 1f else progress
    val scheme = MaterialTheme.colorScheme
    val ember = LocalAccents.current.ember
    val rule = LocalAccents.current.rule
    Canvas(modifier.semantics { contentDescription = "A feed that comes to a stop" }) {
        val w = size.width
        val h = size.height
        val frameW = w * 0.62f
        val left = (w - frameW) / 2
        val radius = 28.dp.toPx()
        // Phone-like frame.
        drawRoundRect(scheme.surfaceContainer, Offset(left, 0f), Size(frameW, h), CornerRadius(radius, radius))
        drawRoundRect(rule, Offset(left, 0f), Size(frameW, h), CornerRadius(radius, radius), style = Stroke(1.dp.toPx()))
        val pad = 18.dp.toPx()
        val rowH = 64.dp.toPx()
        val gap = 14.dp.toPx()
        val rows = 9
        val travel = (rowH + gap) * (rows - 3)
        val offset = -travel * p
        clipRect(left + 1, 1f, left + frameW - 1, h - 1) {
            for (i in 0 until rows) {
                val y = pad + i * (rowH + gap) + offset
                if (y > h || y + rowH < 0) continue
                val x = left + pad
                val inner = frameW - pad * 2
                drawCircle(scheme.surfaceContainerHighest, 9.dp.toPx(), Offset(x + 9.dp.toPx(), y + 9.dp.toPx()))
                pill(scheme.surfaceContainerHighest, x + 26.dp.toPx(), y + 4.dp.toPx(), inner * 0.42f, 10.dp.toPx())
                drawRoundRect(
                    scheme.surfaceContainerHigh, Offset(x, y + 26.dp.toPx()), Size(inner, rowH - 26.dp.toPx()),
                    CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                )
            }
            // The end of the feed: a rule and the full stop, revealed as the scroll settles.
            val endY = pad + (rows - 3) * (rowH + gap) + offset + rowH * 0.35f
            val reveal = ((p - 0.78f) / 0.22f).coerceIn(0f, 1f)
            pill(scheme.onSurface, left + pad, endY, (frameW - pad * 2) * 0.55f * reveal, 10.dp.toPx(), alpha = 0.85f)
            drawCircle(ember, 9.dp.toPx() * reveal, Offset(left + pad + 9.dp.toPx(), endY + 34.dp.toPx()))
        }
    }
}

/** Status glyph: a steady dot with a slow ripple while protection is running. */
@Composable
fun ProtectionPulse(active: Boolean, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val reduced = rememberReducedMotion()
    val color = if (active) LocalAccents.current.moss else LocalAccents.current.ember
    val t = rememberInfiniteTransition(label = "pulse")
    val ripple by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "r")
    Canvas(modifier.size(size * 2.2f)) {
        val base = size.toPx() / 2
        if (active && !reduced) {
            drawCircle(color.copy(alpha = (1 - ripple) * 0.35f), base * (1 + ripple * 1.2f), center)
        }
        drawCircle(color, base, center)
    }
}

/**
 * The intervention lock: the shackle drops shut with a small settle, and the keyhole is the
 * ember full stop from the logo. Drawn with strokes so it stays crisp at any size.
 */
@Composable
fun AnimatedLock(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val reduced = rememberReducedMotion()
    val close = remember { Animatable(if (reduced) 1f else 0f) }
    val dot = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        close.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 900f))
        dot.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val ember = LocalAccents.current.ember
    Canvas(modifier.size(size).semantics { contentDescription = "Locked" }) {
        val w = this.size.width
        val stroke = w * 0.075f
        val bodyTop = w * 0.44f
        val bodyLeft = w * 0.18f
        val bodyW = w * 0.64f
        val bodyH = w * 0.50f
        // Shackle: lifted by up to 18% of the size while open, closes onto the body.
        val lift = (1f - close.value) * w * 0.18f
        val shackleW = bodyW * 0.62f
        val left = (w - shackleW) / 2
        val top = w * 0.12f - lift
        val arcSize = Size(shackleW, shackleW)
        drawArc(ink, 180f, 180f, false, Offset(left, top), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        val legBottom = bodyTop + stroke / 2 - lift * 0.0f
        drawLine(ink, Offset(left, top + shackleW / 2), Offset(left, legBottom), stroke, StrokeCap.Round)
        // The right leg is shorter while open so the shackle reads as "unlatched".
        drawLine(ink, Offset(left + shackleW, top + shackleW / 2), Offset(left + shackleW, legBottom - lift * 1.4f), stroke, StrokeCap.Round)
        drawRoundRect(ink, Offset(bodyLeft, bodyTop), Size(bodyW, bodyH), CornerRadius(w * 0.09f, w * 0.09f))
        drawCircle(ember, w * 0.065f * dot.value, Offset(w / 2, bodyTop + bodyH * 0.48f))
    }
}
