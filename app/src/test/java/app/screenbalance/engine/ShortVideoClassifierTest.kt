package app.screenbalance.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortVideoClassifierTest {
    private val w = 1080
    private val h = 2400
    private val classifier = ShortVideoClassifier()

    private fun snap(views: List<ViewFact> = emptyList(), selected: List<String> = emptyList()) =
        Snapshot(w, h, views, emptyList(), selected)

    @Test fun fullScreenPlayerIsShortVideo() {
        assertTrue(classifier.isShortVideo(WatchedApp.YOUTUBE, snap(listOf(ViewFact("reel_watch_player", Box(0, 0, w, h))))))
        assertTrue(classifier.isShortVideo(WatchedApp.FACEBOOK, snap(listOf(ViewFact("fb_shorts_viewer_page", Box(0, 100, w, h - 100))))))
        assertTrue(classifier.isShortVideo(WatchedApp.SNAPCHAT, snap(listOf(ViewFact("spotlight_player", Box(0, 0, w, h))))))
    }

    @Test fun smallShelfIsNot() {
        // A strip of Shorts inside YouTube's home feed is not the Shorts player.
        assertFalse(classifier.isShortVideo(WatchedApp.YOUTUBE, snap(listOf(ViewFact("reel_watch_player", Box(0, 600, w, 1200))))))
    }

    @Test fun selectedTabCounts() {
        assertTrue(classifier.isShortVideo(WatchedApp.YOUTUBE, snap(selected = listOf("shorts"))))
        assertFalse(classifier.isShortVideo(WatchedApp.YOUTUBE, snap(selected = listOf("home"))))
    }

    @Test fun otherAppsIdsDontCross() {
        assertFalse(classifier.isShortVideo(WatchedApp.YOUTUBE, snap(listOf(ViewFact("spotlight_player", Box(0, 0, w, h))))))
        assertFalse(classifier.isShortVideo(WatchedApp.TIKTOK, snap(listOf(ViewFact("reel_watch_player", Box(0, 0, w, h))))))
    }
}
