package app.screenbalance.engine

import kotlinx.serialization.Serializable

/**
 * The apps Glance looks after. Instagram is handled section by section (see [ScreenClassifier]);
 * YouTube, Facebook and Snapchat have their short-video section closed (see [ShortVideoClassifier]);
 * TikTok, X and Reddit are whole-app only: their main feed *is* the endless scroll.
 *
 * Enum names are persisted (settings, usage history): don't rename them.
 */
@Serializable
enum class WatchedApp(val label: String, val packages: Set<String>, val shortVideoLabel: String?) {
    INSTAGRAM("Instagram", setOf(InstagramSignatures.PACKAGE, "com.instagram.lite"), "Reels"),
    YOUTUBE("YouTube", setOf("com.google.android.youtube"), "Shorts"),
    FACEBOOK("Facebook", setOf("com.facebook.katana"), "Reels and Video"),
    SNAPCHAT("Snapchat", setOf("com.snapchat.android"), "Spotlight"),
    TIKTOK("TikTok", setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), null),
    X("X", setOf("com.twitter.android"), null),
    REDDIT("Reddit", setOf("com.reddit.frontpage"), null),
    ;

    companion object {
        private val byPackage: Map<String, WatchedApp> = entries.flatMap { a -> a.packages.map { it to a } }.toMap()
        val ALL_PACKAGES: Set<String> = byPackage.keys

        fun forPackage(pkg: String?): WatchedApp? = pkg?.let(byPackage::get)
    }
}

/**
 * Screen signals for the short-video sections of YouTube, Facebook and Snapchat. View ids are matched
 * exactly (the part after "<package>:id/"), and a player must also cover most of the screen, so a
 * small shelf or preview never counts. Selected-tab labels are matched exactly, lower-cased.
 *
 * Sources: public accessibility dumps in the open-source blockers ShortsBlocker and FocusBlock, plus
 * uiautomator dumps. Unconfirmed ids only ever add detections on top of the screen-coverage check;
 * when nothing matches, the screen is allowed. See docs/DETECTION.md.
 */
object ShortVideoSignatures {
    val PLAYER_IDS: Map<WatchedApp, Set<String>> = mapOf(
        WatchedApp.YOUTUBE to setOf(
            "reel_player_page_container", "reel_player_page_view", "reel_watch_player", "reel_watch_pager",
            "reel_watch_fragment_root", "reel_watch_fragment_container", "reel_video_player",
            "reel_player_underlay", "shorts_video_pager", "shorts_player",
        ),
        WatchedApp.FACEBOOK to setOf(
            "fb_shorts_viewer_fragment", "fb_shorts_viewer_page", "fb_shorts_video_container", "fb_shorts_container",
        ),
        WatchedApp.SNAPCHAT to setOf(
            "spotlight_player", "spotlight_fullscreen", "spotlight_story_player", "spotlight_swipe_layer",
            "spotlight_video", "spotlight_fragment", "spotlight_container",
        ),
    )

    /** Labels of the bottom/top tab that opens the short-video feed, when it is the selected tab. */
    val TAB_LABELS: Map<WatchedApp, Set<String>> = mapOf(
        WatchedApp.YOUTUBE to setOf("shorts", "shorts, tab"),
        WatchedApp.FACEBOOK to setOf("reels", "reels, tab", "video", "video, tab", "watch", "watch, tab"),
        WatchedApp.SNAPCHAT to setOf("spotlight", "spotlight, tab"),
    )

    /** An allowed tab to move to, by its label (tried in order); Back is the fallback. */
    val SAFE_TAB_LABELS: Map<WatchedApp, List<String>> = mapOf(
        WatchedApp.YOUTUBE to listOf("Subscriptions", "Home"),
        WatchedApp.FACEBOOK to listOf("Home", "Feed"),
        WatchedApp.SNAPCHAT to listOf("Chat", "Camera"),
    )

    const val PLAYER_MIN_WIDTH_FRACTION = 0.80f
    const val PLAYER_MIN_HEIGHT_FRACTION = 0.55f
}

/** Is a YouTube, Facebook or Snapchat screen its short-video feed? Pure, so it's unit-tested. */
class ShortVideoClassifier {
    fun isShortVideo(app: WatchedApp, s: Snapshot): Boolean {
        val ids = ShortVideoSignatures.PLAYER_IDS[app] ?: return false
        val player = ids.any { id -> s.views(id).any { v -> coversScreen(v.bounds, s) } }
        val tab = ShortVideoSignatures.TAB_LABELS[app].orEmpty().let { labels -> s.selectedLabels.any { it in labels } }
        return player || tab
    }

    private fun coversScreen(b: Box, s: Snapshot): Boolean {
        if (s.screenWidth <= 0 || s.screenHeight <= 0) return false
        val c = b.clippedTo(s.screenWidth, s.screenHeight)
        return c.width >= s.screenWidth * ShortVideoSignatures.PLAYER_MIN_WIDTH_FRACTION &&
            c.height >= s.screenHeight * ShortVideoSignatures.PLAYER_MIN_HEIGHT_FRACTION
    }
}
