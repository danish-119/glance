package app.screenbalance.engine

import app.screenbalance.model.AppSettings
import app.screenbalance.model.BlockRules
import app.screenbalance.model.RuntimeState
import app.screenbalance.model.Schedule
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Why something was blocked. */
enum class BlockReason {
    REELS,
    EXPLORE,
    /** The Home tab, closed by the user's choice. */
    HOME_FEED,
    /** "You've completely caught up" reached: always stops there, whatever the Home feed switch. */
    FEED_END,
    /** YouTube Shorts, Facebook Reels/Video, Snapchat Spotlight. */
    SHORT_VIDEO,
    /** Not a block: the optional pause before an app opens. */
    OPEN_PAUSE,
    /** Not a block: "20 min on YouTube", every N minutes in the same app. */
    USE_REMINDER,
    DAILY_LIMIT,
    FOCUS_SESSION,
    SCHEDULE,
    SETTINGS_LOCKED,
}

/**
 * What happens next. Section blocks never show a lock screen: the app is moved somewhere allowed
 * under a blank cover. Only [LEAVE_APP] (the whole app closed) and [BACK_OUT_OF_SETTINGS] come with
 * the lock screen.
 */
enum class Redirect {
    /** To the user's own profile tab. */
    GO_PROFILE,
    /** Back onto the Reel that was opened on purpose (e.g. sent by a friend), after a swipe away. */
    SNAP_BACK,
    /** Into the search box: search by name stays allowed, the Explore grid does not. */
    FOCUS_SEARCH,
    /** Back to the top of the Home feed, where stories are. */
    FEED_TOP,
    /** YouTube, Facebook, Snapchat: to an allowed tab (e.g. Subscriptions, Home, Chat), or Back. */
    SAFE_TAB,
    /** Out of the app to the home screen. */
    LEAVE_APP,
    BACK_OUT_OF_SETTINGS,
}

sealed interface Decision {
    data object Allow : Decision
    data class Block(
        val reason: BlockReason,
        val redirect: Redirect,
        /** Epoch ms when the restriction ends, if it is time-bound (session, schedule, daily limit). */
        val until: Long? = null,
    ) : Decision
}

/** The rules actually in force right now after sessions and schedules are applied. */
data class EffectivePolicy(
    val rules: BlockRules,
    val source: Source,
    /**
     * Strict mode applies: loosening waits, the session can't be ended early and the accessibility
     * settings page is guarded. Without strict mode everything can be changed or ended any time.
     */
    val locked: Boolean,
    /** The chosen apps are closed (focus session or quiet hours). */
    val blockEntirely: Boolean,
    val until: Long?,
) {
    enum class Source { NORMAL, SCHEDULE, FOCUS_SESSION }
}

object ScheduleMath {
    /** End (epoch ms) of the protected window active at [now] across all schedules, or null. */
    fun activeUntil(schedules: List<Schedule>, now: ZonedDateTime): Long? =
        schedules.mapNotNull { activeUntil(it, now) }.maxOrNull()

    /** The next moment (epoch ms, after [now]) a protected window starts, within the coming 8 days. */
    fun nextStart(schedules: List<Schedule>, now: ZonedDateTime): Long? {
        val nowMs = now.toInstant().toEpochMilli()
        return schedules.filter { it.enabled && it.days.isNotEmpty() }.flatMap { s ->
            (0..7).mapNotNull { offset ->
                val day = now.toLocalDate().plusDays(offset.toLong())
                if (day.dayOfWeek.value !in s.days) null
                else day.atStartOfDay(now.zone).plusMinutes(s.startMinute.coerceIn(0, 1439).toLong())
                    .toInstant().toEpochMilli().takeIf { it > nowMs }
            }
        }.minOrNull()
    }

    /** Returns the end instant (epoch ms) if [schedule] is active at [now], else null. */
    fun activeUntil(schedule: Schedule, now: ZonedDateTime): Long? {
        if (!schedule.enabled || schedule.days.isEmpty()) return null
        val start = schedule.startMinute.coerceIn(0, 1439)
        val end = schedule.endMinute.coerceIn(0, 1440)
        val minute = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value
        val yesterday = if (today == 1) 7 else today - 1
        val midnight = now.toLocalDate().atStartOfDay(now.zone)
        return if (end > start) {
            if (today in schedule.days && minute in start until end) {
                midnight.plusMinutes(end.toLong()).toInstant().toEpochMilli()
            } else null
        } else {
            // Overnight (or 24h when start == end).
            when {
                today in schedule.days && minute >= start ->
                    midnight.plusDays(1).plusMinutes(end.toLong()).toInstant().toEpochMilli()
                yesterday in schedule.days && minute < end ->
                    midnight.plusMinutes(end.toLong()).toInstant().toEpochMilli()
                else -> null
            }
        }
    }
}

/** Inputs the policy needs besides settings; all time-dependent values are passed in. */
data class PolicyContext(
    val nowMs: Long,
    val zone: ZoneId,
    /** Today's use of the app being decided on (all its packages). */
    val usageTodayMs: Long,
    val reelViewerIntentional: Boolean,
    val reelViewerSwiped: Boolean,
    /** The feed has been scrolled to or past this visit's "caught up" marker (e.g. via "View older posts"). */
    val feedPastEnd: Boolean = false,
    /** Quiet hours and focus sessions close this app (it is one of the chosen ones). */
    val quietCloses: Boolean = true,
)

class PolicyEngine {

