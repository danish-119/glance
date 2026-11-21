package app.screenbalance.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.KeyguardManager
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import app.screenbalance.AppContainer
import app.screenbalance.R
import app.screenbalance.container
import app.screenbalance.engine.BlockReason
import app.screenbalance.engine.Classification
import app.screenbalance.engine.Decision
import app.screenbalance.engine.InstagramSignatures
import app.screenbalance.engine.PolicyContext
import app.screenbalance.engine.Redirect
import app.screenbalance.engine.Section
import app.screenbalance.engine.ShortVideoClassifier
import app.screenbalance.engine.ShortVideoSignatures
import app.screenbalance.engine.Snapshot
import app.screenbalance.engine.WatchedApp
import app.screenbalance.engine.ReelViewerTracker
import app.screenbalance.engine.ScreenClassifier
import app.screenbalance.engine.Surface
import app.screenbalance.model.AppSettings
import app.screenbalance.model.RuntimeState
import app.screenbalance.ui.intervention.InterventionActions
import app.screenbalance.ui.intervention.InterventionState
import app.screenbalance.widget.GoalWidget
import app.screenbalance.widget.ScreenTimeWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * The blocking engine's eyes and hands.
 *
 * Event flow: event from a watched app -> throttled evaluation -> [SnapshotBuilder] ->
 * [ScreenClassifier] (Instagram) or [ShortVideoClassifier] (YouTube, Facebook, Snapchat) ->
 * [app.screenbalance.engine.PolicyEngine] -> redirect under a blank cover, or the lock screen. Any other
 * app is only noticed by name when it comes to the front, to close it (quiet hours, focus, its daily
 * limit) or pause it; its screen is never read.
 *
 * Battery: events arrive only for the watched apps, unless quiet hours, a focus session, an app
 * limit or the pause needs every app; evaluations are coalesced to at most one every
 * [EVAL_INTERVAL_MS]; the heartbeat runs only while an app Glance manages is in front. No wake
 * locks, alarms or foreground services are used.
 */
