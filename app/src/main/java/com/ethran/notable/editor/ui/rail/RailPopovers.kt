package com.ethran.notable.editor.ui.rail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.R
import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.data.datastore.ToolbarSide
import com.ethran.notable.data.db.getParentFolder
import com.ethran.notable.editor.EditorControlTower
import com.ethran.notable.editor.canvas.CanvasEventBus
import com.ethran.notable.editor.rail.MoreItem
import com.ethran.notable.editor.rail.RailContext
import com.ethran.notable.editor.rail.RailGlyph
import com.ethran.notable.editor.rail.RailLayout
import com.ethran.notable.editor.state.AnnotationMode
import com.ethran.notable.editor.state.EditorState
import com.ethran.notable.editor.state.Mode
import com.ethran.notable.editor.utils.Eraser
import com.ethran.notable.editor.utils.Pen
import com.ethran.notable.editor.utils.PenSetting
import com.ethran.notable.io.ExportEngine
import com.ethran.notable.io.ExportFormat
import com.ethran.notable.io.ExportTarget
import com.ethran.notable.ui.LocalSnackContext
import com.ethran.notable.ui.SnackConf
import com.ethran.notable.ui.components.House
import com.ethran.notable.ui.components.houseTap
import com.ethran.notable.ui.views.BugReportDestination
import com.ethran.notable.ui.views.LibraryDestination
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val INK_COLOURS = listOf(
    android.graphics.Color.BLACK to R.string.rail_colour_black,
    android.graphics.Color.RED to R.string.rail_colour_red,
    android.graphics.Color.BLUE to R.string.rail_colour_blue,
    android.graphics.Color.GREEN to R.string.rail_colour_green,
)
private val MONO_COLOURS = listOf(
    android.graphics.Color.BLACK to R.string.rail_colour_black,
    0xFF444444.toInt() to R.string.rail_colour_dark_grey,
    0xFF888888.toInt() to R.string.rail_colour_grey,
    0xFFCCCCCC.toInt() to R.string.rail_colour_light_grey,
)
private val HIGHLIGHTER_COLOURS = listOf(
    android.graphics.Color.LTGRAY to R.string.rail_colour_light_grey,
    android.graphics.Color.YELLOW to R.string.rail_colour_yellow,
    android.graphics.Color.GREEN to R.string.rail_colour_green,
    android.graphics.Color.CYAN to R.string.rail_colour_cyan,
    android.graphics.Color.MAGENTA to R.string.rail_colour_pink,
)
private val HIGHLIGHTER_MONO = listOf(
    android.graphics.Color.LTGRAY to R.string.rail_colour_light_grey,
    0xFF888888.toInt() to R.string.rail_colour_grey,
    0xFF444444.toInt() to R.string.rail_colour_dark_grey,
)

/** Size range and step of a pen's slider, in page pixels (the same unit the old S/M/L/XL buttons used). */
private fun sizeRange(pen: Pen): Triple<Float, Float, Float> =
    if (pen == Pen.MARKER) Triple(20f, 90f, 5f) else Triple(2f, 30f, 1f)

/**
 * Pen popover: pen type (Ballpoint, Fountain; the highlighter has none), colour, size with a live line preview.
 * Colour and size belong to the pen type (the existing per-pen settings).
 */
