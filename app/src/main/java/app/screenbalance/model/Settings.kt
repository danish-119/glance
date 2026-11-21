package app.screenbalance.model

import app.screenbalance.engine.WatchedApp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Settings from older versions still load: unknown keys are ignored and unknown enum values fall
// back to the default (see SettingsRepository's Json configuration).

@Serializable
enum class ThemePreference { SYSTEM, LIGHT, DARK }

/** What is closed inside the apps that have sections. */
@Serializable
data class BlockRules(
    /** Instagram: the Reels tab and the endless Reels viewer. A Reel opened on purpose (e.g. sent by a friend) still plays. */
    val blockReels: Boolean = true,
    /** Instagram: the Explore grid. Searching for people by name stays available. */
    val blockExplore: Boolean = true,
    /** Instagram: the Home tab, feed and stories row. When closed, Instagram opens on the user's profile instead. */
    val blockHomeFeed: Boolean = false,
    /** YouTube: the Shorts tab and player. Normal videos, search and subscriptions stay open. */
    val blockShorts: Boolean = true,
    /** Facebook: the Reels and Video tabs and the Reels player. The feed, groups and profiles stay open. */
    val blockFacebookVideo: Boolean = true,
    /** Snapchat: the Spotlight tab and player. Chats, the camera, stories and the map stay open. */
    val blockSpotlight: Boolean = true,
) {
    /** Whether [app]'s short-video section is closed. */
    fun blocksShortVideo(app: WatchedApp): Boolean = when (app) {
        WatchedApp.INSTAGRAM -> blockReels
        WatchedApp.YOUTUBE -> blockShorts
        WatchedApp.FACEBOOK -> blockFacebookVideo
        WatchedApp.SNAPCHAT -> blockSpotlight
        WatchedApp.TIKTOK, WatchedApp.X, WatchedApp.REDDIT -> false
    }
}

/**
 * The 10-second pause before an app opens. Either every app (minus [exceptApps]) or just [apps].
 * Packages; the home screen, keyboards, the phone dialer and Glance itself are never paused.
 */
@Serializable
data class OpenPause(
    val enabled: Boolean = false,
    val allApps: Boolean = false,
    val apps: Set<String> = emptySet(),
    val exceptApps: Set<String> = emptySet(),
) {
    fun appliesTo(pkg: String): Boolean = enabled && if (allApps) pkg !in exceptApps else pkg in apps

    fun withApp(pkg: String, on: Boolean): OpenPause =
        if (allApps) copy(exceptApps = if (on) exceptApps - pkg else exceptApps + pkg)
        else copy(apps = if (on) apps + pkg else apps - pkg)
}

/**
 * The apps quiet hours and focus sessions close: the chosen [apps] (by default the seven watched
 * apps), or every app except [exceptApps]. The essentials never close.
 */
@Serializable
data class QuietApps(
    val allApps: Boolean = false,
    val apps: Set<String> = WatchedApp.ALL_PACKAGES,
    val exceptApps: Set<String> = emptySet(),
) {
    fun closes(pkg: String): Boolean = if (allApps) pkg !in exceptApps else pkg in apps

    fun withApp(pkg: String, on: Boolean): QuietApps =
        if (allApps) copy(exceptApps = if (on) exceptApps - pkg else exceptApps + pkg)
        else copy(apps = if (on) apps + pkg else apps - pkg)

    /** Whether an app other than the watched ones can close, so the service must watch every app. */
    val reachesBeyondWatched: Boolean get() = allApps || apps.any { it !in WatchedApp.ALL_PACKAGES }
}

@Serializable
data class Schedule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    /** ISO day-of-week numbers, 1 = Monday .. 7 = Sunday. */
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    /** Minutes after midnight. If end <= start the window runs overnight. The chosen apps are closed throughout. */
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 17 * 60,
)

/**
 * Strict mode, chosen in advance: while it applies, a change that loosens a rule waits until the
 * session or quiet hours end, a focus session can't be ended early, and Glance's Accessibility
 * switch is guarded. Off by default: nothing is locked.
 */
@Serializable
data class StrictMode(
    val focusSessions: Boolean = false,
    val quietHours: Boolean = false,
) {
    val any get() = focusSessions || quietHours
}

