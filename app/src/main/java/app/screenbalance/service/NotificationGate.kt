package app.screenbalance.service

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import app.screenbalance.container
import app.screenbalance.engine.WatchedApp
import app.screenbalance.engine.ScheduleMath
import app.screenbalance.model.AppSettings
import app.screenbalance.model.RuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Holds back notifications by snoozing them, so Android shows them later: every app's during quiet
 * hours and focus sessions (until they end), and an app's own after its daily limit is used up
 * (until midnight). The essentials (calls, messages, …) always notify. Only the posting app's
 * package name is examined; notification content is never read.
 */
class NotificationGate : NotificationListenerService() {

    private val main = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    @Volatile private var settings: AppSettings? = null
    @Volatile private var runtime: RuntimeState = RuntimeState()
    @Volatile private var usedTodayMs: Map<WatchedApp, Long> = emptyMap()
    @Volatile private var packageUsedTodayMs: Map<String, Long> = emptyMap()
    private lateinit var system: SystemApps

    override fun onListenerConnected() {
        super.onListenerConnected()
        system = SystemApps(this)
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        s.launch {
            combine(
                container.settings.settings.filterNotNull(),
                container.settings.runtime.filterNotNull(),
                ServiceBridge.usedTodayMs,
                ServiceBridge.packageUsedTodayMs,
            ) { st, rt, used, usedPackages ->
                settings = st
                runtime = rt
                usedTodayMs = used
                packageUsedTodayMs = usedPackages
                st.schedules to closed()
            }.distinctUntilChanged().collect {
                // Usage ticks every few seconds; only sweep when the schedules or what's closed change.
                sweepAndSchedule()
            }
        }
    }

    override fun onListenerDisconnected() {
        main.removeCallbacksAndMessages(null)
        scope?.cancel()
        scope = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val until = untilFor(sbn.packageName, closed()) ?: return
        snooze(sbn, until)
    }

    /** Snooze anything already showing (e.g. when a window starts), then wake at the next start. */
    private fun sweepAndSchedule() {
        main.removeCallbacksAndMessages(null)
        val s = settings ?: return
        val closed = closed()
        if (closed.everyApp != null || closed.byLimit.isNotEmpty()) {
            val active = try { activeNotifications } catch (e: RuntimeException) { null }
            active?.forEach { sbn -> untilFor(sbn.packageName, closed)?.let { snooze(sbn, it) } }
        }
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val next = ScheduleMath.nextStart(s.schedules, now) ?: return
        val delay = (next - System.currentTimeMillis()).coerceAtLeast(1_000L) + 1_000L
        main.postDelayed({ sweepAndSchedule() }, delay)
    }

    /**
     * What is held right now. [everyApp]: quiet hours or a focus session hold every app's
     * notifications but the essentials', until then. [byLimit]: packages whose daily limit is used up,
     * closed until midnight.
     */
    private data class Closed(val everyApp: Long?, val byLimit: Map<String, Long>)

    private fun closed(): Closed {
        val s = settings ?: return Closed(null, emptyMap())
        val nowMs = System.currentTimeMillis()
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val everyApp = listOfNotNull(
            ScheduleMath.activeUntil(s.schedules, now),
            runtime.session?.endsAtMs?.takeIf { it > nowMs },
        ).maxOrNull()
        val midnight = LocalDate.now(now.zone).plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
        val byLimit = HashMap<String, Long>()
        for (app in WatchedApp.entries) {
            val limitMs = s.limitFor(app) * 60_000L
            if (limitMs > 0 && (usedTodayMs[app] ?: 0L) >= limitMs) app.packages.forEach { byLimit[it] = midnight }
        }
        for ((pkg, minutes) in s.appLimits) {
            if (minutes > 0 && (packageUsedTodayMs[pkg] ?: 0L) >= minutes * 60_000L) byLimit[pkg] = midnight
        }
        return Closed(everyApp, byLimit)
    }

    /** When [pkg]'s notifications may show again, or null if they show now. The essentials always do. */
    private fun untilFor(pkg: String, closed: Closed): Long? {
        if (!system.isCloseable(pkg)) return null
        return listOfNotNull(closed.everyApp, closed.byLimit[pkg]).maxOrNull()
    }

    private fun snooze(sbn: StatusBarNotification, until: Long) {
        val duration = until - System.currentTimeMillis()
        if (duration <= 0) return
        try {
            snoozeNotification(sbn.key, duration)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not snooze notification", e)
        }
    }

    companion object {
        private const val TAG = "NotificationGate"

        fun hasAccess(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        /** Ask Android to rebind after the user changes the setting, in case it was killed. */
        fun requestRebind(context: Context) {
            try {
                NotificationListenerService.requestRebind(ComponentName(context, NotificationGate::class.java))
            } catch (_: RuntimeException) {
                // Not permitted when access is off; nothing to do.
            }
        }
    }
}
