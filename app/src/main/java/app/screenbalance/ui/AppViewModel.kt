package app.screenbalance.ui

import app.screenbalance.widget.GoalWidget
import app.screenbalance.data.GoalStreak
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.screenbalance.container
import app.screenbalance.data.DayStats
import app.screenbalance.data.Insights
import app.screenbalance.data.SettingsLockedException
import android.content.Intent
import android.content.pm.PackageManager
import app.screenbalance.engine.EffectivePolicy
import app.screenbalance.engine.WatchedApp
import app.screenbalance.model.AppSettings
import app.screenbalance.model.BlockRules
import app.screenbalance.model.RuntimeState
import app.screenbalance.service.NotificationGate
import app.screenbalance.service.ServiceBridge
import app.screenbalance.service.SystemApps
import app.screenbalance.ui.util.formatClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class SystemStatus(
    val serviceEnabled: Boolean = false,
    val serviceRunning: Boolean = false,
    val usageAccess: Boolean = false,
    val notificationAccess: Boolean = false,
    /** Watched apps installed on this phone, in [WatchedApp] order. */
    val installedApps: Set<WatchedApp> = emptySet(),
) {
    val protectionActive get() = serviceEnabled && serviceRunning
}

data class UsageState(
    /** Screen time per day, oldest first, 7 days ending today. */
    val week: List<DayUsage> = emptyList(),
    /** Today's screen time: the total of every app's (needs usage access; null without it). */
    val todayMs: Long? = null,
    /** Today's screen time per app, most used first (every app with usage access; otherwise what Glance measured). */
    val apps: List<AppToday> = emptyList(),
)

/** Today's time in one app and its daily limit (0 = none). */
data class AppToday(val packageName: String, val label: String, val usedMs: Long, val limitMinutes: Int)

/** One day's screen time and every app's. */
data class DayDetail(
    val day: LocalDate,
    val totalMs: Long,
    val apps: List<AppToday>,
    /** The total from Android's figures (usage access); null without it. */
    val systemTotalMs: Long? = null,
)

data class DayUsage(
    val day: LocalDate,
    /** Every app's screen time added up with usage access; otherwise screen-on-and-unlocked time. */
    val totalMs: Long,
)

/** An app that can be opened from the launcher, for the app pickers on Rules. */
data class LaunchableApp(val packageName: String, val label: String)