@Serializable
data class AppSettings(
    val onboardingComplete: Boolean = false,
    /** Version of the accessibility disclosure the user explicitly agreed to (0 = never). */
    val disclosureVersionAccepted: Int = 0,
    val rules: BlockRules = BlockRules(),
    /** Daily limit per app in minutes; missing or 0 = no limit. Once reached, that app is closed until tomorrow. */
    val dailyLimits: Map<WatchedApp, Int> = emptyMap(),
    /** The single Instagram limit of earlier versions; read through [limitFor] until a new limit is set. */
    @SerialName("dailyLimitMinutes") val legacyInstagramLimit: Int = 0,
    /** Daily limits for any other app, by package. Set on the Limits tab. */
    val appLimits: Map<String, Int> = emptyMap(),
    /** Quiet hours: [quietApps] are closed and every app's notifications wait. */
    val schedules: List<Schedule> = emptyList(),
    /** The apps quiet hours and focus sessions close. */
    val quietApps: QuietApps = QuietApps(),
    val theme: ThemePreference = ThemePreference.SYSTEM,
    /** A reminder every N minutes in the same app (the clock restarts when another app opens); 0 = off. */
    val reminderEveryMinutes: Int = 0,
    val openPause: OpenPause = OpenPause(),
    val strict: StrictMode = StrictMode(),
    /** Daily screen-time goal in minutes; 0 = none. A goal, not a limit: nothing closes. */
    val dailyGoalMinutes: Int = 0,
    /** A notification on Monday morning with last week's screen time. */
    val weeklySummary: Boolean = true,
) {
    /** Today's limit for [app] in minutes, 0 = none. */
    fun limitFor(app: WatchedApp): Int =
        dailyLimits[app] ?: if (app == WatchedApp.INSTAGRAM) legacyInstagramLimit else 0

    fun withLimit(app: WatchedApp, minutes: Int): AppSettings = copy(
        dailyLimits = dailyLimits + (app to minutes),
        legacyInstagramLimit = if (app == WatchedApp.INSTAGRAM) 0 else legacyInstagramLimit,
    )

    /** Today's limit for the app with package [pkg] in minutes, 0 = none. */
    fun limitForPackage(pkg: String): Int = WatchedApp.forPackage(pkg)?.let(::limitFor) ?: appLimits[pkg] ?: 0

    /**
     * Whether [next] weakens anything: a closed section opened, a limit raised or removed, active quiet
     * hours removed or changed, fewer apps closed or paused, the reminder later or off, strict mode switched
     * off. Checked only while [app.screenbalance.engine.EffectivePolicy.locked]
     * (strict mode during a focus session or quiet hours).
     */
    fun loosens(next: AppSettings): Boolean {
        val a = rules
        val b = next.rules
        val sectionOpened = (a.blockReels && !b.blockReels) || (a.blockExplore && !b.blockExplore) ||
            (a.blockHomeFeed && !b.blockHomeFeed) || (a.blockShorts && !b.blockShorts) ||
            (a.blockFacebookVideo && !b.blockFacebookVideo) || (a.blockSpotlight && !b.blockSpotlight)
        val limitRaised = WatchedApp.entries.any { raised(limitFor(it), next.limitFor(it)) } ||
            appLimits.any { (pkg, m) -> raised(m, next.appLimits[pkg] ?: 0) }
        // A name change is harmless; anything else about an active window (or removing it) is not.
        val windows = { list: List<Schedule> -> list.filter { it.enabled }.map { it.copy(name = "") } }
        val quietRemoved = !windows(next.schedules).containsAll(windows(schedules))
        val fewerClosed = shrinks(quietApps.allApps, quietApps.apps, quietApps.exceptApps,
            next.quietApps.allApps, next.quietApps.apps, next.quietApps.exceptApps)
        val fewerPaused = openPause.enabled && (!next.openPause.enabled || shrinks(openPause.allApps, openPause.apps,
            openPause.exceptApps, next.openPause.allApps, next.openPause.apps, next.openPause.exceptApps))
        val strictOff = (strict.focusSessions && !next.strict.focusSessions) || (strict.quietHours && !next.strict.quietHours)
        return sectionOpened || limitRaised || quietRemoved || fewerClosed || fewerPaused ||
            raised(reminderEveryMinutes, next.reminderEveryMinutes) || strictOff
    }

    /** Sets the limit of the app with package [pkg] (a watched app shares one limit across its packages); 0 = off. */
    fun withPackageLimit(pkg: String, minutes: Int): AppSettings =
        WatchedApp.forPackage(pkg)?.let { withLimit(it, minutes) }
            ?: copy(appLimits = if (minutes > 0) appLimits + (pkg to minutes) else appLimits - pkg)

    companion object {
        const val CURRENT_DISCLOSURE_VERSION = 2
    }
}

/** Minutes (0 = none) became looser: removed, or longer. */
private fun raised(old: Int, new: Int) = old > 0 && (new == 0 || new > old)

/** An app selection ("chosen [apps]" or "all but [except]") now covers fewer apps. */
private fun shrinks(
    oldAll: Boolean, oldApps: Set<String>, oldExcept: Set<String>,
    newAll: Boolean, newApps: Set<String>, newExcept: Set<String>,
): Boolean = when {
    !oldAll && !newAll -> !newApps.containsAll(oldApps)
    !oldAll -> newExcept.any { it in oldApps }
    !newAll -> true
    else -> !oldExcept.containsAll(newExcept)
}

/** A focus session: like quiet hours, the chosen apps are closed until [endsAtMs]. */
@Serializable
data class FocusSession(
    val startedAtMs: Long,
    val endsAtMs: Long,
)

/** Mutable runtime state that must survive process death and reboots. */
@Serializable
data class RuntimeState(
    val session: FocusSession? = null,
    /** Emergency access: every app open until this moment (epoch ms), strict mode included. */
    val emergencyUntilMs: Long = 0L,
    /** The day (ISO date) [emergencyUsed] counts for. */
    val emergencyDay: String = "",
    val emergencyUsed: Int = 0,
) {
    fun emergencyActive(nowMs: Long) = emergencyUntilMs > nowMs

    /** Emergency passes left today. */
    fun emergencyLeft(today: java.time.LocalDate): Int =
        EMERGENCY_PER_DAY - if (emergencyDay == today.toString()) emergencyUsed else 0

    companion object {
        const val EMERGENCY_PER_DAY = 3
        const val EMERGENCY_MINUTES = 5
    }
}
