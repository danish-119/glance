package app.screenbalance.engine

import app.screenbalance.engine.InstagramSignatures as Sig

/** The Instagram surface the user is currently looking at. */
enum class Surface {
    HOME_FEED,
    REELS_TAB,
    REELS_VIEWER,
    EXPLORE,
    SEARCH,
    DIRECT,
    PROFILE,
    SUGGESTED_ACCOUNTS,
    OTHER;

    val section: Section
        get() = when (this) {
            HOME_FEED -> Section.FEED
            REELS_TAB, REELS_VIEWER -> Section.REELS
            EXPLORE, SUGGESTED_ACCOUNTS -> Section.DISCOVERY
            SEARCH -> Section.SEARCH
            DIRECT -> Section.MESSAGES
            PROFILE -> Section.PROFILES
            OTHER -> Section.OTHER
        }
}

/** Coarse groups the usage minutes are stored under (names are persisted; don't rename). */
enum class Section { FEED, REELS, DISCOVERY, SEARCH, MESSAGES, PROFILES, OTHER }

data class Classification(
    val surface: Surface,
    val selectedTab: Tab?,
    /** "You've completely caught up": the end of new posts from followed accounts. */
    val feedEndVisible: Boolean = false,
    val sharedReelMarker: Boolean = false,
)

/**
 * Pure classification of a [Snapshot]. Ordered from most to least specific; when nothing
 * matches with confidence the result is [Surface.OTHER], which is always allowed. Failing
 * open is intentional: wrongly blocking DMs or a friend's profile is worse than missing a Reel.
 */
class ScreenClassifier {

    fun classify(s: Snapshot): Classification {
        val tabBarVisible = Sig.TAB_IDS.keys.any { s.has(it) }
        val selectedTab = Sig.TAB_IDS.entries.firstOrNull { (id, _) ->
            s.views(id).any { it.selected }
        }?.value

        // 1. Full-screen Reels player.
        val viewer = Sig.REELS_VIEWER.firstOrNull { id ->
            s.views(id).any { v -> coversScreen(v.bounds, s) }
        }
        if (viewer != null) {
            val shared = s.hasAny(Sig.SHARED_REEL_MARKERS)
            val surface = if (tabBarVisible && selectedTab == Tab.REELS) Surface.REELS_TAB else Surface.REELS_VIEWER
            return Classification(surface, selectedTab, sharedReelMarker = shared)
        }

        // 2. Direct messages (inbox or a thread).
        val dm = Sig.DIRECT_MARKERS.firstOrNull { s.has(it) }
        if (dm != null || (tabBarVisible && selectedTab == Tab.DIRECT)) {
            return Classification(Surface.DIRECT, selectedTab)
        }

        // 3. A profile (own or someone else's), reachable from any tab.
        val profile = Sig.PROFILE_MARKERS.firstOrNull { s.has(it) }
        if (profile != null) {
            return Classification(Surface.PROFILE, selectedTab)
        }

        // 4. Suggested accounts page.
        if (s.hasText(Sig.DISCOVER_PEOPLE_TEXT)) {
            return Classification(Surface.SUGGESTED_ACCOUNTS, selectedTab)
        }

        // 5. Typing in search is allowed. Instagram hides the tab bar while the box is focused,
        // so this is checked without it.
        if (Sig.SEARCH_INPUT.any { id -> s.views(id).any { it.focused || it.hasUserText } }) {
            return Classification(Surface.SEARCH, selectedTab)
        }

        // 6. Search tab: search results are allowed, the browse grid is Explore.
        if (tabBarVisible && selectedTab == Tab.SEARCH) {
            val searching = Sig.SEARCH_INPUT.any { id -> s.views(id).any { it.focused || it.hasUserText } } ||
                s.hasAny(Sig.SEARCH_RESULT_MARKERS)
            if (searching) {
                return Classification(Surface.SEARCH, selectedTab)
            }
            val cells = s.count(Sig.EXPLORE_GRID_CELL)
            if (cells >= Sig.EXPLORE_MIN_CELLS) {
                return Classification(Surface.EXPLORE, selectedTab)
            }
            // Search and Explore are one screen: an idle search bar on top means the Explore feed
            // is underneath. A post opened from search has no search bar, so it stays allowed.
            if (s.hasAny(Sig.SEARCH_INPUT) || s.hasExactText(Sig.SEARCH_BAR_LABELS)) {
                return Classification(Surface.EXPLORE, selectedTab)
            }
            return Classification(Surface.SEARCH, selectedTab)
        }

        // 7. Home feed.
        if (tabBarVisible && selectedTab == Tab.HOME) {
            val end = s.hasText(Sig.FEED_END_TEXT)
            return Classification(Surface.HOME_FEED, selectedTab, feedEndVisible = end)
        }

        if (tabBarVisible && selectedTab == Tab.PROFILE) {
            return Classification(Surface.PROFILE, selectedTab)
        }
        return Classification(Surface.OTHER, selectedTab)
    }

    private fun coversScreen(b: Box, s: Snapshot): Boolean {
        if (s.screenWidth <= 0 || s.screenHeight <= 0) return false
        val c = b.clippedTo(s.screenWidth, s.screenHeight)
        return c.width >= s.screenWidth * Sig.VIEWER_MIN_WIDTH_FRACTION &&
            c.height >= s.screenHeight * Sig.VIEWER_MIN_HEIGHT_FRACTION
    }
}