/** The phone's apps with a launcher icon, for the Limits and Pause tabs and the keep-open list. */
data class PhoneApps(
    val all: List<LaunchableApp> = emptyList(),
    /** The essentials: never closed. */
    val alwaysOpen: Set<String> = emptySet(),
    /** Never paused: home screens, keyboards, the dialer. */
    val neverPause: Set<String> = emptySet(),
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.container
    private val ctx get() = getApplication<Application>()

    val settings: StateFlow<AppSettings?> = app.settings.settings
    val runtime: StateFlow<RuntimeState?> = app.settings.runtime

    private val _status = MutableStateFlow(SystemStatus())
    val status: StateFlow<SystemStatus> = combine(_status, ServiceBridge.connected) { s, connected ->
        s.copy(serviceRunning = connected)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SystemStatus())

    /** Ticks every second so countdowns and "locked" state stay current. */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    val policy: StateFlow<EffectivePolicy?> = combine(settings, runtime, _now) { s, r, n ->
        if (s == null || r == null) null else app.policy.effective(s, r, n, ZoneId.systemDefault())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _usage = MutableStateFlow(UsageState())
    val usage: StateFlow<UsageState> = _usage.asStateFlow()

    /** Live phone time today, from the service. */
    val phoneTodayMs: StateFlow<Long> = ServiceBridge.phoneTodayMs

    private val _phoneApps = MutableStateFlow(PhoneApps())
    val phoneApps: StateFlow<PhoneApps> = _phoneApps.asStateFlow()

    private val _route = MutableStateFlow<String?>(null)
    /** A tab to open (from a notification); MainShell navigates and clears it. */
    val route: StateFlow<String?> = _route.asStateFlow()
    fun openRoute(r: String) { _route.value = r }
    fun routeOpened() { _route.value = null }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-off user-facing messages (snackbars). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _now.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
        viewModelScope.launch { app.stats.version.collect { loadUsage() } }
        viewModelScope.launch { ServiceBridge.usedTodayMs.collect { loadUsage() } }
        viewModelScope.launch { ServiceBridge.packageUsedTodayMs.collect { loadUsage() } }
        viewModelScope.launch { settings.collect { loadUsage() } }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            val st = withContext(Dispatchers.IO) {
                SystemStatus(
                    serviceEnabled = ServiceBridge.isEnabledInSettings(ctx),
                    usageAccess = app.usage.hasAccess(),
                    notificationAccess = NotificationGate.hasAccess(ctx),
                    installedApps = app.usage.installedApps(),
                )
            }
            _status.value = st
            loadUsage()
        }
    }

    private suspend fun loadUsage() {
        val today = LocalDate.now()
        val s = settings.value ?: return
        val days = app.stats.days(today.minusDays(6), today)
        val state = withContext(Dispatchers.IO) {
            val detail = detailOf(today, days.lastOrNull(), s, today)
            val week = days.map { d -> if (d.day == today) DayUsage(today, detail.totalMs) else DayUsage(d.day, pastDetail(d.day, d, s).totalMs) }
            UsageState(week = week, todayMs = detail.systemTotalMs, apps = detail.apps.filter { it.usedMs >= 60_000L || it.limitMinutes > 0 })
        }
        _usage.value = state
    }

    /** One day: its total and every app's time, most used first (apps under a minute left out). */
    suspend fun dayDetail(day: LocalDate): DayDetail {
        val today = LocalDate.now()
        val s = settings.value ?: AppSettings()
        val tracked = app.stats.days(day, day).firstOrNull()
        val d = withContext(Dispatchers.IO) {
            if (day == today) detailOf(day, tracked, s, today) else pastDetail(day, tracked, s)
        }
        return d.copy(apps = d.apps.filter { it.usedMs >= 60_000L || it.limitMinutes > 0 })
    }

    /** The 7 days ending [end], oldest first: every app's total, or only [pkg]'s. */
    suspend fun week(end: LocalDate, pkg: String? = null): List<DayUsage> {
        val today = LocalDate.now()
        val s = settings.value ?: AppSettings()
        val days = app.stats.days(end.minusDays(6), end)
        return withContext(Dispatchers.IO) {
            days.map { d ->
                val detail = when {
                    d.day.isAfter(today) -> null
                    d.day == today -> detailOf(today, d, s, today)
                    else -> pastDetail(d.day, d, s)
                }
                val ms = when {
                    detail == null -> 0L
                    pkg == null -> detail.totalMs
                    else -> detail.apps.firstOrNull { it.packageName == pkg }?.usedMs ?: 0L
                }
                DayUsage(d.day, ms)
            }
        }
    }

    /** Past days don't change: each is read from Android once. */
    private suspend fun pastDetail(day: LocalDate, tracked: DayStats?, s: AppSettings): DayDetail =
        synchronized(pastDays) { pastDays[day] } ?: detailOf(day, tracked, s, LocalDate.now()).also {
            synchronized(pastDays) { pastDays[day] = it }
        }

    /**
     * Every app's time on [day]: Android's figures (with usage access), raised to what Glance measured
     * itself; today also includes the service's live counts. Limits are shown only for today.
     */
    private suspend fun detailOf(day: LocalDate, tracked: DayStats?, s: AppSettings, today: LocalDate): DayDetail {
        val isToday = day == today
        val live = if (isToday) ServiceBridge.usedTodayMs.value else emptyMap()
        val livePackages = if (isToday) ServiceBridge.packageUsedTodayMs.value else emptyMap()
        // Glance's own measurement: the watched apps and the apps with a limit.
        val measured = HashMap<String, Long>()
        for (a in WatchedApp.entries) {
            val ms = maxOf(tracked?.trackedFor(a) ?: 0L, live[a] ?: 0L)
            if (ms > 0) installedPackageOf(a)?.let { measured[it] = ms }
        }
        tracked?.packageMs?.forEach { (pkg, ms) -> measured[pkg] = maxOf(ms, livePackages[pkg] ?: 0L) }
        livePackages.forEach { (pkg, ms) -> measured[pkg] = maxOf(measured[pkg] ?: 0L, ms) }
        // Android's figures for every app (today live; earlier days from the saved history).
        val system = if (isToday) app.screenTime.byApp(day) else app.history.day(day, today)?.apps?.mapValues { it.value.ms }
        val apps = (system.orEmpty().keys + measured.keys).filter(app.screenTime::isApp).map { pkg ->
            AppToday(pkg, labelOf(pkg), maxOf(system?.get(pkg) ?: 0L, measured[pkg] ?: 0L), if (isToday) s.limitForPackage(pkg) else 0)
        }.filter { it.usedMs > 0 || it.limitMinutes > 0 }.sortedByDescending { it.usedMs }
        val systemTotal = system?.let { apps.sumOf { it.usedMs } }
        val phone = if (isToday) maxOf(tracked?.phoneMs ?: 0L, ServiceBridge.phoneMsToday()) else tracked?.phoneMs ?: 0L
        return DayDetail(day, systemTotal ?: phone, apps, systemTotal)
    }

    /**
     * Insights for the 7 days ending [weeksBack] weeks before today, compared with the 7 days before;
     * null when there is nothing to show (no usage access and no saved history).
     */
    suspend fun insights(weeksBack: Int): Insights? = insightsEnding(LocalDate.now().minusDays(7L * weeksBack))

    /** Insights for the 7 days ending [end] (the Day view uses its last day and the 7 days before). */
    suspend fun insightsEnding(end: LocalDate): Insights? = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val all = app.history.days(end, 28, today)
        if (!app.history.hasAccess() && all.all { it.apps.isEmpty() }) return@withContext null
        Insights(
            days = all.subList(21, 28),
            previous = all.subList(14, 21),
            weeklyTotals = all.chunked(7).map { w -> w.sumOf { it.totalMs } },
            daysCounted = all.subList(21, 28).count { !it.day.isAfter(today) },
        )
    }

    fun categoryOf(pkg: String) = app.history.categoryOf(pkg)

    /** Days in a row, up to yesterday, with screen time within [goalMinutes] (same count as the goal widget). */
    suspend fun goalStreak(goalMinutes: Int): Int = withContext(Dispatchers.IO) { GoalStreak.days(app, goalMinutes) }



    /** Screen time already read for past days (UsageStats), so it isn't read again. */
    private val pastDays = HashMap<LocalDate, DayDetail>()

    /** The package of [a] that is installed (Instagram or Instagram Lite, TikTok's two editions). */
    private fun installedPackageOf(a: WatchedApp): String? = a.packages.firstOrNull { pkg ->
        try {
            ctx.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** An app's name, or its package name once it is uninstalled. */
    fun labelOf(pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    /** The phone's apps with a launcher icon, and the essentials, for the app lists. */
    fun loadPhoneApps() {
        viewModelScope.launch {
            _phoneApps.value = withContext(Dispatchers.IO) {
                val pm = ctx.packageManager
                val all = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
                    .asSequence()
                    .map { it.activityInfo.packageName }
                    .filter { it != ctx.packageName }
                    .distinct()
                    .map { pkg -> LaunchableApp(pkg, labelOf(pkg)) }
                    .sortedBy { it.label.lowercase() }
                    .toList()
                val system = SystemApps(ctx)
                PhoneApps(all, system.alwaysOpen, system.neverPause)
            }
        }
    }

    // ------------------------------------------------------------ mutations

    /** Applies a settings change, reporting a locked session instead of crashing. */
    fun update(allowWhenLocked: Boolean = false, transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            try {
                val goal = settings.value?.dailyGoalMinutes
                app.settings.updateSettings(allowWhenLocked, transform)
                if (settings.value?.dailyGoalMinutes != goal) GoalWidget.refresh(ctx)
            } catch (e: SettingsLockedException) {
                _messages.tryEmit(lockedMessage(e.until))
            }
        }
    }

    fun updateRules(transform: (BlockRules) -> BlockRules) = update { it.copy(rules = transform(it.rules)) }

    fun startFocus(minutes: Int) {
        viewModelScope.launch { app.settings.startFocusSession(minutes) }
    }

    fun endFocus() {
        viewModelScope.launch {
            try {
                app.settings.endFocusSession()
            } catch (e: SettingsLockedException) {
                _messages.tryEmit(lockedMessage(e.until))
            }
        }
    }

    fun clearStats() {
        viewModelScope.launch {
            app.stats.clearAll()
            synchronized(pastDays) { pastDays.clear() }
            _messages.tryEmit("Usage history cleared")
        }
    }

    fun completeOnboarding() = update(allowWhenLocked = true) { it.copy(onboardingComplete = true) }

    fun acceptDisclosure() = update(allowWhenLocked = true) {
        it.copy(disclosureVersionAccepted = AppSettings.CURRENT_DISCLOSURE_VERSION)
    }

    private fun lockedMessage(until: Long?) =
        if (until != null) "Strict mode is on: that loosens a rule, so it waits until ${formatClock(until)}"
        else "Strict mode is on: that loosens a rule, so it waits until this ends"
}
