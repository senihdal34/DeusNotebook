package com.ethran.notable.editor.ui.rail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ethran.notable.data.datastore.ToolbarSide
import com.ethran.notable.editor.rail.RailGlyph
import com.ethran.notable.editor.rail.PopoverPlacement
import com.ethran.notable.editor.rail.RailMetrics
import com.ethran.notable.ui.components.House
import com.ethran.notable.ui.components.HouseCaps
import com.ethran.notable.ui.components.houseTap
import com.ethran.notable.ui.convertDpToPixel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Draws one of the rail's line icons. Decorative: the button around it carries the content description. */
@Composable
internal fun RailGlyphImage(glyph: RailGlyph, tint: Color, size: Dp = 30.dp) {
    Icon(RailIcons.of(glyph), contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

/**
 * A round rail button. Selected = filled black circle with a white icon; otherwise a thin icon on the paper.
 * A long press shows the label next to the button (plain text, no animation); [label] is also the accessibility description.
 * No ripple, no animation: e-ink.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RailButton(
    label: String,
    circle: Dp,
    side: ToolbarSide,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable (tint: Color) -> Unit,
) {
    var showLabel by remember { mutableStateOf(false) }
    val tint = when {
        selected -> Color.White
        !enabled -> House.Grey2
        else -> House.Ink
    }
    Box(
        Modifier
            .size(circle)
            .clip(CircleShape)
            .background(if (selected) House.Ink else Color.Transparent)
            .semantics { contentDescription = label; role = Role.Button }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (enabled) onClick() },
                onLongClick = { showLabel = true },
            ),
        contentAlignment = Alignment.Center
    ) {
        content(tint)
        if (showLabel) RailTooltip(label, circle, side) { showLabel = false }
    }
}

/** The label of a button, beside it, for a moment. */
@Composable
private fun RailTooltip(label: String, circle: Dp, side: ToolbarSide, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val gap = convertDpToPixel(circle + 10.dp, context).roundToInt()
    LaunchedEffect(Unit) {
        delay(1600)
        onDismiss()
    }
    Popup(
        alignment = if (side == ToolbarSide.Left) Alignment.CenterStart else Alignment.CenterEnd,
        offset = IntOffset(if (side == ToolbarSide.Left) gap else -gap, 0),
        properties = PopupProperties(focusable = false),
        onDismissRequest = onDismiss,
    ) {
        Box(
            Modifier
                .background(House.Ink, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) { Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, fontFamily = House.Sans) }
    }
}

/**
 * Lowest edge (in window pixels) of a bar that covers the top of the page area, such as the inbox bar. Popovers never
 * start above it, so they do not overlap the bar. 0 when there is no such bar.
 */
internal val LocalRailTopInset = compositionLocalOf { 0 }

/**
 * Frame of a popover: paper, black outline, no shadow. It opens beside the rail, top-aligned with its button (or
 * bottom-aligned), pushed down below [LocalRailTopInset] and up above the bottom of the window when needed.
 */
@Composable
internal fun RailPopover(
    side: ToolbarSide,
    bottomAligned: Boolean = false,
    padding: Dp = 18.dp,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val gap = convertDpToPixel((RailMetrics.GUTTER_DP - 4 + 6).dp, context).roundToInt()
    val left = side == ToolbarSide.Left
    val topInset = LocalRailTopInset.current
    val position = remember(left, bottomAligned, gap, topInset) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize,
            ): IntOffset {
                val x = if (left) anchorBounds.left + gap else anchorBounds.right - gap - popupContentSize.width
                val y = PopoverPlacement.y(
                    anchorBounds.top, anchorBounds.bottom, popupContentSize.height, windowSize.height, topInset, bottomAligned
                )
                return IntOffset(x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)), y)
            }
        }
    }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier
                .border(1.5.dp, House.Ink, RoundedCornerShape(18.dp))
                .background(House.Paper, RoundedCornerShape(18.dp))
                .padding(padding)
        ) { content() }
    }
}

