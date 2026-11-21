package app.screenbalance.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenClassifierTest {
    private val w = 1080
    private val h = 2400
    private val classifier = ScreenClassifier()
    private val full = Box(0, 0, w, h)
    private val small = Box(0, 0, 200, 200)

    private fun tabs(selected: String) = InstagramSignatures.TAB_IDS.keys.map {
        ViewFact(it, Box(0, h - 150, 200, h), selected = it == selected)
    }

    private fun snap(views: List<ViewFact>, texts: List<String> = emptyList()) =
        Snapshot(w, h, views, texts.map { Snapshot.normalizeText(it) })

    @Test fun reelsTabWithViewerIsReelsTab() {
        val s = snap(tabs("clips_tab") + ViewFact("clips_viewer_view_pager", full))
        assertEquals(Surface.REELS_TAB, classifier.classify(s).surface)
    }

    @Test fun viewerWithoutTabBarIsViewer() {
        val s = snap(listOf(ViewFact("clips_viewer_view_pager", full)))
        assertEquals(Surface.REELS_VIEWER, classifier.classify(s).surface)
    }

    @Test fun smallViewerIdIsNotAReel() {
        // e.g. an inline preview or a Stories element: must not be classified as the player.
        val s = snap(tabs("feed_tab") + ViewFact("clips_viewer_view_pager", small))
        assertEquals(Surface.HOME_FEED, classifier.classify(s).surface)
    }

    @Test fun storiesTrayIdsAreIgnored() {
        val s = snap(tabs("feed_tab") + ViewFact("reels_tray_container", full))
        assertEquals(Surface.HOME_FEED, classifier.classify(s).surface)
    }

    @Test fun sharedReelMarkerDetected() {
        val s = snap(listOf(ViewFact("clips_viewer_view_pager", full), ViewFact("clips_sent_by", small)))
        val c = classifier.classify(s)
        assertEquals(Surface.REELS_VIEWER, c.surface)
        assertTrue(c.sharedReelMarker)
    }

    @Test fun directThreadIsMessages() {
        val s = snap(listOf(ViewFact("direct_thread_header", small), ViewFact("row_thread_composer_edittext", small)))
        assertEquals(Surface.DIRECT, classifier.classify(s).surface)
    }

    @Test fun directTabIsMessages() {
        assertEquals(Surface.DIRECT, classifier.classify(snap(tabs("direct_tab"))).surface)
    }

    @Test fun profileOpenedFromHomeIsProfileNotFeed() {
        val s = snap(tabs("feed_tab") + ViewFact("profile_viewpager", full), texts = listOf("Suggested for you"))
        assertEquals(Surface.PROFILE, classifier.classify(s).surface)
    }

    @Test fun exploreGridDetected() {
        val cells = (0 until 6).map { ViewFact("image_preview", small) }
        val s = snap(tabs("search_tab") + ViewFact("action_bar_search_edit_text", small) + cells)
        assertEquals(Surface.EXPLORE, classifier.classify(s).surface)
    }

    @Test fun focusedSearchIsAllowedSearch() {
        val cells = (0 until 6).map { ViewFact("image_preview", small) }
        val s = snap(tabs("search_tab") + ViewFact("action_bar_search_edit_text", small, focused = true) + cells)
        assertEquals(Surface.SEARCH, classifier.classify(s).surface)
    }

    @Test fun idleSearchBarMeansExploreEvenWithoutGridIds() {
        // Current Instagram: search and Explore share one screen; grid cell ids may be absent.
        val s = snap(tabs("search_tab"), texts = listOf("Search with Meta AI"))
        assertEquals(Surface.EXPLORE, classifier.classify(s).surface)
    }

    @Test fun postOpenedFromSearchIsNotExplore() {
        // No search bar, no grid: a post or page reached from search stays allowed.
        val s = snap(tabs("search_tab"), texts = listOf("Like", "Comment"))
        assertEquals(Surface.SEARCH, classifier.classify(s).surface)
    }

    @Test fun searchResultsAreAllowedSearch() {
        val s = snap(tabs("search_tab") + ViewFact("row_search_user_container", small))
        assertEquals(Surface.SEARCH, classifier.classify(s).surface)
    }

    @Test fun feedEndDetectedWithCurlyApostrophe() {
        val c = classifier.classify(snap(tabs("feed_tab"), texts = listOf("You’re all caught up")))
        assertEquals(Surface.HOME_FEED, c.surface)
        assertTrue(c.feedEndVisible)
    }

    @Test fun discoverPeopleIsSuggestedAccounts() {
        val c = classifier.classify(snap(emptyList(), texts = listOf("Discover people")))
        assertEquals(Surface.SUGGESTED_ACCOUNTS, c.surface)
    }

    @Test fun unknownScreenFailsOpen() {
        assertEquals(Surface.OTHER, classifier.classify(snap(emptyList())).surface)
    }
}
