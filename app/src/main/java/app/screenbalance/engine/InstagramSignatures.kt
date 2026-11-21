package app.screenbalance.engine

/**
 * Everything Glance knows about Instagram's UI lives here, so an Instagram update
 * means editing one table instead of hunting through logic.
 *
 * View IDs are short names (the part after "com.instagram.android:id/"). Exact matches only:
 * substring matching is deliberately avoided because Stories use ids containing "reel".
 *
 * Sources: community-verified accessibility dumps published by open-source blockers
 * (Scrollhalt — verified on Instagram 451.0.0.53.80, ReelGuard, NoReel, Unreel) and
 * uiautomator dumps. See docs/DETECTION.md.
 */
object InstagramSignatures {
    const val PACKAGE = "com.instagram.android"
    const val ID_PREFIX = "$PACKAGE:id/"

    // Bottom navigation tabs.
    const val TAB_HOME = "feed_tab"
    const val TAB_REELS = "clips_tab"
    const val TAB_SEARCH = "search_tab"
    const val TAB_DIRECT = "direct_tab"
    const val TAB_PROFILE = "profile_tab"
    val TAB_IDS = mapOf(
        TAB_HOME to Tab.HOME,
        TAB_REELS to Tab.REELS,
        TAB_SEARCH to Tab.SEARCH,
        TAB_DIRECT to Tab.DIRECT,
        TAB_PROFILE to Tab.PROFILE,
    )

    /** Full-screen Reels player. Must also cover most of the screen (see [VIEWER_MIN_*]). */
    val REELS_VIEWER = setOf(
        "clips_viewer_view_pager",
        "clips_view_pager",
        "clips_viewer_fragment_container",
        "clips_viewer_container",
    )

    /** Pagers whose page changes mean "swiped to the next Reel". */
    val REELS_PAGERS = setOf("clips_viewer_view_pager", "clips_view_pager")

    const val VIEWER_MIN_WIDTH_FRACTION = 0.80f
    const val VIEWER_MIN_HEIGHT_FRACTION = 0.55f

    /** Markers shown on a Reel that was sent to the user in a DM. */
    val SHARED_REEL_MARKERS = setOf(
        "clips_sent_by",
        "reel_viewer_message_composer",
        "reply_bar_container_scroll_view",
        "sender_username_or_fullname",
    )

    /** Direct messages: inbox and threads. */
    val DIRECT_MARKERS = setOf(
        "direct_thread_header",
        "direct_thread_toolbar",
        "row_thread_composer_edittext",
        "row_thread_composer_container",
        "message_composer_reply_bar_container",
        "thread_message_list",
        "direct_inbox_recycler_view",
    )

    /** Someone's profile page. */
    val PROFILE_MARKERS = setOf(
        "profile_viewpager",
        "profile_tab_layout",
        "row_profile_header_imageview_frame_layout",
    )

    /** Search box and results: intentional search by name stays allowed. */
    val SEARCH_INPUT = setOf("action_bar_search_edit_text", "search_edit_text", "action_bar_search_box")

    /**
     * Labels of the idle search bar at the top of the Search/Explore tab, matched exactly
     * (lower-cased). Search and Explore share one screen in current Instagram versions.
     */
    val SEARCH_BAR_LABELS = setOf("search", "search with meta ai", "ask meta ai or search", "buscar", "pesquisar", "suchen", "rechercher")
    val SEARCH_RESULT_MARKERS = setOf("row_search_user_container", "row_search_keyword_title")

    /** Explore grid cells; the grid is shown when the Search tab is open with no query. */
    const val EXPLORE_GRID_CELL = "image_preview"
    const val EXPLORE_MIN_CELLS = 3

    /**
     * Visible text markers (case-insensitive, apostrophes normalised). Only used in memory,
     * never stored. English first; other languages are best effort.
     */
    val FEED_END_TEXT = listOf(
        "you're all caught up",
        "you've completely caught up",
        "estás al día",
        "você está em dia",
        "du bist auf dem neuesten stand",
        "vous êtes à jour",
    )
    val DISCOVER_PEOPLE_TEXT = listOf("discover people", "descubrir personas", "descobrir pessoas")
}

enum class Tab { HOME, REELS, SEARCH, DIRECT, PROFILE }
