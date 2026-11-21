package app.screenbalance.engine

/** Screen-space rectangle, independent of android.graphics so the engine is JVM-testable. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = (right - left).coerceAtLeast(0)
    val height get() = (bottom - top).coerceAtLeast(0)

    fun clippedTo(w: Int, h: Int) = Box(
        left.coerceIn(0, w), top.coerceIn(0, h), right.coerceIn(0, w), bottom.coerceIn(0, h),
    )
}

/** One view of the watched app that carries a resource id. */
data class ViewFact(
    val id: String,
    val bounds: Box,
    val selected: Boolean = false,
    val focused: Boolean = false,
    /** For edit texts: true when the user has typed something (not the hint). */
    val hasUserText: Boolean = false,
)

/**
 * An immutable, privacy-minimal snapshot of the visible window of a watched app.
 * Built by the accessibility service; consumed by the pure classifiers.
 *
 * [texts] holds short visible labels lower-cased for marker matching. It lives only in
 * memory for one classification pass and is never logged or persisted.
 */
class Snapshot(
    val screenWidth: Int,
    val screenHeight: Int,
    views: List<ViewFact>,
    val texts: List<String>,
    /** Labels (content description or text, normalised) of selected views, e.g. the current tab. */
    val selectedLabels: List<String> = emptyList(),
) {
    private val byId: Map<String, List<ViewFact>> = views.groupBy { it.id }

    fun views(id: String): List<ViewFact> = byId[id].orEmpty()
    fun has(id: String) = byId.containsKey(id)
    fun hasAny(ids: Collection<String>) = ids.any { byId.containsKey(it) }
    fun count(id: String) = byId[id]?.size ?: 0

    fun hasText(markers: List<String>): Boolean =
        texts.any { t -> markers.any { m -> t.contains(m) } }

    fun hasExactText(labels: Set<String>): Boolean = texts.any { it in labels }

    companion object {
        fun normalizeText(raw: CharSequence): String =
            raw.toString().lowercase().replace('’', '\'').replace('‘', '\'').trim()
    }
}