    fun effective(settings: AppSettings, runtime: RuntimeState, nowMs: Long, zone: ZoneId): EffectivePolicy {
        val session = runtime.session
        if (session != null && session.endsAtMs > nowMs) {
            // A focus session closes the chosen apps until it ends. Only strict mode locks it.
            return EffectivePolicy(
                settings.rules, EffectivePolicy.Source.FOCUS_SESSION,
                locked = settings.strict.focusSessions, blockEntirely = true, until = session.endsAtMs,
            )
        }
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone)
        val until = ScheduleMath.activeUntil(settings.schedules, now)
        if (until != null) {
            // Quiet hours close the same apps; overlapping windows end at the latest one. Only strict mode locks them.
            return EffectivePolicy(
                settings.rules, EffectivePolicy.Source.SCHEDULE,
                locked = settings.strict.quietHours, blockEntirely = true, until = until,
            )
        }
        return EffectivePolicy(
            settings.rules, EffectivePolicy.Source.NORMAL,
            locked = false, blockEntirely = false, until = null,
        )
    }

    /** The Instagram app itself, section by section. */
    fun decide(c: Classification, settings: AppSettings, runtime: RuntimeState, ctx: PolicyContext): Decision {
        if (runtime.emergencyActive(ctx.nowMs)) return Decision.Allow
        val policy = effective(settings, runtime, ctx.nowMs, ctx.zone)

        // 1-2. Focus session, quiet hours, or Instagram's own daily limit: the whole app is closed.
        closure(settings.limitFor(WatchedApp.INSTAGRAM), policy, ctx)?.let { return it }

        // 3. Section rules.
        val rules = policy.rules
        return when (c.surface) {
            Surface.REELS_TAB ->
                if (rules.blockReels) Decision.Block(BlockReason.REELS, Redirect.GO_PROFILE) else Decision.Allow
            Surface.REELS_VIEWER -> when {
                !rules.blockReels -> Decision.Allow
                // A Reel sent by a friend (or opened from a profile or post) always plays...
                ctx.reelViewerIntentional && !ctx.reelViewerSwiped -> Decision.Allow
                // ...and swiping away from it brings the user straight back to it.
                ctx.reelViewerIntentional -> Decision.Block(BlockReason.REELS, Redirect.SNAP_BACK)
                else -> Decision.Block(BlockReason.REELS, Redirect.GO_PROFILE)
            }
            Surface.EXPLORE ->
                if (rules.blockExplore) Decision.Block(BlockReason.EXPLORE, Redirect.FOCUS_SEARCH) else Decision.Allow
            Surface.HOME_FEED -> when {
                rules.blockHomeFeed -> Decision.Block(BlockReason.HOME_FEED, Redirect.GO_PROFILE)
                c.feedEndVisible || ctx.feedPastEnd -> Decision.Block(BlockReason.FEED_END, Redirect.FEED_TOP)
                else -> Decision.Allow
            }
            else -> Decision.Allow
        }
    }

    /**
     * Every other watched app, and Instagram Lite (which draws its own screens): closed during focus
     * sessions, quiet hours and after the app's own daily limit. YouTube, Facebook and Snapchat also
     * have their short-video section closed; [shortVideo] = that section is on screen.
     */
    fun decideApp(
        app: WatchedApp,
        shortVideo: Boolean,
        settings: AppSettings,
        runtime: RuntimeState,
        ctx: PolicyContext,
    ): Decision {
        if (runtime.emergencyActive(ctx.nowMs)) return Decision.Allow
        val policy = effective(settings, runtime, ctx.nowMs, ctx.zone)
        closure(settings.limitFor(app), policy, ctx)?.let { return it }
        return if (shortVideo && app != WatchedApp.INSTAGRAM && policy.rules.blocksShortVideo(app)) {
            Decision.Block(BlockReason.SHORT_VIDEO, Redirect.SAFE_TAB)
        } else {
            Decision.Allow
        }
    }

    /**
     * Any other app with a launcher icon (the essentials never get here): closed during focus
     * sessions and quiet hours if it is one of the chosen apps, and after its own daily limit of [limitMinutes].
     */
    fun decidePackage(limitMinutes: Int, settings: AppSettings, runtime: RuntimeState, ctx: PolicyContext): Decision =
        if (runtime.emergencyActive(ctx.nowMs)) Decision.Allow
        else closure(limitMinutes, effective(settings, runtime, ctx.nowMs, ctx.zone), ctx) ?: Decision.Allow

    /** The whole app closed: a focus session or quiet hours (if chosen), or its own daily limit. */
    private fun closure(limitMinutes: Int, policy: EffectivePolicy, ctx: PolicyContext): Decision.Block? =
        when {
            policy.blockEntirely && ctx.quietCloses -> Decision.Block(entireReason(policy), Redirect.LEAVE_APP, policy.until)
            limitReached(limitMinutes, ctx.usageTodayMs) ->
                Decision.Block(BlockReason.DAILY_LIMIT, Redirect.LEAVE_APP, endOfDay(ctx))
            else -> null
        }

    fun limitReached(limitMinutes: Int, usageTodayMs: Long): Boolean =
        limitMinutes > 0 && usageTodayMs >= limitMinutes * 60_000L

    private fun entireReason(policy: EffectivePolicy) = when (policy.source) {
        EffectivePolicy.Source.FOCUS_SESSION -> BlockReason.FOCUS_SESSION
        else -> BlockReason.SCHEDULE
    }

    private fun endOfDay(ctx: PolicyContext): Long =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(ctx.nowMs), ctx.zone)
            .toLocalDate().plusDays(1).atStartOfDay(ctx.zone).toInstant().toEpochMilli()
}
