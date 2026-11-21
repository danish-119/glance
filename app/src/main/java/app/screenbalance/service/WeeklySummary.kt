package app.screenbalance.service

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import app.screenbalance.R
import app.screenbalance.container
import app.screenbalance.data.Insights
import app.screenbalance.ui.MainActivity
import app.screenbalance.ui.util.formatMinutesShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Monday morning: one notification with last week's screen time (Monday to Sunday), how it compares
 * with the week before, the most used app and the days within the goal. Not a wakeup alarm: it
 * arrives when the phone is next used after 9:00.
 */
class WeeklySummaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val c = context.container
        c.appScope.launch(Dispatchers.IO) {
            try {
                val s = c.settings.current()
                if (s.weeklySummary) post(context, s.dailyGoalMinutes)
            } finally {
                schedule(context)
                pending.finish()
            }
        }
    }

    private suspend fun post(context: Context, goalMinutes: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val c = context.container
        val sunday = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.SUNDAY))
        val days = c.history.days(sunday, 14)
        val week = Insights(days.subList(7, 14), days.subList(0, 7), emptyList(), 7)
        if (week.totalMs <= 0) return

        val lines = ArrayList<String>()
        week.previousDailyAvgMs?.takeIf { it > 0 }?.let { prev ->
            val pct = ((week.dailyAvgMs - prev) * 100f / prev).roundToInt()
            lines += when {
                pct == 0 -> "About the same as the week before."
                pct < 0 -> "${abs(pct)}% less than the week before. Nice."
                else -> "$pct% more than the week before."
            }
        }
        week.apps.firstOrNull()?.let { (pkg, a) ->
            val label = try {
                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                pkg
            }
            lines += "Most used: $label, ${formatMinutesShort(a.ms / 7)} a day."
        }
        if (goalMinutes > 0) {
            val within = week.days.count { it.totalMs in 1..goalMinutes * 60_000L }
            lines += "Within your goal on $within of 7 days."
        }

        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Weekly summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Last week's screen time, on Monday morning"
                },
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "insights"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Last week: ${formatMinutesShort(week.dailyAvgMs)} a day")
            .setContentText(lines.firstOrNull() ?: "See your week in Insights.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    companion object {
        private const val CHANNEL = "weekly_summary"
        private const val NOTIFICATION_ID = 7

        /** Next Monday 9:00 (today, if it's Monday before 9). Safe to call often: it replaces the alarm. */
        fun schedule(context: Context) {
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            var at = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).atTime(9, 0).atZone(zone)
            if (at.toInstant().toEpochMilli() <= now) at = at.plusWeeks(1)
            val pi = PendingIntent.getBroadcast(
                context, 2, Intent(context, WeeklySummaryReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            context.getSystemService(AlarmManager::class.java)?.set(AlarmManager.RTC, at.toInstant().toEpochMilli(), pi)
        }
    }
}
