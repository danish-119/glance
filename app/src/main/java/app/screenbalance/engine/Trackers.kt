package app.screenbalance.engine

/**
 * Tracks one visit to the full-screen Reels viewer so a single Reel opened on purpose
 * (from a DM, a profile, a post) can play while swiping into the endless feed is blocked.
 */
class ReelViewerTracker(
    /** Instagram scrolls the pager into position right after opening; ignore that. */
    private val positioningWindowMs: Long = 600L,
) {
    var inViewer = false
        private set
    /** Surface the viewer was opened from. */
    var origin: Surface? = null
        private set
    var swiped = false
        private set
    private var openedAt = 0L
    /** Pager index of the Reel that was opened, and of the one showing now (-1 = unknown). */
    var startIndex = -1
        private set
    var currentIndex = -1
        private set

    /** Call on every classification. [previous] is the last surface that was not the viewer. */
    fun onSurface(surface: Surface, previous: Surface?, now: Long) {
        if (surface == Surface.REELS_VIEWER) {
            if (!inViewer) {
                inViewer = true
                origin = previous
                swiped = false
                startIndex = -1
                currentIndex = -1
                openedAt = now
            }
        } else {
            reset()
        }
    }

    /** A scroll event from a Reels pager. Indices are -1 when the platform did not report them. */
    fun onPagerScrolled(fromIndex: Int, toIndex: Int, now: Long) {
        if (!inViewer) return
        val index = if (fromIndex >= 0) fromIndex else toIndex
        if (index >= 0) currentIndex = index
        if (now - openedAt < positioningWindowMs) {
            if (index >= 0) startIndex = index
            return
        }
        if (startIndex < 0) {
            // No index from the positioning phase: the first index we see is the start.
            if (index >= 0) { startIndex = index; return }
            swiped = true // Pager scrolled and we cannot tell where: treat as a swipe.
            return
        }
        if (index >= 0 && index != startIndex) swiped = true
    }

    /**
     * Whether the viewer was opened deliberately: from a DM, a profile, the followed feed or a post.
     * Not from the Reels tab, Explore or search results (search results are a discovery grid too).
     */
    val openedIntentionally: Boolean
        get() = origin != null && origin != Surface.REELS_TAB && origin != Surface.EXPLORE &&
            origin != Surface.SEARCH && origin != Surface.SUGGESTED_ACCOUNTS

    /** The user was put back on the Reel they opened: the next swipe away counts again. */
    fun backAtStart() {
        if (startIndex >= 0) currentIndex = startIndex
        swiped = false
    }

    fun reset() {
        inViewer = false
        origin = null
        swiped = false
        startIndex = -1
        currentIndex = -1
    }
}

