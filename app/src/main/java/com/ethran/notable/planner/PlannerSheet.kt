package com.ethran.notable.planner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * Lays [content] out on the fixed planner page and puts that page where [viewport] says: the content is measured at the
 * page's own size (so the tab bar, the zoom strip, the tap zones and the pill are laid out in page pixels, exactly as in
 * portrait) and then scaled and moved onto the sheet. Taps are mapped back through the same transform by Compose, so a tap
 * at a screen point hits the same zone that is drawn there. With the identity viewport (portrait, and every page that is
 * not a planner page) this is a plain full-size box.
 */
@Composable
fun PlannerSheet(viewport: PlannerViewport, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Layout(content = { Box(Modifier.fillMaxSize(), content = content) }, modifier = modifier.fillMaxSize()) { measurables, constraints ->
        val boxW = constraints.maxWidth
        val boxH = constraints.maxHeight
        if (viewport.isIdentity) {
            val placeable = measurables.first().measure(Constraints.fixed(boxW, boxH))
            layout(boxW, boxH) { placeable.place(0, 0) }
        } else {
            val placeable = measurables.first().measure(Constraints.fixed(viewport.pageW.roundToInt(), viewport.pageH.roundToInt()))
            layout(boxW, boxH) {
                placeable.placeWithLayer(viewport.offsetX.roundToInt(), viewport.offsetY.roundToInt()) {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = viewport.scale
                    scaleY = viewport.scale
                }
            }
        }
    }
}
