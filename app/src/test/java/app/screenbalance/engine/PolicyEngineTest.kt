package app.screenbalance.engine

import app.screenbalance.model.AppSettings
import app.screenbalance.model.FocusSession
import app.screenbalance.model.OpenPause
import app.screenbalance.model.RuntimeState
import app.screenbalance.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PolicyEngineTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val engine = PolicyEngine()
    // Wednesday 2026-11-18 10:30.
    private val now = ZonedDateTime.of(2026, 11, 18, 10, 30, 0, 0, zone).toInstant().toEpochMilli()

    private fun ctx(
        usage: Long = 0, intentional: Boolean = false, swiped: Boolean = false,
    ) = PolicyContext(now, zone, usage, intentional, swiped)

    private fun c(surface: Surface, end: Boolean = false) = Classification(surface, null, feedEndVisible = end)

    private val balanced = AppSettings()

    @Test fun messagesAndProfilesAlwaysAllowed() {
        for (s in listOf(Surface.DIRECT, Surface.PROFILE, Surface.SEARCH, Surface.OTHER)) {
            assertEquals(Decision.Allow, engine.decide(c(s), balanced, RuntimeState(), ctx()))
        }
    }

    @Test fun reelsTabGoesToProfile() {
        val d = engine.decide(c(Surface.REELS_TAB), balanced, RuntimeState(), ctx()) as Decision.Block
        assertEquals(BlockReason.REELS, d.reason)
        assertEquals(Redirect.GO_PROFILE, d.redirect)
    }

    @Test fun sentReelPlaysAndSwipingAwaySnapsBack() {
        assertEquals(Decision.Allow, engine.decide(c(Surface.REELS_VIEWER), balanced, RuntimeState(), ctx(intentional = true)))
        val d = engine.decide(c(Surface.REELS_VIEWER), balanced, RuntimeState(), ctx(intentional = true, swiped = true))
        assertEquals(Redirect.SNAP_BACK, (d as Decision.Block).redirect)
    }

    @Test fun reelFromSearchGoesToProfile() {
        val d = engine.decide(c(Surface.REELS_VIEWER), balanced, RuntimeState(), ctx(intentional = false)) as Decision.Block
        assertEquals(Redirect.GO_PROFILE, d.redirect)
    }

    @Test fun reelsAllowedWhenSwitchedOff() {
        val s = balanced.copy(rules = balanced.rules.copy(blockReels = false))
        assertEquals(Decision.Allow, engine.decide(c(Surface.REELS_TAB), s, RuntimeState(), ctx()))
        assertEquals(Decision.Allow, engine.decide(c(Surface.REELS_VIEWER), s, RuntimeState(), ctx()))
    }

    @Test fun exploreRedirectsToSearch() {
        val d = engine.decide(c(Surface.EXPLORE), balanced, RuntimeState(), ctx()) as Decision.Block
        assertEquals(Redirect.FOCUS_SEARCH, d.redirect)
    }

    @Test fun homeFeedOpenUntilCaughtUp() {
        assertEquals(Decision.Allow, engine.decide(c(Surface.HOME_FEED), balanced, RuntimeState(), ctx()))
        val d = engine.decide(c(Surface.HOME_FEED, end = true), balanced, RuntimeState(), ctx()) as Decision.Block
        assertEquals(BlockReason.FEED_END, d.reason)
        assertEquals(Redirect.FEED_TOP, d.redirect) // Back to the top, where stories are.
    }

    @Test fun pastCaughtUpViaOlderPostsAlsoStops() {
        val d = engine.decide(c(Surface.HOME_FEED), balanced, RuntimeState(), ctx().copy(feedPastEnd = true)) as Decision.Block
        assertEquals(BlockReason.FEED_END, d.reason)
    }

    @Test fun blockedHomeFeedOpensProfile() {
        val s = balanced.copy(rules = balanced.rules.copy(blockHomeFeed = true))
        val d = engine.decide(c(Surface.HOME_FEED), s, RuntimeState(), ctx()) as Decision.Block
        assertEquals(BlockReason.HOME_FEED, d.reason)
        assertEquals(Redirect.GO_PROFILE, d.redirect)
        assertEquals(Decision.Allow, engine.decide(c(Surface.DIRECT), s, RuntimeState(), ctx()))
    }

    @Test fun dailyLimitClosesInstagram() {
        val s = balanced.withLimit(WatchedApp.INSTAGRAM, 30)
        assertEquals(Decision.Allow, engine.decide(c(Surface.DIRECT), s, RuntimeState(), ctx(usage = 29 * 60_000L)))
        for (surface in listOf(Surface.DIRECT, Surface.HOME_FEED, Surface.PROFILE)) {
            val d = engine.decide(c(surface), s, RuntimeState(), ctx(usage = 31 * 60_000L)) as Decision.Block
            assertEquals(BlockReason.DAILY_LIMIT, d.reason)
            assertEquals(Redirect.LEAVE_APP, d.redirect)
        }
    }

    @Test fun suggestedAccountsPageIsAllowed() {
        assertEquals(Decision.Allow, engine.decide(c(Surface.SUGGESTED_ACCOUNTS), balanced, RuntimeState(), ctx()))
    }

    @Test fun focusSessionLeavesSettingsChangeable() {
        val rt = RuntimeState(session = FocusSession(now - 1000, now + 30 * 60_000))
        val p = engine.effective(balanced, rt, now, zone)
        assertFalse(p.locked)
        assertEquals(EffectivePolicy.Source.FOCUS_SESSION, p.source)
    }

    @Test fun strictModeLocksOnlyWhatWasChosen() {
        val rt = RuntimeState(session = FocusSession(now - 1000, now + 30 * 60_000))
        val strictFocus = balanced.copy(strict = app.screenbalance.model.StrictMode(focusSessions = true))
        assertTrue(engine.effective(strictFocus, rt, now, zone).locked)
        val sched = Schedule("w", "Work", days = setOf(3), startMinute = 9 * 60, endMinute = 17 * 60)
        val quiet = balanced.copy(schedules = listOf(sched))
        assertFalse(engine.effective(quiet.copy(strict = strictFocus.strict), RuntimeState(), now, zone).locked)
        assertTrue(engine.effective(quiet.copy(strict = app.screenbalance.model.StrictMode(quietHours = true)), RuntimeState(), now, zone).locked)
    }

    @Test fun switchingStrictModeOffLoosens() {
        val on = balanced.copy(strict = app.screenbalance.model.StrictMode(focusSessions = true))
        assertTrue(on.loosens(balanced))
        assertFalse(balanced.loosens(on))
        assertFalse(balanced.loosens(balanced.copy(dailyGoalMinutes = 600)))
    }

    @Test fun focusSessionClosesInstagramCompletely() {
        val rt = RuntimeState(session = FocusSession(now - 1000, now + 30 * 60_000))
        val d = engine.decide(c(Surface.DIRECT), balanced, rt, ctx()) as Decision.Block
        assertEquals(BlockReason.FOCUS_SESSION, d.reason)
        assertEquals(Redirect.LEAVE_APP, d.redirect)
    }

    @Test fun focusSessionClosesOnlyTheChosenApps() {
        val rt = RuntimeState(session = FocusSession(now - 1000, now + 30 * 60_000))
        val d = engine.decidePackage(0, balanced, rt, ctx()) as Decision.Block
        assertEquals(BlockReason.FOCUS_SESSION, d.reason)
        assertEquals(Redirect.LEAVE_APP, d.redirect)
        assertEquals(Decision.Allow, engine.decidePackage(0, balanced, rt, ctx().copy(quietCloses = false)))
        assertEquals(Decision.Allow, engine.decidePackage(0, balanced, RuntimeState(), ctx()))
    }

    @Test fun unchosenAppStillHonoursItsOwnLimit() {
        val sched = Schedule("w", "Work", days = setOf(3), startMinute = 9 * 60, endMinute = 17 * 60)
        val s = balanced.copy(schedules = listOf(sched))
        val open = ctx(usage = 31 * 60_000L).copy(quietCloses = false)
        assertEquals(Decision.Allow, engine.decidePackage(0, s, RuntimeState(), open))
        val d = engine.decidePackage(30, s, RuntimeState(), open) as Decision.Block
        assertEquals(BlockReason.DAILY_LIMIT, d.reason)
    }

    @Test fun anyAppHasItsOwnLimit() {
        assertEquals(Decision.Allow, engine.decidePackage(30, balanced, RuntimeState(), ctx(usage = 29 * 60_000L)))
        val d = engine.decidePackage(30, balanced, RuntimeState(), ctx(usage = 30 * 60_000L)) as Decision.Block
        assertEquals(BlockReason.DAILY_LIMIT, d.reason)
        assertEquals(Redirect.LEAVE_APP, d.redirect)
        assertEquals(Decision.Allow, engine.decidePackage(0, balanced, RuntimeState(), ctx(usage = 999 * 60_000L)))
    }

    @Test fun watchedAppLimitsAreSeparate() {
        val s = balanced.withLimit(WatchedApp.YOUTUBE, 30)
        assertTrue(engine.decideApp(WatchedApp.YOUTUBE, false, s, RuntimeState(), ctx(usage = 31 * 60_000L)) is Decision.Block)
        assertEquals(Decision.Allow, engine.decideApp(WatchedApp.TIKTOK, false, s, RuntimeState(), ctx(usage = 31 * 60_000L)))
    }

    @Test fun shortVideoMovesToASafeTab() {
        for (a in listOf(WatchedApp.YOUTUBE, WatchedApp.FACEBOOK, WatchedApp.SNAPCHAT)) {
            val d = engine.decideApp(a, true, balanced, RuntimeState(), ctx()) as Decision.Block
            assertEquals(BlockReason.SHORT_VIDEO, d.reason)
            assertEquals(Redirect.SAFE_TAB, d.redirect)
            assertEquals(Decision.Allow, engine.decideApp(a, false, balanced, RuntimeState(), ctx()))
        }
        val off = balanced.copy(rules = balanced.rules.copy(blockShorts = false))
        assertEquals(Decision.Allow, engine.decideApp(WatchedApp.YOUTUBE, true, off, RuntimeState(), ctx()))
        // Whole-app-only apps have no section to close.
        assertEquals(Decision.Allow, engine.decideApp(WatchedApp.TIKTOK, true, balanced, RuntimeState(), ctx()))
    }

    @Test fun earlierSingleLimitStillApplies() {
        val old = AppSettings(legacyInstagramLimit = 45)
        assertEquals(45, old.limitFor(WatchedApp.INSTAGRAM))
        assertEquals(45, old.limitForPackage("com.instagram.lite"))
        assertEquals(0, old.limitFor(WatchedApp.YOUTUBE))
        val set = old.withLimit(WatchedApp.INSTAGRAM, 20)
        assertEquals(20, set.limitFor(WatchedApp.INSTAGRAM))
        assertEquals(0, set.legacyInstagramLimit)
        assertEquals(15, AppSettings(appLimits = mapOf("com.example.game" to 15)).limitForPackage("com.example.game"))
    }

    @Test fun instagramLiteFollowsTheLimits() {
        val lite = { st: AppSettings, rt: RuntimeState, u: Long -> engine.decideApp(WatchedApp.INSTAGRAM, false, st, rt, ctx(usage = u)) }
        assertEquals(Decision.Allow, lite(balanced, RuntimeState(), 0))
        assertTrue(lite(balanced, RuntimeState(session = FocusSession(now - 1, now + 60_000)), 0) is Decision.Block)
        assertTrue(lite(balanced.withLimit(WatchedApp.INSTAGRAM, 10), RuntimeState(), 11 * 60_000L) is Decision.Block)
    }

    @Test fun oneLimitSetterForEveryApp() {
        val s = balanced.withPackageLimit("com.example.game", 20).withPackageLimit("com.instagram.lite", 40)
        assertEquals(20, s.limitForPackage("com.example.game"))
        assertEquals(40, s.limitFor(WatchedApp.INSTAGRAM)) // Lite and Instagram share one limit.
        assertEquals(40, s.limitForPackage(InstagramSignatures.PACKAGE))
        assertTrue("com.example.game" !in s.withPackageLimit("com.example.game", 0).appLimits)
    }

    @Test fun quietHoursCloseTheSocialAppsByDefault() {
        val q = AppSettings().quietApps
        assertTrue(q.closes(InstagramSignatures.PACKAGE))
        assertTrue(!q.closes("com.whatsapp"))
        assertTrue(!q.reachesBeyondWatched)
        val withGame = q.withApp("com.example.game", true)
        assertTrue(withGame.closes("com.example.game") && withGame.reachesBeyondWatched)
        val all = q.copy(allApps = true).withApp("com.whatsapp", false)
        assertTrue(all.closes("com.example.game"))
        assertTrue(!all.closes("com.whatsapp"))
    }

    @Test fun tighteningIsNeverLoosening() {
        val base = balanced.withPackageLimit("com.example.game", 30)
        assertTrue(!base.loosens(base.withPackageLimit("com.example.game", 20)))
        assertTrue(!base.loosens(base.withPackageLimit("com.example.other", 20)))
        assertTrue(!base.loosens(base.copy(quietApps = base.quietApps.withApp("com.example.game", true))))
        assertTrue(!base.loosens(base.copy(openPause = base.openPause.copy(enabled = true))))
        assertTrue(!base.loosens(base.copy(reminderEveryMinutes = 10)))
        val night = Schedule("n", "Night", days = (1..7).toSet(), startMinute = 22 * 60, endMinute = 7 * 60)
        assertTrue(!base.loosens(base.copy(schedules = listOf(night))))
        assertTrue(!base.copy(schedules = listOf(night)).loosens(base.copy(schedules = listOf(night.copy(name = "Sleep")))))
    }

    @Test fun looseningIsCaught() {
        val night = Schedule("n", "Night", days = (1..7).toSet(), startMinute = 22 * 60, endMinute = 7 * 60)
        val base = balanced.withPackageLimit("com.example.game", 30).copy(
            schedules = listOf(night), reminderEveryMinutes = 10, openPause = OpenPause(enabled = true, apps = setOf("a")),
        )
        assertTrue(base.loosens(base.withPackageLimit("com.example.game", 60)))
        assertTrue(base.loosens(base.withPackageLimit("com.example.game", 0)))
        assertTrue(base.loosens(base.copy(rules = base.rules.copy(blockReels = false))))
        assertTrue(base.loosens(base.copy(schedules = emptyList())))
        assertTrue(base.loosens(base.copy(schedules = listOf(night.copy(endMinute = 6 * 60)))))
        assertTrue(base.loosens(base.copy(quietApps = base.quietApps.withApp(InstagramSignatures.PACKAGE, false))))
        assertTrue(base.loosens(base.copy(openPause = base.openPause.copy(enabled = false))))
        assertTrue(base.loosens(base.copy(reminderEveryMinutes = 0)))
        assertTrue(base.loosens(base.copy(reminderEveryMinutes = 15)))
    }

    @Test fun pauseChosenOrAllApps() {
        val chosen = OpenPause(enabled = true).withApp("a", true)
        assertTrue(chosen.appliesTo("a"))
        assertTrue(!chosen.appliesTo("b"))
        val all = OpenPause(enabled = true, allApps = true).withApp("b", false)
        assertTrue(all.appliesTo("a"))
        assertTrue(!all.appliesTo("b"))
        assertTrue(!all.copy(enabled = false).appliesTo("a"))
    }

    @Test fun expiredSessionIgnored() {
        val rt = RuntimeState(session = FocusSession(now - 10_000, now - 1))
        assertEquals(EffectivePolicy.Source.NORMAL, engine.effective(balanced, rt, now, zone).source)
    }

    @Test fun protectedHoursCloseInstagramCompletely() {
        val sched = Schedule("w", "Work", days = setOf(3), startMinute = 9 * 60, endMinute = 17 * 60)
        val s = balanced.copy(schedules = listOf(sched))
        for (surface in listOf(Surface.DIRECT, Surface.PROFILE, Surface.HOME_FEED)) {
            val d = engine.decide(c(surface), s, RuntimeState(), ctx()) as Decision.Block
            assertEquals(BlockReason.SCHEDULE, d.reason)
            assertEquals(Redirect.LEAVE_APP, d.redirect)
        }
        // Quiet hours close apps but leave every setting changeable.
        assertFalse(engine.effective(s, RuntimeState(), now, zone).locked)
    }

    @Test fun overnightProtectedHoursAndNextStart() {
        val night = Schedule("n", "Evening", days = (1..7).toSet(), startMinute = 18 * 60, endMinute = 8 * 60)
        val at = { h: Int, m: Int -> ZonedDateTime.of(2026, 11, 18, h, m, 0, 0, zone) }
        // 17:59 -> not active, next start today 18:00.
        assertNull(ScheduleMath.activeUntil(listOf(night), at(17, 59)))
        assertEquals(at(18, 0).toInstant().toEpochMilli(), ScheduleMath.nextStart(listOf(night), at(17, 59)))
        // 18:00 -> active until tomorrow 08:00.
        assertEquals(at(8, 0).plusDays(1).toInstant().toEpochMilli(), ScheduleMath.activeUntil(listOf(night), at(18, 0)))
        // 07:59 -> still active, ends 08:00 today.
        assertEquals(at(8, 0).toInstant().toEpochMilli(), ScheduleMath.activeUntil(listOf(night), at(7, 59)))
        assertNull(ScheduleMath.activeUntil(listOf(night), at(8, 0)))
    }

    @Test fun scheduleWindowMath() {
        val wed = ZonedDateTime.of(2026, 11, 18, 23, 30, 0, 0, zone)
        val thuEarly = ZonedDateTime.of(2026, 11, 19, 6, 0, 0, 0, zone)
        val night = Schedule("n", "Night", days = setOf(3), startMinute = 22 * 60, endMinute = 7 * 60)
        assertNotNull(ScheduleMath.activeUntil(night, wed))
        assertNotNull("overnight window continues after midnight", ScheduleMath.activeUntil(night, thuEarly))
        assertNull(ScheduleMath.activeUntil(night, ZonedDateTime.of(2026, 11, 19, 8, 0, 0, 0, zone)))
        assertNull(ScheduleMath.activeUntil(night.copy(enabled = false), wed))
        val work = Schedule("w", "Work", days = setOf(4), startMinute = 9 * 60, endMinute = 17 * 60)
        assertNull("not on Wednesday", ScheduleMath.activeUntil(work, ZonedDateTime.of(2026, 11, 18, 10, 0, 0, 0, zone)))
    }
}
