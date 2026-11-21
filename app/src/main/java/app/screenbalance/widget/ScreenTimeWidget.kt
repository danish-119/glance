package app.screenbalance.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import app.screenbalance.R
import app.screenbalance.container
import app.screenbalance.service.ServiceBridge
import app.screenbalance.ui.MainActivity
import app.screenbalance.ui.util.formatMinutesShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Home-screen widget: today's screen time and the three most used apps. Tapping it opens Glance.
 * Android refreshes it every 30 minutes; the accessibility service also refreshes it at each unlock
 * and screen-off, and an alarm just after midnight (and any clock change) starts the new day at 0.
 */
class ScreenTimeWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        context.container.appScope.launch(Dispatchers.IO) {
            try {
                render(context, manager, ids)
            } finally {
                pending.finish()
            }
        }
    }

    /** The clock or time zone changed: "today" may be another day now. */
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED -> {
                val pending = goAsync()
                context.container.appScope.launch(Dispatchers.IO) {
                    try {
                        val manager = AppWidgetManager.getInstance(context)
                        val ids = manager.getAppWidgetIds(ComponentName(context, ScreenTimeWidget::class.java))
                        if (ids.isNotEmpty()) render(context, manager, ids)
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        /** Redraws every placed widget; does nothing when none is placed. */
        fun refresh(context: Context) {
            GoalWidget.refresh(context)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ScreenTimeWidget::class.java))
            if (ids.isEmpty()) return
            context.container.appScope.launch(Dispatchers.IO) { render(context, manager, ids) }
        }

        private val ROWS = listOf(
            Row(R.id.widget_row1, R.id.widget_icon1, R.id.widget_label1, R.id.widget_time1),
            Row(R.id.widget_row2, R.id.widget_icon2, R.id.widget_label2, R.id.widget_time2),
            Row(R.id.widget_row3, R.id.widget_icon3, R.id.widget_label3, R.id.widget_time3),
        )

        private class Row(val root: Int, val icon: Int, val label: Int, val time: Int)

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val c = context.container
            val today = LocalDate.now()
            val byApp = c.screenTime.byApp(today)
            val total = todayTotal(context, today, byApp)
            val top = byApp.orEmpty().entries.filter { it.value >= 60_000L }.sortedByDescending { it.value }.take(ROWS.size)

            val views = RemoteViews(context.packageName, R.layout.widget_screen_time)
            views.setTextViewText(R.id.widget_total, formatMinutesShort(total))
            val pm = context.packageManager
            val iconPx = context.resources.getDimensionPixelSize(R.dimen.widget_icon)
            ROWS.forEachIndexed { i, row ->
                val entry = top.getOrNull(i)
                views.setViewVisibility(row.root, if (entry == null) View.GONE else View.VISIBLE)
                if (entry == null) return@forEachIndexed
                try {
                    val info = pm.getApplicationInfo(entry.key, 0)
                    views.setTextViewText(row.label, pm.getApplicationLabel(info))
                    views.setImageViewBitmap(row.icon, pm.getApplicationIcon(info).toBitmap(iconPx, iconPx))
                } catch (_: PackageManager.NameNotFoundException) {
                    views.setTextViewText(row.label, entry.key)
                }
                views.setTextViewText(row.time, formatMinutesShort(entry.value))
            }
            views.setViewVisibility(R.id.widget_note, if (byApp == null) View.VISIBLE else View.GONE)
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, views)
            scheduleMidnight(context, ScreenTimeWidget::class.java, ids, requestCode = 1)
        }

        /** Today's screen time as the widgets show it. Without usage access: Glance's own screen-on time. */
        suspend fun todayTotal(
            context: Context,
            today: LocalDate = LocalDate.now(),
            byApp: Map<String, Long>? = context.container.screenTime.byApp(today),
        ): Long = byApp?.values?.sum()
            ?: maxOf(context.container.stats.days(today, today).firstOrNull()?.phoneMs ?: 0L, ServiceBridge.phoneMsToday())

        /**
         * Redraws just after midnight so the new day starts at 0. Not a wakeup alarm: a sleeping phone
         * gets it the moment it wakes, which is when the widget can be seen. Needs no permission.
         */
        fun scheduleMidnight(context: Context, provider: Class<out AppWidgetProvider>, ids: IntArray, requestCode: Int) {
            val at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 5_000L
            val intent = Intent(context, provider)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            val pi = PendingIntent.getBroadcast(
                context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            context.getSystemService(AlarmManager::class.java)?.set(AlarmManager.RTC, at, pi)
        }
    }
}
