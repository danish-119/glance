package app.screenbalance.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import app.screenbalance.R
import app.screenbalance.container
import app.screenbalance.data.GoalStreak
import app.screenbalance.ui.MainActivity
import app.screenbalance.ui.util.formatLimit
import app.screenbalance.ui.util.formatMinutesShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Home-screen goal widget: the day's screen-time budget as a ring that empties as the phone is used
 * (the percentage left in the middle; a full ember ring once over), what's left, and the streak. Without a goal it invites setting one. Tapping opens Today.
 * Refreshed with [ScreenTimeWidget] (unlock, screen-off, midnight, every 30 minutes) and when the goal changes.
 */
class GoalWidget : AppWidgetProvider() {

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

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED -> refresh(context)
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        /** Redraws every placed goal widget; does nothing when none is placed. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GoalWidget::class.java))
            if (ids.isEmpty()) return
            context.container.appScope.launch(Dispatchers.IO) { render(context, manager, ids) }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val c = context.container
            val goal = c.settings.current().dailyGoalMinutes
            val today = LocalDate.now()
            val used = ScreenTimeWidget.todayTotal(context, today)
            val goalMs = goal * 60_000L
            val views = RemoteViews(context.packageName, R.layout.widget_goal)

            // A budget, not a target: the ring starts full and empties as the day's screen time is used.
            // Past the goal it shows a full ember ring.
            val ink = context.getColor(R.color.widget_ink)
            val ember = context.getColor(R.color.widget_ember)
            val over = goal > 0 && used > goalMs
            val left = if (goal > 0) (1f - used / goalMs.toFloat()).coerceIn(0f, 1f) else 0f
            views.setImageViewBitmap(R.id.goal_ring, ring(context, if (over) 1f else left, if (over) ember else ink))
            views.setTextViewText(
                R.id.goal_percent,
                when {
                    goal <= 0 -> ""
                    over -> "over"
                    else -> "${(left * 100).toInt()}%"
                },
            )

            if (goal <= 0) {
                views.setTextViewText(R.id.goal_headline, "No goal yet")
                views.setTextViewText(R.id.goal_detail, "Tap to set a daily goal")
            } else {
                views.setTextViewText(
                    R.id.goal_headline,
                    if (used > goalMs) "${formatMinutesShort(used - goalMs)} over" else "${formatMinutesShort(goalMs - used)} left",
                )
                val streak = GoalStreak.days(c, goal, today)
                views.setTextViewText(
                    R.id.goal_detail,
                    "of ${formatLimit(goal)}" + when {
                        streak >= 2 -> " · $streak-day streak"
                        streak == 1 -> " · 1 day in a row"
                        else -> ""
                    },
                )
            }
            views.setContentDescription(
                R.id.goal_root,
                if (goal > 0) "Screen time ${formatMinutesShort(used)} of a ${formatLimit(goal)} goal" else "No daily goal set",
            )
            val open = PendingIntent.getActivity(
                context, 3, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "today"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.goal_root, open)
            manager.updateAppWidget(ids, views)
            ScreenTimeWidget.scheduleMidnight(context, GoalWidget::class.java, ids, requestCode = 4)
        }

        /** The ring: a faint full track and the progress arc from 12 o'clock, round caps. */
        private fun ring(context: Context, fraction: Float, color: Int): Bitmap {
            val size = context.resources.getDimensionPixelSize(R.dimen.widget_ring)
            val stroke = size * 0.1f
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val rect = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
            paint.color = ColorUtils.setAlphaComponent(context.getColor(R.color.widget_muted), 0x33)
            canvas.drawArc(rect, 0f, 360f, false, paint)
            if (fraction > 0f) {
                paint.color = color
                canvas.drawArc(rect, -90f, 360f * fraction.coerceAtLeast(0.02f), false, paint)
            }
            return bmp
        }
    }
}
