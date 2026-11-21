package app.screenbalance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.screenbalance.data.AppDay
import app.screenbalance.data.DaySummary
import app.screenbalance.data.DemoMode
import app.screenbalance.widget.GoalWidget
import app.screenbalance.widget.ScreenTimeWidget
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/**
 * Debug builds only: fills Glance with four weeks of believable sample usage for recording the
 * store video. `adb shell am broadcast -a app.screenbalance.DEMO -n <pkg>/app.screenbalance.DemoReceiver
 * --es pkgs a,b,c [--ei goal 180] [--ez off true]`; pkgs are installed launcher apps, most used first.
 */
class DemoReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                if (intent.getBooleanExtra("wallpaper", false)) {
                    setDuskWallpaper(context)
                    return@launch
                }
                if (intent.getBooleanExtra("off", false)) {
                    DemoMode.set(context, false, emptyMap())
                    c.stats.clearAll()
                    return@launch
                }
                val pkgs = intent.getStringExtra("pkgs")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                if (pkgs.isEmpty()) return@launch
                val goal = intent.getIntExtra("goal", 180)
                val today = LocalDate.now()
                val rnd = Random(7)
                // Share of the day per app, most used first; the total trends down week over week.
                val shares = pkgs.indices.map { 1.0 / (it + 1.4) }.let { s -> s.map { it / s.sum() } }
                for (back in 27 downTo 0) {
                    val day = today.minusDays(back.toLong())
                    val weekend = day.dayOfWeek.value >= 6
                    val trend = 1.0 + back / 27.0 * 0.45 // A month ago was ~45% heavier: the app is working.
                    var totalMin = (150 + rnd.nextInt(-25, 35)) * trend * (if (weekend) 1.2 else 1.0)
                    // The last six days: mostly within the goal, for a streak.
                    if (back in 1..6) totalMin = minOf(totalMin, goal - 8.0 - rnd.nextInt(0, 30))
                    val dayFraction = if (back == 0) (LocalTime.now().toSecondOfDay() / 86_400.0).coerceIn(0.35, 1.0) else 1.0
                    val totalMs = (totalMin * 60_000 * dayFraction).toLong()
                    val apps = pkgs.zip(shares).associate { (p, sh) ->
                        val ms = (totalMs * sh * (0.85 + rnd.nextDouble() * 0.3)).toLong()
                        p to AppDay(ms, (ms / 60_000 / 3).toInt().coerceAtLeast(1) + rnd.nextInt(0, 6))
                    }
                    val sum = apps.values.sumOf { it.ms }
                    val hours = HOUR_WEIGHTS.mapIndexed { h, w ->
                        if (back == 0 && h > LocalTime.now().hour) 0L else (sum * w / HOUR_WEIGHTS.sum()).toLong()
                    }
                    val summary = DaySummary(
                        day, apps, hours,
                        unlocks = 58 + rnd.nextInt(-10, 20), firstUnlockMinute = 7 * 60 + rnd.nextInt(0, 50),
                        longestPkg = pkgs.first(), longestMs = (28 + rnd.nextInt(0, 30)) * 60_000L,
                    )
                    c.stats.saveDay(summary)
                    if (back == 0) DemoMode.set(context, true, apps.mapValues { it.value.ms })
                }
                c.settings.updateSettings(allowWhenLocked = true) { it.copy(dailyGoalMinutes = goal) }
                ScreenTimeWidget.refresh(context)
                GoalWidget.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    /** A calm dusk in Glance's colours (sky, ember sun, layered hills) as home and lock wallpaper. */
    private fun setDuskWallpaper(context: Context) {
        val w = 1080; val h = 2400
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, h * 0.78f,
            intArrayOf(0xFF1F1D2B.toInt(), 0xFF4A3A4F.toInt(), 0xFF9C5E52.toInt(), 0xFFE7A27A.toInt()),
            floatArrayOf(0f, 0.38f, 0.72f, 1f), android.graphics.Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        // Sun with a soft glow.
        val sx = w * 0.66f; val sy = h * 0.60f
        p.shader = android.graphics.RadialGradient(sx, sy, w * 0.45f, intArrayOf(0x66F08A5D, 0x00F08A5D), null, android.graphics.Shader.TileMode.CLAMP)
        c.drawCircle(sx, sy, w * 0.45f, p)
        p.shader = null; p.color = 0xFFE0532B.toInt()
        c.drawCircle(sx, sy, w * 0.085f, p)
        // Hills, back to front.
        fun hill(base: Float, amp: Float, phase: Float, color: Int) {
            val path = android.graphics.Path()
            path.moveTo(0f, h.toFloat())
            var x = 0f
            while (x <= w) {
                val y = base - amp * kotlin.math.sin((x / w) * 3.4f + phase).toFloat() - amp * 0.4f * kotlin.math.sin((x / w) * 7.1f + phase * 2).toFloat()
                path.lineTo(x, y); x += 8f
            }
            path.lineTo(w.toFloat(), h.toFloat()); path.close()
            p.color = color; c.drawPath(path, p)
        }
        hill(h * 0.64f, 46f, 0.6f, 0xFF7A4B4E.toInt())
        hill(h * 0.70f, 60f, 2.2f, 0xFF52363F.toInt())
        hill(h * 0.77f, 54f, 4.1f, 0xFF33262F.toInt())
        hill(h * 0.85f, 40f, 1.3f, 0xFF1E1820.toInt())
        val wm = android.app.WallpaperManager.getInstance(context)
        wm.setBitmap(bmp, null, true, android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK)
    }

    private companion object {
        /** When in the day phones get used: a morning check, lunch, and a long evening. */
        val HOUR_WEIGHTS = listOf(
            2, 1, 0, 0, 0, 0, 1, 4, 6, 4, 3, 3, 5, 6, 3, 3, 4, 5, 6, 7, 8, 9, 8, 5,
        ).map { it.toDouble() }
    }
}