@Composable
internal fun PenPopover(
    state: EditorState,
    pen: Pen,
    onPen: (Pen) -> Unit,
    onSetting: (Pen, PenSetting) -> Unit,
) {
    val isMarker = pen == Pen.MARKER
    val setting = state.penSettings[pen.penName] ?: Pen.DEFAULT_SETTINGS.getValue(pen.penName)
    val (min, max, step) = sizeRange(pen)
    var live by remember(pen, setting.strokeSize) { mutableFloatStateOf(setting.strokeSize.coerceIn(min, max)) }
    val continuous = GlobalAppSettings.current.continuousStrokeSlider
    val mono = GlobalAppSettings.current.monochromeMode
    val colours = when {
        isMarker && mono -> HIGHLIGHTER_MONO
        isMarker -> HIGHLIGHTER_COLOURS
        mono -> MONO_COLOURS
        else -> INK_COLOURS
    }
    fun commit(size: Float) = onSetting(pen, PenSetting(size, setting.color))

    Column(Modifier.width(380.dp)) {
        PopoverCaption(stringResource(if (isMarker) R.string.rail_pop_highlighter else R.string.rail_pop_pen))
        if (!isMarker) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(
                    stringResource(R.string.rail_pen_ballpoint), selected = pen == Pen.BALLPEN,
                    leading = { RailGlyphImage(RailGlyph.PenBallpoint, it, 26.dp) }
                ) { onPen(Pen.BALLPEN) }
                Chip(
                    stringResource(R.string.rail_pen_fountain), selected = pen == Pen.FOUNTAIN,
                    leading = { RailGlyphImage(RailGlyph.PenFountain, it, 26.dp) }
                ) { onPen(Pen.FOUNTAIN) }
            }
            Spacer(Modifier.height(16.dp))
        }
        PopoverCaption(stringResource(R.string.rail_pop_colour))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colours.forEach { (color, nameRes) ->
                Swatch(color, stringResource(nameRes), selected = setting.color == color) {
                    onSetting(pen, PenSetting(live, color))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PopoverCaption(stringResource(R.string.rail_pop_size))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepButton("–", stringResource(R.string.rail_size_minus)) {
                live = (live - step).coerceAtLeast(min); commit(live)
            }
            RailSlider(
                value = live, min = min, max = max, step = step, modifier = Modifier.weight(1f),
                onChange = { live = it; if (continuous) commit(it) },
                onChangeFinished = { commit(live) },
            )
            StepButton("+", stringResource(R.string.rail_size_plus)) {
                live = (live + step).coerceAtMost(max); commit(live)
            }
        }
        LinePreview(live, setting.color, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun EraserPopover(
    eraser: Eraser,
    onEraser: (Eraser) -> Unit,
    scribbleToErase: Boolean,
    onScribbleToErase: (Boolean) -> Unit,
) {
    var scribble by remember { mutableStateOf(scribbleToErase) }
    Column(Modifier.width(340.dp)) {
        PopoverCaption(stringResource(R.string.rail_pop_eraser))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(
                stringResource(R.string.rail_eraser_stroke), selected = eraser == Eraser.PEN,
                leading = { RailGlyphImage(RailGlyph.Eraser, it, 26.dp) }
            ) { onEraser(Eraser.PEN) }
            Chip(
                stringResource(R.string.rail_eraser_area), selected = eraser == Eraser.SELECT,
                leading = { RailGlyphImage(RailGlyph.EraserArea, it, 26.dp) }
            ) { onEraser(Eraser.SELECT) }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().houseTap { scribble = !scribble; onScribbleToErase(scribble) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.rail_scribble_erase), color = House.Ink, fontSize = 18.sp,
                fontWeight = FontWeight.Medium, fontFamily = House.Sans, modifier = Modifier.weight(1f)
            )
            Box(
                Modifier.size(30.dp).border(2.dp, House.Ink, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .background(if (scribble) House.Ink else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) { if (scribble) Text("✓", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
internal fun LinePopover(dashed: Boolean, setting: PenSetting?, onDashed: (Boolean) -> Unit) {
    Column(Modifier.width(340.dp)) {
        PopoverCaption(stringResource(R.string.rail_pop_line))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(
                stringResource(R.string.rail_line_solid), selected = !dashed,
                leading = { RailGlyphImage(RailGlyph.Line, it, 26.dp) }
            ) { onDashed(false) }
            Chip(
                stringResource(R.string.rail_line_dashed), selected = dashed,
                leading = { RailGlyphImage(RailGlyph.LineDashed, it, 26.dp) }
            ) { onDashed(true) }
        }
        LinePreview(setting?.strokeSize ?: 5f, setting?.color ?: android.graphics.Color.BLACK, dashed, Modifier.padding(top = 12.dp))
    }
}

/**
 * The ⋯ menu. Everything the old top bar and menu offered that is not a rail button lives here:
 * insert image, paste, page background, reset view, export, clear page, annotations, library, bug report, hide.
 */
@Composable
internal fun RailMoreMenu(
    ctx: RailContext,
    side: ToolbarSide,
    state: EditorState,
    controlTower: EditorControlTower,
    exportEngine: ExportEngine,
    navController: NavController,
    appRepository: AppRepository,
    scope: CoroutineScope,
    onInsertImage: () -> Unit,
    onHide: () -> Unit,
) {
    val snackManager = LocalSnackContext.current
    var exportOpen by remember { mutableStateOf(false) }
    fun close() { state.menuStates.isMenuOpen = false }

    val pagePdf = stringResource(R.string.exporting_the_page_to, "PDF")
    val pagePng = stringResource(R.string.exporting_the_page_to, "PNG")
    val pageJpeg = stringResource(R.string.exporting_the_page_to, "JPEG")
    val pageXopp = stringResource(R.string.exporting_the_page_to, "xopp")
    val bookPdf = stringResource(R.string.exporting_the_book_to, "PDF")
    val bookPng = stringResource(R.string.exporting_the_book_to, "PNG")
    val bookXopp = stringResource(R.string.exporting_the_book_to, "xopp")
    val cleared = stringResource(R.string.cleared_all_strokes)

    fun export(message: String, target: ExportTarget, format: ExportFormat) {
        scope.launch(Dispatchers.IO) {
            snackManager.runWithSnack(message) { exportEngine.export(target = target, format = format) }
        }
        close()
    }

    RailPopover(side, bottomAligned = true, padding = 6.dp, onDismiss = { close() }) {
        Column(Modifier.width(340.dp)) {
            if (!exportOpen) {
                RailLayout.moreItems(ctx).forEachIndexed { i, item ->
                    if (i > 0) MenuRule()
                    when (item) {
                        MoreItem.InsertImage -> MenuRow(stringResource(R.string.rail_more_insert_image)) { close(); onInsertImage() }
                        MoreItem.Paste -> MenuRow(stringResource(R.string.rail_more_paste)) { close(); controlTower.pasteFromClipboard() }
                        MoreItem.PageBackground -> MenuRow(stringResource(R.string.rail_more_background)) {
                            close(); state.menuStates.isBackgroundSelectorModalOpen = true
                        }
                        MoreItem.ResetView -> MenuRow(stringResource(R.string.rail_more_reset_view)) { close(); controlTower.resetZoomAndScroll() }
                        MoreItem.Export -> MenuRow(stringResource(R.string.rail_more_export), trailing = "›") { exportOpen = true }
                        MoreItem.ClearPage -> MenuRow(stringResource(R.string.clean_all_strokes)) {
                            close()
                            scope.launch {
                                CanvasEventBus.clearPageSignal.emit(Unit)
                                snackManager.displaySnack(SnackConf(text = cleared, duration = 3000))
                            }
                        }
                        MoreItem.WikiLink -> MenuRow(
                            stringResource(R.string.rail_more_wiki_link), checked = state.annotationMode == AnnotationMode.WikiLink
                        ) { toggleAnnotation(state, AnnotationMode.WikiLink); close() }
                        MoreItem.Tag -> MenuRow(
                            stringResource(R.string.rail_more_tag), checked = state.annotationMode == AnnotationMode.Tag
                        ) { toggleAnnotation(state, AnnotationMode.Tag); close() }
                        MoreItem.Library -> MenuRow(stringResource(R.string.home_view_name)) {
                            close()
                            scope.launch {
                                val page = withContext(Dispatchers.IO) { appRepository.pageRepository.getById(state.currentPageId) }
                                val parent = withContext(Dispatchers.IO) { page?.getParentFolder(appRepository.bookRepository) }
                                navController.navigate(LibraryDestination.createRoute(parent))
                            }
                        }
                        MoreItem.BugReport -> MenuRow(stringResource(R.string.bug_report)) {
                            close(); navController.navigate(BugReportDestination.route)
                        }
                        MoreItem.HideToolbar -> MenuRow(stringResource(R.string.rail_more_hide)) { close(); onHide() }
                    }
                }
            } else {
                MenuRow(stringResource(R.string.rail_more_export), leading = "‹", bold = true) { exportOpen = false }
                val pageTarget = ExportTarget.Page(pageId = state.currentPageId)
                MenuRule()
                MenuRow(stringResource(R.string.export_page_to, "PDF")) { export(pagePdf, pageTarget, ExportFormat.PDF) }
                MenuRow(stringResource(R.string.export_page_to, "PNG")) { export(pagePng, pageTarget, ExportFormat.PNG) }
                MenuRow(stringResource(R.string.export_page_to, "JPEG")) { export(pageJpeg, pageTarget, ExportFormat.JPEG) }
                MenuRow(stringResource(R.string.export_page_to, "xopp")) { export(pageXopp, pageTarget, ExportFormat.XOPP) }
                state.bookId?.let { bookId ->
                    MenuRule()
                    val bookTarget = ExportTarget.Book(bookId = bookId)
                    MenuRow(stringResource(R.string.export_book_to, "PDF")) { export(bookPdf, bookTarget, ExportFormat.PDF) }
                    MenuRow(stringResource(R.string.export_book_to, "PNG")) { export(bookPng, bookTarget, ExportFormat.PNG) }
                    MenuRow(stringResource(R.string.export_book_to, "xopp")) { export(bookXopp, bookTarget, ExportFormat.XOPP) }
                }
            }
        }
    }
}

private fun toggleAnnotation(state: EditorState, mode: AnnotationMode) {
    state.annotationMode = if (state.annotationMode == mode) AnnotationMode.None else mode
    // The stylus gesture that makes the annotation is drawn in draw mode.
    if (state.annotationMode != AnnotationMode.None) state.mode = Mode.Draw
}

@Composable
private fun MenuRow(
    label: String,
    leading: String? = null,
    trailing: String? = null,
    checked: Boolean = false,
    bold: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).houseTap(onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leading != null) Text(leading, fontSize = 24.sp, color = House.Ink, fontWeight = FontWeight.Bold)
        Text(
            label, modifier = Modifier.weight(1f), fontSize = 20.sp, fontFamily = House.Sans, color = House.Ink,
            fontWeight = if (bold || checked) FontWeight.Bold else FontWeight.Medium
        )
        if (checked) Text("✓", fontSize = 20.sp, color = House.Ink, fontWeight = FontWeight.Bold)
        if (trailing != null) Text(trailing, fontSize = 24.sp, color = House.Ink)
    }
}

@Composable
private fun MenuRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(House.Grey2.copy(alpha = 0.6f)))
}