class FocusAccessibilityService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var app: AppContainer
    private lateinit var overlay: InterventionOverlay

    private var settings: AppSettings? = null
    private var runtime: RuntimeState = RuntimeState()

    private val classifier = ScreenClassifier()
    private val shortVideo = ShortVideoClassifier()
    private val reelTracker = ReelViewerTracker()

    /**
     * Instagram (main app) time per section, every other watched app's time (incl. Instagram Lite),
     * and the time in any other app that has a daily limit.
     */
    private lateinit var sectionClock: UsageClock<Section>
    private lateinit var appClock: UsageClock<WatchedApp>
    private lateinit var packageClock: UsageClock<String>
    /** Time already written for today, and the day it was loaded for. */
    private var baseToday: Map<WatchedApp, Long> = emptyMap()
    private var basePackageToday: Map<String, Long> = emptyMap()
    private var baseDay: LocalDate? = null
    /** UsageStats totals for today by package (authoritative when granted), refreshed every [USAGE_REFRESH_MS]. */
    private var systemToday: Map<String, Long>? = null
    private var systemAt = 0L

    /** The app in front that Glance manages: a watched app ([currentApp]) or any other closeable app (null). */
    private var currentPkg: String? = null
    private var currentApp: WatchedApp? = null
    /** The app in front of any kind, for the pause before opening and "now on …". */
    private var foregroundPkg: String? = null
    /** The essentials: never closed, never paused. */
    private lateinit var system: SystemApps
    /** Apps the user chose to open after the pause: no second pause for a minute (uptime ms). */
    private val pausePassedUntil = HashMap<String, Long>()
    /** The app whose pause screen is showing. */
    private var pausing: String? = null

    /** Phone time (screen on and unlocked, any app), for Today. */
    private val phone = PhoneUse()
    /** The reminder: when the app in front came to the front (wall ms), and the minute mark for the next card. */
    private var inAppSince = 0L
    private var nextReminderMin = 0

    private var lastSurface: Surface? = null
    private var lastNonViewerSurface: Surface? = null
    private var lastEvalAt = 0L
    private var evalScheduled = false
    /** In search (typing, results, or the keyboard just closed): Explore next means "back out of search". */
    private var searchSession = false
    /** Home feed list position (first visible item, from vertical scrolls) and where "caught up" sat this visit. */
    private var feedPosition = 0
    private var feedEndIndex: Int? = null
    private var lastShownAt = 0L
    private var notInAppTicks = 0

    private var guardEvalAt = 0L
    private var flushJob: Job? = null
    private var screenWidth = 0
    private var screenHeight = 0

    // ---------------------------------------------------------------- lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        app = container
        overlay = InterventionOverlay(this)
        sectionClock = UsageClock({ day, m -> app.stats.addSectionTime(day, m) })
        appClock = UsageClock({ day, m -> app.stats.addAppTime(day, m) })
        packageClock = UsageClock({ day, m -> app.stats.addPackageTime(day, m) })
        system = SystemApps(this)
        WeeklySummaryReceiver.schedule(this)
        updateScreenSize()
        ServiceBridge.setConnected(true)

        scope.launch {
            combine(app.settings.settings.filterNotNull(), app.settings.runtime.filterNotNull()) { s, r -> s to r }
                .collect { (s, r) ->
                    val old = settings
                    val watchChanged = old == null || watchesEveryApp(old, runtime) != watchesEveryApp(s, r)
                    settings = s
                    runtime = r
                    if (watchChanged) applyWatchedPackages(s, r)
                    if (old != null && old.dailyGoalMinutes != s.dailyGoalMinutes) GoalWidget.refresh(this@FocusAccessibilityService)
                }
        }
        scope.launch {
            reloadBase()
            val today = LocalDate.now()
            phone.setStoredToday(today, app.stats.days(today, today).firstOrNull()?.phoneMs ?: 0L)
            if (isUnlockedNow()) onUnlocked()
        }
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun isUnlockedNow(): Boolean =
        getSystemService(PowerManager::class.java)?.isInteractive == true &&
            getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private var tornDown = false
    private fun teardown() {
        if (tornDown || !::app.isInitialized) return
        tornDown = true
        ServiceBridge.setConnected(false)
        runCatching { unregisterReceiver(screenReceiver) }
        main.removeCallbacksAndMessages(null)
        overlay.dismiss()
        val now = System.currentTimeMillis()
        sectionClock.leave(now)
        appClock.leave(now)
        packageClock.leave(now)
        val phoneMs = phone.onScreenOff(now)
        // Persist buffered time on the app scope: the service scope is about to be cancelled.
        app.appScope.launch {
            flushClocks()
            if (phoneMs > 0) app.stats.addPhoneTime(LocalDate.now(), phoneMs)
        }
        scope.cancel()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_USER_PRESENT -> onUnlocked()
                // Without a lock screen, turning the screen on is the unlock.
                Intent.ACTION_SCREEN_ON -> if (isUnlockedNow()) onUnlocked()
            }
        }
    }

    /**
     * Quiet hours, a focus session, an app limit, the pause or the reminder can apply to any app, so
     * then every app's events are delivered (the handler drops all but "an app came to the front" for
     * apps other than the watched ones).
     */
    private fun watchesEveryApp(s: AppSettings, r: RuntimeState) =
        s.openPause.enabled || s.reminderEveryMinutes > 0 || s.appLimits.values.any { it > 0 } ||
            (s.quietApps.reachesBeyondWatched && (s.schedules.any { it.enabled } || r.session != null))

    private fun applyWatchedPackages(s: AppSettings, r: RuntimeState) {
        val info = serviceInfo ?: return
        info.packageNames = if (watchesEveryApp(s, r)) null else (WatchedApp.ALL_PACKAGES + SystemApps.SETTINGS_PACKAGES).toTypedArray()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        serviceInfo = info
    }

    // ---------------------------------------------------------------- events

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        val s = settings ?: return
        if (!s.onboardingComplete) return
        val inFront = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (inFront) onAppInFront(pkg, s)
        val watched = WatchedApp.forPackage(pkg)
        when {
            watched != null -> {
                enterApp(pkg, watched)
                if (pkg == InstagramSignatures.PACKAGE) onInstagramEvent(event) else onOtherAppEvent(event, pkg, watched)
            }
            pkg in SystemApps.SETTINGS_PACKAGES -> {
                if (inFront) leaveApp()
                guardSettings()
            }
            else -> if (inFront) onAnyAppInFront(pkg)
        }
    }

    /** Any app but the watched ones: only its name is used, to close it when its time is up. */
    private fun onAnyAppInFront(pkg: String) {
        when {
            system.isCloseable(pkg) -> {
                enterApp(pkg, null)
                scheduleEvaluate()
            }
            pkg in system.homes || pkg in system.alwaysOpen && pkg !in system.neverPause -> leaveApp()
        }
    }

    private fun onInstagramEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && onTabClicked(event)) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val now = SystemClock.uptimeMillis()
            val id = event.source?.let { src ->
                val v = src.viewIdResourceName
                SnapshotBuilder.recycle(src)
                v
            }
            val shortId = id?.removePrefix(InstagramSignatures.ID_PREFIX)
            if (shortId != null && shortId in InstagramSignatures.REELS_PAGERS) {
                reelTracker.onPagerScrolled(event.fromIndex, event.toIndex, now)
            } else if (lastSurface == Surface.HOME_FEED && event.fromIndex >= 0) {
                // Only the vertical feed list counts, not sideways rows (stories, suggestions).
                val vertical = android.os.Build.VERSION.SDK_INT < 28 || event.scrollDeltaX == 0 || event.scrollDeltaY != 0
                if (vertical) feedPosition = event.fromIndex
            }
        }
        scheduleEvaluate()
    }

    /**
     * React to the tap on a bottom tab itself, before the screen behind it is even drawn: otherwise
     * Explore can be visible (and a Reel tappable) for the moment it takes to load and be recognised.
     */
    private fun onTabClicked(event: AccessibilityEvent): Boolean {
        if (overlay.isShowing) return false
        val s = settings ?: return false
        val src = event.source ?: return false
        val id = src.viewIdResourceName?.removePrefix(InstagramSignatures.ID_PREFIX)
        val desc = src.contentDescription?.toString()?.lowercase()
        SnapshotBuilder.recycle(src)
        val surface = when {
            id == InstagramSignatures.TAB_SEARCH || desc?.startsWith("search") == true -> Surface.EXPLORE
            id == InstagramSignatures.TAB_REELS || desc == "reels" -> Surface.REELS_TAB
            id == InstagramSignatures.TAB_HOME || desc == "home" -> Surface.HOME_FEED
            else -> return false
        }
        val ctx = contextFor(InstagramSignatures.PACKAGE, s, System.currentTimeMillis())
        val d = app.policy.decide(Classification(surface, null), s, runtime, ctx) as? Decision.Block ?: return false
        return when (d.redirect) {
            Redirect.FOCUS_SEARCH -> {
                searchSession = false
                openSearch(s)
                true
            }
            Redirect.GO_PROFILE -> {
                reelTracker.reset()
                coverThen(NavTarget.PROFILE, s, d.reason) { backOutUnderCover() }
                true
            }
            else -> false // Whole-Instagram closures go through the normal check.
        }
    }

    private fun scheduleEvaluate() {
        if (evalScheduled) return
        val wait = (EVAL_INTERVAL_MS - (SystemClock.uptimeMillis() - lastEvalAt)).coerceAtLeast(0)
        evalScheduled = true
        main.postDelayed({
            evalScheduled = false
            evaluateCurrent()
        }, wait)
    }

    private fun evaluateCurrent() {
        val pkg = currentPkg ?: return
        when (val a = currentApp) {
            null -> evaluatePackage(pkg)
            else -> if (pkg == InstagramSignatures.PACKAGE) evaluateInstagram() else evaluateOtherApp(pkg, a)
        }
    }

    // ---------------------------------------------------------------- which app is in front

    /** [a] = null: an app other than the watched ones, clocked only when it has a daily limit. */
    private fun enterApp(pkg: String, a: WatchedApp?) {
        if (currentPkg == pkg) return
        leaveApp()
        currentPkg = pkg
        currentApp = a
        notInAppTicks = 0
        val now = System.currentTimeMillis()
        when {
            a == null -> if ((settings?.limitForPackage(pkg) ?: 0) > 0) packageClock.observe(pkg, now)
            pkg != InstagramSignatures.PACKAGE -> appClock.observe(a, now) // Instagram is clocked per section.
        }
        main.removeCallbacks(heartbeat)
        main.postDelayed(heartbeat, HEARTBEAT_MS)
        if (flushJob?.isActive != true) {
            flushJob = scope.launch {
                while (true) {
                    kotlinx.coroutines.delay(FLUSH_MS)
                    flushClocks()
                }
            }
        }
    }

    /** Any app coming to the front (watched or not): the pause before opening, and the reminder's clock. */
    private fun onAppInFront(pkg: String, s: AppSettings) {
        // Home, Back or Recents during a pause leaves that app: the pause goes with it.
        val paused = pausing
        if (paused != null && pkg != paused && (pkg in system.homes || pkg !in system.neverPause)) {
            pausing = null
            overlay.dismiss()
        }
        if (pkg in system.neverPause) {
            if (pkg in system.homes) foregroundPkg = null // Back home: the next app opened is a fresh open.
            return
        }
        if (pkg == foregroundPkg) return
        foregroundPkg = pkg
        restartReminder()
        pauseBeforeOpening(pkg, s)
    }

    /**
     * The optional pause each time an app is opened: 10 seconds to decide. Skipped when the app is
     * closed anyway (the lock screen says why), or when the user went on through it a minute ago.
     */
    private fun pauseBeforeOpening(pkg: String, s: AppSettings) {
        if (!s.openPause.appliesTo(pkg) || !system.isLaunchable(pkg) || overlay.isShowing) return
        if (runtime?.emergencyActive(System.currentTimeMillis()) == true) return
        val nowUp = SystemClock.uptimeMillis()
        if ((pausePassedUntil[pkg] ?: 0L) > nowUp) return
        if (wholeAppDecision(pkg, s) is Decision.Block) return
        val state = InterventionState(reason = BlockReason.OPEN_PAUSE, appLabel = labelOf(pkg))
        overlay.show(
            state, s.theme,
            InterventionActions(
                onDismiss = {
                    pausing = null
                    pausePassedUntil[pkg] = SystemClock.uptimeMillis() + PAUSE_GRACE_MS
                    dismissLockScreen()
                },
                onLeave = {
                    pausing = null
                    overlay.dismiss()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    leaveApp()
                },
            ),
        )
        pausing = pkg
        lastShownAt = nowUp
    }

    private fun labelOf(pkg: String): String =
        WatchedApp.forPackage(pkg)?.label ?: try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            "this app"
        }

    private fun leaveApp() {
        if (currentPkg == null) return
        val now = System.currentTimeMillis()
        sectionClock.leave(now)
        appClock.leave(now)
        packageClock.leave(now)
        currentPkg = null
        currentApp = null
        reelTracker.reset()
        lastSurface = null
        feedPosition = 0
        feedEndIndex = null // Instagram may reload the feed next time.
        main.removeCallbacks(heartbeat)
        flushJob?.cancel()
        scope.launch { flushClocks() }
        publishUsage(now)
    }

    private val heartbeat = object : Runnable {
        override fun run() {
            val pkg = currentPkg ?: return
            val front = rootInActiveWindow?.let { r -> r.packageName?.toString().also { SnapshotBuilder.recycle(r) } }
            if (front == pkg) {
                notInAppTicks = 0
                val now = System.currentTimeMillis()
                sectionClock.tick(now)
                appClock.tick(now)
                packageClock.tick(now)
                publishUsage(now)
                evaluateCurrent() // Limits can trip while the user sits still.
            } else if (front != packageName) {
                // Our own overlay in front is not "leaving".
                if (++notInAppTicks >= 2) {
                    leaveApp()
                    return
                }
            }
            main.postDelayed(this, HEARTBEAT_MS)
        }
    }

    // ---------------------------------------------------------------- phone time and reminders

    /** The day finished days were last saved to the usage history. */
    private var savedOn: LocalDate? = null

    private fun onUnlocked() {
        val wasLocked = !phone.isUnlocked
        if (wasLocked) restartReminder()
        val now = System.currentTimeMillis()
        phone.onUnlocked(now)
        if (wasLocked) {
            // Phone time first (it starts over at midnight), so the widget never redraws yesterday's.
            ServiceBridge.setPhoneToday(phone.todayMs(now))
            ScreenTimeWidget.refresh(this)
            val today = LocalDate.now()
            if (savedOn != today) {
                savedOn = today
                scope.launch(Dispatchers.IO) { runCatching { app.history.saveRecent(today) } }
            }
        }
        main.removeCallbacks(phoneCheck)
        main.post(phoneCheck)
    }

    private fun onScreenOff() {
        val now = System.currentTimeMillis()
        val ms = phone.onScreenOff(now)
        main.removeCallbacks(phoneCheck)
        leaveApp()
        if (ms > 0) {
            scope.launch {
                app.stats.addPhoneTime(LocalDate.now(), ms)
                phone.storedTodayMs += ms
                ServiceBridge.setPhoneToday(phone.todayMs(System.currentTimeMillis()))
                ScreenTimeWidget.refresh(this@FocusAccessibilityService)
            }
        }
    }

    /** While the phone is unlocked: phone time for Today, and the reminder. */
    private val phoneCheck = object : Runnable {
        override fun run() {
            if (!phone.isUnlocked) return
            val now = System.currentTimeMillis()
            ServiceBridge.setPhoneToday(phone.todayMs(now))
            settings?.let { s -> if (s.onboardingComplete && !overlay.isShowing) nudgeIfDue(s, now) }
            main.postDelayed(this, PHONE_CHECK_MS)
        }
    }

    /** The reminder counts from when the app in front was opened (or the phone unlocked). */
    private fun restartReminder() {
        inAppSince = System.currentTimeMillis()
        nextReminderMin = 0
    }

    /** "20 min on YouTube": every N minutes in the same app, until it's left. */
    private fun nudgeIfDue(s: AppSettings, now: Long) {
        val every = s.reminderEveryMinutes
        val pkg = foregroundPkg ?: return
        if (every <= 0) return
        if (nextReminderMin < every) nextReminderMin = every
        val inAppMin = ((now - inAppSince) / 60_000L).toInt()
        if (inAppMin >= nextReminderMin) {
            nextReminderMin = (inAppMin / every + 1) * every
            showReminder(s, InterventionState(reason = BlockReason.USE_REMINDER, minutes = inAppMin, appLabel = labelOf(pkg)))
        }
    }

    /** The reminder card over whatever is open; "Keep going" closes it, "Take a break" goes home. */
    private fun showReminder(s: AppSettings, state: InterventionState) {
        overlay.show(
            state, s.theme,
            InterventionActions(
                onDismiss = ::dismissLockScreen,
                onLeave = {
                    overlay.dismiss()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    leaveApp()
                },
            ),
        )
        lastShownAt = SystemClock.uptimeMillis()
    }

    // ---------------------------------------------------------------- YouTube, Facebook, Snapchat, TikTok, X, Reddit, Instagram Lite

    private fun onOtherAppEvent(event: AccessibilityEvent, pkg: String, a: WatchedApp) {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && onShortVideoTabClicked(event, pkg, a)) return
        scheduleEvaluate()
    }

    /** React to the tap on the Shorts / Reels / Spotlight tab before its first video plays. */
    private fun onShortVideoTabClicked(event: AccessibilityEvent, pkg: String, a: WatchedApp): Boolean {
        val labels = ShortVideoSignatures.TAB_LABELS[a] ?: return false
        if (overlay.isShowing) return false
        val s = settings ?: return false
        val src = event.source ?: return false
        val label = (src.contentDescription ?: src.text)?.let(Snapshot::normalizeText)
        SnapshotBuilder.recycle(src)
        if (label == null || label !in labels) return false
        val now = System.currentTimeMillis()
        val d = app.policy.decideApp(a, shortVideo = true, s, runtime, contextFor(pkg, s, now)) as? Decision.Block ?: return false
        if (d.redirect != Redirect.SAFE_TAB) return false
        safeTabUnderCover(pkg, a, s)
        return true
    }

    private fun evaluateOtherApp(pkg: String, a: WatchedApp) {
        lastEvalAt = SystemClock.uptimeMillis()
        val s = settings ?: return
        if (overlay.isShowing) return
        // Only apps with a short-video section need to be looked at; the rest are whole-app only.
        val onShortVideo = ShortVideoSignatures.PLAYER_IDS.containsKey(a) && isShortVideoNow(pkg, a)
        val now = System.currentTimeMillis()
        val d = app.policy.decideApp(a, onShortVideo, s, runtime, contextFor(pkg, s, now)) as? Decision.Block ?: return
        if (d.redirect == Redirect.SAFE_TAB) safeTabUnderCover(pkg, a, s) else block(d, s, a.label)
    }

    /** Any other app: closed by quiet hours, a focus session or its own daily limit. */
    private fun evaluatePackage(pkg: String) {
        lastEvalAt = SystemClock.uptimeMillis()
        val s = settings ?: return
        if (overlay.isShowing) return
        val d = wholeAppDecision(pkg, s) as? Decision.Block ?: return
        block(d, s, labelOf(pkg))
    }

    /** Whether all of [pkg] is closed right now (sections aside); the essentials never are. */
    private fun wholeAppDecision(pkg: String, s: AppSettings): Decision {
        val ctx = contextFor(pkg, s, System.currentTimeMillis())
        val a = WatchedApp.forPackage(pkg)
        return when {
            a != null -> app.policy.decideApp(a, shortVideo = false, s, runtime, ctx)
            system.isCloseable(pkg) -> app.policy.decidePackage(s.limitForPackage(pkg), s, runtime, ctx)
            else -> Decision.Allow
        }
    }

    private fun contextFor(pkg: String, s: AppSettings, now: Long) =
        PolicyContext(now, ZoneId.systemDefault(), usageToday(pkg, now), false, false, quietCloses = s.quietApps.closes(pkg))

    private fun isShortVideoNow(pkg: String, a: WatchedApp): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            if (root.packageName?.toString() != pkg) return false
            shortVideo.isShortVideo(a, SnapshotBuilder.build(root, screenWidth, screenHeight, "$pkg:id/"))
        } catch (e: RuntimeException) {
            Log.d(TAG, "Snapshot failed", e)
            false
        } finally {
            SnapshotBuilder.recycle(root)
        }
    }

    /**
     * Moves YouTube / Facebook / Snapchat off the short-video feed under a blank cover (its sound
     * paused): to an allowed tab such as Subscriptions, Home or Chat, or Back. The cover lifts once
     * the short-video feed is gone.
     */
    private fun safeTabUnderCover(pkg: String, a: WatchedApp, s: AppSettings, attempt: Int = 0) {
        if (attempt == 0) showCover(s, BlockReason.SHORT_VIDEO)
        val labels = ShortVideoSignatures.SAFE_TAB_LABELS[a].orEmpty()
        if (labels.none { clickByLabel(pkg, it) }) performGlobalAction(GLOBAL_ACTION_BACK)
        main.postDelayed({
            if (!isShortVideoNow(pkg, a) || attempt >= NAV_ATTEMPTS) finishNavigation(null)
            else safeTabUnderCover(pkg, a, s, attempt + 1)
        }, NAV_VERIFY_MS + 150L)
    }

    /** Clicks the visible view whose label is [label] (or starts with "[label],", as tab labels do). */
    private fun clickByLabel(pkg: String, label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            if (root.packageName?.toString() != pkg) return false
            val want = label.lowercase()
            val nodes = root.findAccessibilityNodeInfosByText(label)
            try {
                for (n in nodes) {
                    if (!n.isVisibleToUser) continue
                    val l = (n.contentDescription ?: n.text)?.let(Snapshot::normalizeText) ?: continue
                    if (l != want && !l.startsWith("$want,")) continue
                    var node: AccessibilityNodeInfo? = n
                    var depth = 0
                    while (node != null && !node.isClickable && depth < 5) { node = node.parent; depth++ }
                    if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
                }
            } finally {
                nodes.forEach(SnapshotBuilder::recycle)
            }
            return false
        } finally {
            SnapshotBuilder.recycle(root)
        }
    }

    // ---------------------------------------------------------------- Instagram

    private fun evaluateInstagram() {
        lastEvalAt = SystemClock.uptimeMillis()
        val s = settings ?: return
        if (overlay.isShowing) return
        val root = rootInActiveWindow ?: return
        val snapshot = try {
            if (root.packageName?.toString() != InstagramSignatures.PACKAGE) return
            SnapshotBuilder.build(root, screenWidth, screenHeight)
        } catch (e: RuntimeException) {
            // Nodes can go stale mid-walk while Instagram animates; just try again next event.
            Log.d(TAG, "Snapshot failed", e)
            return
        } finally {
            SnapshotBuilder.recycle(root)
        }
        val c = classifier.classify(snapshot)
        val nowWall = System.currentTimeMillis()
        val nowUp = SystemClock.uptimeMillis()
        val previousSurface = lastSurface

        if (c.surface != Surface.REELS_VIEWER) lastNonViewerSurface = c.surface
        when (c.surface) {
            Surface.SEARCH -> searchSession = true
            Surface.EXPLORE, Surface.OTHER -> Unit
            else -> searchSession = false
        }
        reelTracker.onSurface(c.surface, lastNonViewerSurfaceBefore(c), nowUp)
        lastSurface = c.surface
        sectionClock.observe(c.surface.section, nowWall)

        val ctx = PolicyContext(
            nowMs = nowWall,
            zone = ZoneId.systemDefault(),
            usageTodayMs = usageToday(WatchedApp.INSTAGRAM, nowWall),
            reelViewerIntentional = reelTracker.openedIntentionally,
            reelViewerSwiped = reelTracker.swiped,
            feedPastEnd = c.surface == Surface.HOME_FEED && feedEndIndex?.let { feedPosition >= it } == true,
            quietCloses = s.quietApps.closes(InstagramSignatures.PACKAGE),
        )
        if (c.surface == Surface.HOME_FEED && c.feedEndVisible) {
            feedEndIndex = feedEndIndex?.let { minOf(it, feedPosition) } ?: feedPosition
        }
        val decision = app.policy.decide(c, s, runtime, ctx)
        if (decision is Decision.Block) act(decision, c, s, previousSurface)
    }

    /** Carries out a decision for Instagram itself. */
    private fun act(d: Decision.Block, c: Classification, s: AppSettings, previousSurface: Surface?) {
        // Section blocks never show a lock screen: a blank cover hides the screen for a moment while
        // Instagram is moved somewhere allowed underneath.
        when (d.redirect) {
            Redirect.FOCUS_SEARCH ->
                if (searchSession || previousSurface == Surface.SEARCH) {
                    // Backing out of search lands on Explore, and search has no tab bar to leave by.
                    searchSession = false
                    val out = if (s.rules.blockHomeFeed) NavTarget.PROFILE else NavTarget.HOME
                    coverThen(out, s, d.reason) { backOutUnderCover() }
                } else {
                    // The Search tab opens on Explore: go straight into the search box.
                    openSearch(s)
                }
            Redirect.GO_PROFILE -> {
                reelTracker.reset()
                coverThen(NavTarget.PROFILE, s, d.reason) { backOutUnderCover() }
            }
            Redirect.SNAP_BACK -> snapBackToStartReel(s)
            Redirect.FEED_TOP -> scrollFeedToTopUnderCover(s)
            // All of Instagram is closed (daily limit, quiet hours, focus): the lock screen says why
            // and until when.
            else -> block(d, s, WatchedApp.INSTAGRAM.label)
        }
    }

    /** The viewer's origin is the surface seen right before it opened. */
    private var originCandidate: Surface? = null
    private fun lastNonViewerSurfaceBefore(c: Classification): Surface? {
        if (c.surface != Surface.REELS_VIEWER) {
            originCandidate = c.surface
            return null
        }
        return originCandidate
    }

    // ---------------------------------------------------------------- settings guard

    /**
     * During a focus session or quiet hours, opening Glance's own entry in Accessibility settings
     * (or its App info page) bounces back, so protection can't be switched off meanwhile.
     */
    private fun guardSettings() {
        val s = settings ?: return
        val now = System.currentTimeMillis()
        val policy = app.policy.effective(s, runtime, now, ZoneId.systemDefault())
        if (!policy.locked || overlay.isShowing) return
        val nowUp = SystemClock.uptimeMillis()
        if (nowUp - guardEvalAt < GUARD_INTERVAL_MS) return
        guardEvalAt = nowUp
        val root = rootInActiveWindow ?: return
        val mentionsUs = try {
            val labels = listOf(getString(R.string.accessibility_service_label), getString(R.string.app_name))
            labels.any { label -> root.findAccessibilityNodeInfosByText(label).let { n -> val hit = n.isNotEmpty(); n.forEach(SnapshotBuilder::recycle); hit } }
        } finally {
            SnapshotBuilder.recycle(root)
        }
        if (!mentionsUs) return
        block(Decision.Block(BlockReason.SETTINGS_LOCKED, Redirect.BACK_OUT_OF_SETTINGS, policy.until), s, labelOf(root.packageName?.toString() ?: ""))
    }

    // ---------------------------------------------------------------- lock screen

    /**
     * The lock screen: only when a whole app is closed (daily limit, quiet hours, focus), and for
     * the Settings guard. It covers the screen at once; then whatever is
     * behind it is moved away. The overlay is not focusable, so back presses still reach the app.
     */
    private fun block(d: Decision.Block, s: AppSettings, appLabel: String) {
        val nowUp = SystemClock.uptimeMillis()
        if (nowUp - lastShownAt < MIN_REBLOCK_MS || overlay.isShowing) return
        lastShownAt = nowUp
        reelTracker.reset()
        // A whole app closed (not a section, not the Settings guard): emergency access is offered.
        val blockedPkg = foregroundPkg ?: currentPkg
        val offerEmergency = d.redirect == Redirect.LEAVE_APP
        val state = InterventionState(
            reason = d.reason, copy = InterventionCopy.forReason(d.reason, appLabel), until = d.until,
            emergencyLeft = if (offerEmergency) runtime?.emergencyLeft(LocalDate.now()) ?: 0 else null,
        )
        overlay.show(state, s.theme, InterventionActions(onDismiss = ::dismissLockScreen, onEmergency = { startEmergency(blockedPkg) }))
        redirect(d.redirect)
    }

    /** Emergency access: everything opens for a few minutes; the app that was closed opens again. */
    private fun startEmergency(pkg: String?) {
        scope.launch {
            if (!app.settings.startEmergency()) return@launch
            main.post {
                dismissLockScreen()
                val launch = pkg?.let { packageManager.getLaunchIntentForPackage(it) } ?: return@post
                runCatching { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
    }

    private fun dismissLockScreen() {
        overlay.dismiss()
        lastShownAt = SystemClock.uptimeMillis()
        main.postDelayed({ scheduleEvaluate() }, POST_DISMISS_DELAY_MS)
    }

    private fun redirect(r: Redirect) {
        when (r) {
            Redirect.BACK_OUT_OF_SETTINGS -> performGlobalAction(GLOBAL_ACTION_BACK)
            Redirect.LEAVE_APP -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
                leaveApp()
            }
            Redirect.FOCUS_SEARCH -> if (InstagramSignatures.SEARCH_INPUT.none { clickInstagram(it) }) clickSearchBarByLabel()
            // Handled under a cover by [act].
            Redirect.GO_PROFILE, Redirect.SNAP_BACK, Redirect.FEED_TOP, Redirect.SAFE_TAB -> Unit
        }
    }

    // ---------------------------------------------------------------- redirects under a cover

    /** Allowed places Instagram is moved to. */
    private enum class NavTarget { HOME, SEARCH, PROFILE }

    private fun showCover(s: AppSettings, reason: BlockReason, pauseAudio: Boolean = true) {
        val cover = InterventionState(reason = reason, coverOnly = true, pauseAudio = pauseAudio)
        overlay.show(cover, s.theme, InterventionActions(onDismiss = {}))
        lastShownAt = SystemClock.uptimeMillis()
    }

    /** Shows a blank cover at once, then moves Instagram to [dest] underneath (see [goUnderOverlay]). */
    private fun coverThen(dest: NavTarget, s: AppSettings, reason: BlockReason, onFail: () -> Unit) {
        showCover(s, reason)
        goUnderOverlay(dest, onFail = onFail)
    }

    /** Into the search box; to the profile if it can't be reached. */
    private fun openSearch(s: AppSettings) {
        coverThen(NavTarget.SEARCH, s, BlockReason.EXPLORE) {
            goUnderOverlay(NavTarget.PROFILE) { backOutUnderCover() }
        }
    }

    /**
     * Tapping Home while on the feed scrolls it to the top. Retried until the "caught up" marker is
     * gone; then the cover lifts.
     */
    private fun scrollFeedToTopUnderCover(s: AppSettings, attempt: Int = 0) {
        if (attempt == 0) showCover(s, BlockReason.FEED_END)
        if (!clickInstagram(InstagramSignatures.TAB_HOME)) performGlobalAction(GLOBAL_ACTION_BACK)
        main.postDelayed({
            val c = currentClassification()
            val atTop = c != null && c.surface == Surface.HOME_FEED && !c.feedEndVisible
            if (atTop || attempt >= NAV_ATTEMPTS) {
                feedPosition = 0
                finishNavigation(c?.surface)
            } else {
                scrollFeedToTopUnderCover(s, attempt + 1)
            }
        }, FEED_TOP_SETTLE_MS)
    }

    /**
     * Scrolls the Reels pager back to the Reel that was opened, under a cover that hides the next one.
     * Audio is not paused: the user's own Reel should simply carry on. Falls back to the profile.
     */
    private fun snapBackToStartReel(s: AppSettings, attempt: Int = 0) {
        if (attempt == 0) showCover(s, BlockReason.REELS, pauseAudio = false)
        val start = reelTracker.startIndex
        val now = reelTracker.currentIndex
        val forward = start >= 0 && now >= 0 && now < start
        val scrolled = scrollReelsPager(forward)
        main.postDelayed({
            val back = start >= 0 && reelTracker.currentIndex == start
            when {
                back || (scrolled && start < 0) -> {
                    reelTracker.backAtStart()
                    finishNavigation(Surface.REELS_VIEWER)
                }
                attempt < SNAP_BACK_ATTEMPTS -> snapBackToStartReel(s, attempt + 1)
                else -> {
                    reelTracker.reset()
                    goUnderOverlay(NavTarget.PROFILE) { backOutUnderCover() }
                }
            }
        }, NAV_VERIFY_MS + 150L)
    }

    private fun scrollReelsPager(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            if (root.packageName?.toString() != InstagramSignatures.PACKAGE) return false
            for (id in InstagramSignatures.REELS_PAGERS) {
                val nodes = root.findAccessibilityNodeInfosByViewId(InstagramSignatures.ID_PREFIX + id)
                try {
                    val pager = nodes.firstOrNull { it.isVisibleToUser } ?: continue
                    val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    if (pager.performAction(action)) return true
                } finally {
                    nodes.forEach(SnapshotBuilder::recycle)
                }
            }
            return false
        } finally {
            SnapshotBuilder.recycle(root)
        }
    }

    /** Last resort when a redirect target can't be reached: step back, lift the cover, re-check. */
    private fun backOutUnderCover() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        main.postDelayed({ finishNavigation(null) }, BACK_VERIFY_MS)
    }

    /**
     * Moves Instagram to [dest] while the cover is up, and lifts the cover only once [dest] is
     * showing, so nothing blocked flashes in between. Retries a few times, then [onFail].
     */
    private fun goUnderOverlay(dest: NavTarget, attempt: Int = 0, onFail: (() -> Unit)? = null) {
        if (!arrived(dest)) navigate(dest, attempt)
        main.postDelayed({
            when {
                arrived(dest) -> finishNavigation(arrivedSurface(dest))
                attempt >= NAV_ATTEMPTS -> onFail?.invoke() ?: finishNavigation(null)
                else -> goUnderOverlay(dest, attempt + 1, onFail)
            }
        }, if (dest == NavTarget.SEARCH && attempt == 0) SEARCH_FOCUS_DELAY_MS + NAV_VERIFY_MS else NAV_VERIFY_MS)
    }

    private fun navigate(dest: NavTarget, attempt: Int) {
        when (dest) {
            NavTarget.HOME -> if (!clickInstagram(InstagramSignatures.TAB_HOME)) performGlobalAction(GLOBAL_ACTION_BACK)
            NavTarget.PROFILE -> if (!clickInstagram(InstagramSignatures.TAB_PROFILE)) performGlobalAction(GLOBAL_ACTION_BACK)
            NavTarget.SEARCH ->
                // The Search tab opens on Explore; then go on into the search box.
                if (attempt == 0) {
                    clickInstagram(InstagramSignatures.TAB_SEARCH)
                    main.postDelayed({ if (!searchBoxFocused()) redirect(Redirect.FOCUS_SEARCH) }, SEARCH_FOCUS_DELAY_MS)
                } else {
                    redirect(Redirect.FOCUS_SEARCH)
                }
        }
    }

    private fun arrivedSurface(dest: NavTarget): Surface = when (dest) {
        NavTarget.HOME -> Surface.HOME_FEED
        NavTarget.PROFILE -> Surface.PROFILE
        NavTarget.SEARCH -> Surface.SEARCH
    }

    private fun arrived(dest: NavTarget): Boolean =
        if (dest == NavTarget.SEARCH) searchBoxFocused() else currentClassification()?.surface == arrivedSurface(dest)

    /** Classifies Instagram's screen right now (underneath the overlay, which is not focusable). */
    private fun currentClassification(): Classification? {
        val root = rootInActiveWindow ?: return null
        return try {
            if (root.packageName?.toString() != InstagramSignatures.PACKAGE) return null
            classifier.classify(SnapshotBuilder.build(root, screenWidth, screenHeight))
        } catch (e: RuntimeException) {
            null
        } finally {
            SnapshotBuilder.recycle(root)
        }
    }

    private fun finishNavigation(surface: Surface?) {
        overlay.dismiss()
        lastShownAt = SystemClock.uptimeMillis()
        lastSurface = surface
        searchSession = surface == Surface.SEARCH
        scheduleEvaluate()
        recheckSoon()
    }

    /**
     * Looks again shortly after a move: a feed that was still loading when the cover lifted (a black
     * Shorts page, say) can start playing without sending any further accessibility events, so
     * waiting for the next event or the 5-second heartbeat would let it play for seconds.
     */
    private fun recheckSoon() {
        main.removeCallbacks(recheck)
        for (delay in RECHECK_DELAYS_MS) main.postDelayed(recheck, delay)
    }

    private val recheck = Runnable { if (!overlay.isShowing) evaluateCurrent() }

    private fun searchBoxFocused(): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            if (root.packageName?.toString() != InstagramSignatures.PACKAGE) return false
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            val ok = focused.isEditable && focused.viewIdResourceName?.removePrefix(InstagramSignatures.ID_PREFIX)
                .let { it == null || it in InstagramSignatures.SEARCH_INPUT }
            SnapshotBuilder.recycle(focused)
            return ok
        } finally {
            SnapshotBuilder.recycle(root)
        }
    }

    private fun clickInstagram(shortId: String): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            if (root.packageName?.toString() != InstagramSignatures.PACKAGE) return false
            val nodes = root.findAccessibilityNodeInfosByViewId(InstagramSignatures.ID_PREFIX + shortId)
            try {
                var node: AccessibilityNodeInfo? = nodes.firstOrNull { it.isVisibleToUser } ?: return false
                // The id often sits on a child of the clickable view; climb to the clickable ancestor.
                var depth = 0
                while (node != null && !node.isClickable && depth < 5) {
                    node = node.parent
                    depth++
                }
                return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
            } finally {
                nodes.forEach(SnapshotBuilder::recycle)
            }
        } finally {
            SnapshotBuilder.recycle(root)
        }
    }

    /** Fallback when the search box id is unknown: the "Search" box in the top third of the screen. */
    private fun clickSearchBarByLabel(): Boolean {
        val root = rootInActiveWindow ?: return false
        val rect = android.graphics.Rect()
        try {
            if (root.packageName?.toString() != InstagramSignatures.PACKAGE) return false
            for (label in listOf("Search", "Search with Meta AI", "Ask Meta AI or Search")) {
                val nodes = root.findAccessibilityNodeInfosByText(label)
                try {
                    for (n in nodes) {
                        n.getBoundsInScreen(rect)
                        if (!n.isVisibleToUser || rect.top > screenHeight / 3) continue
                        var node: AccessibilityNodeInfo? = n
                        var depth = 0
                        while (node != null && !node.isClickable && depth < 5) { node = node.parent; depth++ }
                        if (node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
                    }
                } finally {
                    nodes.forEach(SnapshotBuilder::recycle)
                }
            }
            return false
        } finally {
            SnapshotBuilder.recycle(root)
        }
    }

    // ---------------------------------------------------------------- usage

    /** Today's use of the app with package [pkg]; for a watched app, all of its packages together. */
    private fun usageToday(pkg: String, now: Long): Long {
        WatchedApp.forPackage(pkg)?.let { return usageToday(it, now) }
        refreshToday()
        val tracked = (basePackageToday[pkg] ?: 0L) + packageClock.pendingToday(now) { it == pkg }
        return maxOf(tracked, systemToday?.get(pkg) ?: 0L)
    }

    /** Today's use of [a]: UsageStats when granted, else what Glance measured (the larger wins). */
    private fun usageToday(a: WatchedApp, now: Long): Long {
        refreshToday()
        val pending = appClock.pendingToday(now) { it == a } +
            if (a == WatchedApp.INSTAGRAM) sectionClock.pendingToday(now) else 0L
        val tracked = (baseToday[a] ?: 0L) + pending
        val system = systemToday?.let { m -> a.packages.sumOf { m[it] ?: 0L } } ?: 0L
        return maxOf(tracked, system)
    }

    /** A new day starts from zero; UsageStats totals are re-read at most every [USAGE_REFRESH_MS]. */
    private fun refreshToday() {
        val today = LocalDate.now()
        if (baseDay != today) {
            baseDay = today
            baseToday = emptyMap()
            basePackageToday = emptyMap()
            scope.launch { reloadBase() }
        }
        val nowUp = SystemClock.uptimeMillis()
        if (systemAt > 0 && nowUp - systemAt < USAGE_REFRESH_MS) return
        systemAt = nowUp
        val packages = WatchedApp.ALL_PACKAGES + settings?.appLimits?.keys.orEmpty()
        scope.launch { systemToday = withContext(Dispatchers.IO) { app.usage.foregroundByPackage(today, packages) } }
    }

    private fun publishUsage(now: Long) {
        ServiceBridge.setUsedToday(
            WatchedApp.entries.associateWith { usageToday(it, now) },
            settings?.appLimits?.keys.orEmpty().associateWith { usageToday(it, now) },
        )
    }

    private suspend fun flushClocks() {
        sectionClock.flush()
        appClock.flush()
        packageClock.flush()
        reloadBase()
    }

    /** What is already written for today; pending (unflushed) time is added on top. */
    private suspend fun reloadBase() {
        val today = LocalDate.now()
        val day = app.stats.days(today, today).firstOrNull() ?: return
        baseToday = WatchedApp.entries.associateWith { day.trackedFor(it) }
        basePackageToday = day.packageMs
        baseDay = today
        publishUsage(System.currentTimeMillis())
    }

    private fun updateScreenSize() {
        val wm = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            screenWidth = b.width()
            screenHeight = b.height()
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            screenWidth = dm.widthPixels
            screenHeight = dm.heightPixels
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenSize()
    }

    private companion object {
        const val TAG = "GlanceService"
        const val EVAL_INTERVAL_MS = 250L
        const val HEARTBEAT_MS = 5_000L
        const val FLUSH_MS = 60_000L
        const val GUARD_INTERVAL_MS = 400L
        const val MIN_REBLOCK_MS = 1_200L
        const val POST_DISMISS_DELAY_MS = 250L
        const val BACK_VERIFY_MS = 350L
        const val SEARCH_FOCUS_DELAY_MS = 450L
        const val NAV_VERIFY_MS = 250L
        const val NAV_ATTEMPTS = 4
        const val PAUSE_GRACE_MS = 60_000L
        const val PHONE_CHECK_MS = 30_000L
        /** Follow-up looks after a move, for screens that finish loading late. */
        val RECHECK_DELAYS_MS = longArrayOf(700L, 1_500L, 3_000L)
        const val SNAP_BACK_ATTEMPTS = 2
        const val FEED_TOP_SETTLE_MS = 450L
        const val USAGE_REFRESH_MS = 60_000L
    }
}
