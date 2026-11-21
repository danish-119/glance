package app.screenbalance.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.screenbalance.ui.theme.LocalAccents
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * A horizontal ruler you swipe to pick a value: fast for big jumps, snaps to each [step].
 * The value under the centre mark is committed once the scroll settles.
 */
@Composable
fun ScrollPicker(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    step: Int,
    format: (Int) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Values that get a long tick and a label under the ruler. */
    majorEvery: Int = 30,
    tickLabel: (Int) -> String = { v ->
        when {
            v == 0 -> "Off"
            v < 60 -> "${v}m"
            v % 60 == 0 -> "${v / 60}h"
            else -> "${v / 60}h ${v % 60}m"
        }
    },
) {
    val values = remember(range, step) { (range step step).toList() }
    val index = values.indexOfFirst { it >= value }.let { if (it < 0) values.lastIndex else it }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index)
    val scope = rememberCoroutineScope()
    val commit by rememberUpdatedState(onValueChange)
    val current by rememberUpdatedState(value)
    val itemWidth = 56.dp
    val itemPx = with(LocalDensity.current) { itemWidth.toPx() }

    // With half-width side padding, the first visible item (rounded by offset) sits under the mark.
    val centered by remember {
        derivedStateOf {
            val i = state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
            i.coerceIn(0, values.lastIndex)
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().filter { !it }.collect {
            val v = values[centered]
            if (v != current) commit(v)
        }
    }
    // Follow outside changes (e.g. settings restored) when the user isn't dragging.
    LaunchedEffect(index) {
        if (!state.isScrollInProgress && centered != index) state.scrollToItem(index)
    }

    val accents = LocalAccents.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = format(values[centered])
                if (enabled) customActions = listOf(
                    CustomAccessibilityAction("Increase") {
                        if (index < values.lastIndex) commit(values[index + 1]); true
                    },
                    CustomAccessibilityAction("Decrease") {
                        if (index > 0) commit(values[index - 1]); true
                    },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            format(values[centered]),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(44.dp)) {
            val side = (maxWidth - itemWidth) / 2
            LazyRow(
                state = state,
                flingBehavior = rememberSnapFlingBehavior(state),
                userScrollEnabled = enabled,
                contentPadding = PaddingValues(horizontal = side),
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(values) { i, v ->
                    val selected = i == centered
                    val major = v % majorEvery == 0
                    val color by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onSurface else accents.faint, label = "tick",
                    )
                    Column(
                        Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = enabled,
                            ) { scope.launch { state.animateScrollToItem(i) } },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(Modifier.width(if (selected) 2.dp else 1.dp).height(if (major || selected) 18.dp else 10.dp).background(color))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (major) tickLabel(v) else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = accents.faint,
                        )
                    }
                }
            }
            // Centre mark.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .width(2.dp)
                    .height(6.dp)
                    .background(accents.ember),
            )
        }
    }
}
