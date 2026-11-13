package com.ethran.notable.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ethran.notable.editor.state.EditorState
import com.ethran.notable.ui.convertDpToPixel
import kotlin.math.max

/**
 * Vertical scroll indicator (right side).
 * Uses page.scroll.y (IntOffset) for the vertical position.
 */
@Composable
fun ScrollIndicator(state: EditorState) {
    BoxWithConstraints(
        modifier = Modifier
            .width(5.dp)
            .fillMaxHeight()
    ) {
        val viewportHeightPx = convertDpToPixel(this.maxHeight, LocalContext.current).toInt()
        val page = state.pageView
        // A planner sheet scaled into a landscape page area does not scroll.
        if (page.fixedViewport?.isIdentity == false) return@BoxWithConstraints

        // Total scrollable height approximation:
        // page.height is the total content height (page coordinates)
        // page.scroll.y + viewportHeightPx ensures indicator still shows while near bottom
        val virtualHeight = max(page.height, page.scroll.y.toInt() + viewportHeightPx)
        if (virtualHeight <= viewportHeightPx) return@BoxWithConstraints

        val indicatorSizeDp = (viewportHeightPx / virtualHeight.toFloat()) * this.maxHeight.value
        val indicatorPositionDp = (page.scroll.y / virtualHeight.toFloat()) * this.maxHeight.value

        if (!state.isToolbarOpen) return@BoxWithConstraints

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(
                    y = indicatorPositionDp.dp
                )
                .background(Color.Black)
                .height(indicatorSizeDp.dp)
        )
    }
}

/**
 * Horizontal scroll indicator (bottom). Drawn only while the page is scrolled sideways ([HorizontalIndicator]).
 * It is measured against its own width, the page area beside the tool rail: measuring against the window width
 * (which includes the rail's gutter) drew a thick black bar along the bottom of every page.
 */
@Composable
fun HorizontalScrollIndicator(state: EditorState) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))
        BoxWithConstraints(
            modifier = Modifier
                .height(5.dp)
                .fillMaxWidth()
        ) {
            if (!state.isToolbarOpen) return@BoxWithConstraints
            val viewportWidthPx = convertDpToPixel(this.maxWidth, LocalContext.current).toInt()
            val (start, size) = HorizontalIndicator.fractions(viewportWidthPx, state.pageView.scroll.x)
                ?: return@BoxWithConstraints

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .offset(x = (start * this.maxWidth.value).dp)
                    .background(Color.Black)
                    .width((size * this.maxWidth.value).dp)
            )
        }
    }
}
