package app.screenbalance.service

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import app.screenbalance.engine.Box
import app.screenbalance.engine.InstagramSignatures
import app.screenbalance.engine.Snapshot
import app.screenbalance.engine.ViewFact

/**
 * Walks the visible Instagram node tree once and reduces it to a [Snapshot].
 *
 * Cost control: bounded node count and depth, invisible subtrees skipped, and only short
 * labels kept (long text is almost certainly user content, which we never need).
 */
internal object SnapshotBuilder {
    private const val MAX_NODES = 1_500
    private const val MAX_DEPTH = 64
    private const val MAX_LABEL_LENGTH = 48

    /** [idPrefix] is "<package>:id/" of the app on screen; only its own view ids are kept. */
    fun build(
        root: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int,
        idPrefix: String = InstagramSignatures.ID_PREFIX,
    ): Snapshot {
        val views = ArrayList<ViewFact>(128)
        val texts = ArrayList<String>(64)
        val selectedLabels = ArrayList<String>(4)
        val rect = Rect()
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        var count = 0

        while (stack.isNotEmpty() && count < MAX_NODES) {
            val (node, depth) = stack.removeLast()
            count++
            try {
                if (!node.isVisibleToUser) continue
                val id = node.viewIdResourceName
                if (id != null && id.startsWith(idPrefix)) {
                    node.getBoundsInScreen(rect)
                    views += ViewFact(
                        id = id.substring(idPrefix.length),
                        bounds = Box(rect.left, rect.top, rect.right, rect.bottom),
                        selected = node.isSelected,
                        focused = node.isFocused,
                        hasUserText = node.isEditable && !node.text.isNullOrEmpty() && !isShowingHint(node),
                    )
                }
                if (node.isSelected && !node.isEditable) {
                    collectLabel(node.contentDescription, selectedLabels)
                    collectLabel(node.text, selectedLabels)
                }
                if (!node.isEditable) {
                    collectLabel(node.text, texts)
                    collectLabel(node.contentDescription, texts)
                } else {
                    // A field's hint ("Search") is UI, not user content; what the user typed is never read.
                    collectLabel(node.hintText, texts)
                }
                if (depth < MAX_DEPTH) {
                    for (i in node.childCount - 1 downTo 0) {
                        val child = node.getChild(i) ?: continue
                        stack.addLast(child to depth + 1)
                    }
                }
            } finally {
                if (node !== root) recycle(node)
            }
        }
        // Release anything left when the node budget ran out.
        while (stack.isNotEmpty()) recycle(stack.removeLast().first)
        return Snapshot(screenWidth, screenHeight, views, texts, selectedLabels)
    }

    private fun collectLabel(value: CharSequence?, out: MutableList<String>) {
        if (value.isNullOrEmpty() || value.length > MAX_LABEL_LENGTH) return
        out += Snapshot.normalizeText(value)
    }

    private fun isShowingHint(node: AccessibilityNodeInfo): Boolean =
        node.isShowingHintText || (node.hintText != null && node.text?.toString() == node.hintText?.toString())

    @Suppress("DEPRECATION")
    fun recycle(node: AccessibilityNodeInfo) {
        // No-op from API 33; required on older versions to avoid leaking node objects.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) node.recycle()
    }
}
