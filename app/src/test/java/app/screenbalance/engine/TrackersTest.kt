package app.screenbalance.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackersTest {

    @Test fun reelFromDmIsIntentionalUntilSwipe() {
        val t = ReelViewerTracker(positioningWindowMs = 600)
        t.onSurface(Surface.REELS_VIEWER, Surface.DIRECT, now = 0)
        assertTrue(t.openedIntentionally)
        t.onPagerScrolled(3, 3, now = 100) // positioning
        assertFalse(t.swiped)
        t.onPagerScrolled(3, 3, now = 2000)
        assertFalse(t.swiped)
        t.onPagerScrolled(4, 4, now = 3000)
        assertTrue(t.swiped)
    }

    @Test fun snappingBackToTheSentReelRearmsTheSwipe() {
        val t = ReelViewerTracker(positioningWindowMs = 600)
        t.onSurface(Surface.REELS_VIEWER, Surface.DIRECT, now = 0)
        t.onPagerScrolled(3, 3, now = 100)
        t.onPagerScrolled(4, 4, now = 2000)
        assertTrue(t.swiped)
        assertEquals(3, t.startIndex)
        assertEquals(4, t.currentIndex)
        t.onPagerScrolled(3, 3, now = 2400) // scrolled back by Glance
        t.backAtStart()
        assertFalse(t.swiped)
        assertTrue(t.inViewer)
        assertTrue(t.openedIntentionally)
        t.onPagerScrolled(4, 4, now = 5000)
        assertTrue(t.swiped)
    }

    @Test fun reelFromReelsTabIsNotIntentional() {
        val t = ReelViewerTracker()
        t.onSurface(Surface.REELS_VIEWER, Surface.REELS_TAB, now = 0)
        assertFalse(t.openedIntentionally)
        t.reset()
        t.onSurface(Surface.REELS_VIEWER, Surface.EXPLORE, now = 0)
        assertFalse(t.openedIntentionally)
        t.reset()
        t.onSurface(Surface.REELS_VIEWER, Surface.SEARCH, now = 0)
        assertFalse("Reels from search results are discovery too", t.openedIntentionally)
    }

    @Test fun unknownIndexScrollCountsAsSwipe() {
        val t = ReelViewerTracker(positioningWindowMs = 600)
        t.onSurface(Surface.REELS_VIEWER, Surface.PROFILE, now = 0)
        t.onPagerScrolled(-1, -1, now = 1000)
        assertTrue(t.swiped)
    }

    @Test fun leavingViewerResets() {
        val t = ReelViewerTracker(positioningWindowMs = 0)
        t.onSurface(Surface.REELS_VIEWER, Surface.DIRECT, now = 0)
        t.onPagerScrolled(1, 1, now = 10)
        t.onPagerScrolled(2, 2, now = 20)
        assertTrue(t.swiped)
        t.onSurface(Surface.DIRECT, null, now = 30)
        assertFalse(t.inViewer)
        assertFalse(t.swiped)
    }

}