@Composable
internal fun PopoverCaption(text: String, modifier: Modifier = Modifier) =
    HouseCaps(text, 12, House.Grey1, modifier = modifier.padding(bottom = 8.dp))

/** A pill-shaped choice: black when chosen. */
@Composable
internal fun Chip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    leading: (@Composable (tint: Color) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tint = if (selected) Color.White else House.Ink
    androidx.compose.foundation.layout.Row(
        modifier
            .height(46.dp)
            .clip(House.Pill)
            .border(1.5.dp, House.Ink, House.Pill)
            .background(if (selected) House.Ink else Color.Transparent)
            .semantics { role = Role.RadioButton }
            .houseTap(onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        leading?.invoke(tint)
        Text(text, color = tint, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = House.Sans)
    }
}

/** A colour choice: a filled circle with a ring (double ring when chosen) and the colour's initial letter. */
@Composable
internal fun Swatch(color: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    val c = Color(color)
    val dark = (0.299f * c.red + 0.587f * c.green + 0.114f * c.blue) < 0.55f
    Box(
        Modifier
            .size(54.dp)
            .clip(CircleShape)
            .border(if (selected) 3.5.dp else 1.5.dp, House.Ink, CircleShape)
            .semantics { contentDescription = name; role = Role.RadioButton }
            .houseTap(onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(c).border(1.dp, House.Grey2, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Black speaks for itself; the others get their initial so they stay apart on a black-and-white screen.
            if (color != android.graphics.Color.BLACK) {
                Text(
                    name.take(1).uppercase(), color = if (dark) Color.White else House.Ink,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = House.Sans
                )
            }
        }
    }
}

/** A square for the − and + next to the size slider. */
@Composable
internal fun StepButton(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 52.dp, height = 40.dp)
            .clip(House.Pill)
            .border(1.5.dp, House.Ink, House.Pill)
            .semantics { contentDescription = description; role = Role.Button }
            .houseTap(onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = House.Ink) }
}

/**
 * Plain slider: a thin track, a black bar up to the knob and a round knob. No halo, no animation.
 * [onChange] fires while dragging, [onChangeFinished] when the finger or pen lifts.
 */
@Composable
internal fun RailSlider(
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
) {
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onChangeFinished)
    var widthPx by remember { mutableIntStateOf(1) }
    val fraction = ((value - min) / (max - min)).coerceIn(0f, 1f)
    Box(
        modifier
            .height(44.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(min, max, step) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun at(x: Float) {
                        val raw = min + (x / size.width).coerceIn(0f, 1f) * (max - min)
                        change((Math.round(raw / step) * step).coerceIn(min, max))
                    }
                    at(down.position.x)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach {
                            if (it.positionChanged()) {
                                it.consume()
                                at(it.position.x)
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    finished()
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(House.Grey2.copy(alpha = 0.45f)))
        Box(Modifier.fillMaxWidth(fraction).height(6.dp).background(House.Ink))
        val knob = 30.dp
        val knobX = with(androidx.compose.ui.platform.LocalDensity.current) { (fraction * widthPx).toDp() - knob / 2 }
        Box(Modifier.padding(start = knobX.coerceAtLeast(0.dp)).size(knob).clip(CircleShape).background(House.Ink))
    }
}

/** A straight line as thick as the pen writes, in its colour: what the stroke will look like. */
@Composable
internal fun LinePreview(strokeSize: Float, color: Int, dashed: Boolean = false, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.fillMaxWidth().height(52.dp)) {
        val y = size.height / 2
        val cap = if (dashed) StrokeCap.Butt else StrokeCap.Round
        val pad = strokeSize / 2
        drawLine(
            Color(color), Offset(pad, y), Offset(size.width - pad, y), strokeWidth = strokeSize, cap = cap,
            pathEffect = if (dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(strokeSize * 3f, strokeSize * 2f)) else null
        )
    }
}

/** A thin rule between groups of buttons; as tall as [RailMetrics.RULE_DP]. */
@Composable
internal fun RailRule() {
    Box(Modifier.height(RailMetrics.RULE_DP.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(40.dp).height(1.5.dp).background(House.Grey2))
    }
}
