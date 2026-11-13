package com.ethran.notable.editor.ui.rail

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.ethran.notable.R
import com.ethran.notable.data.AppRepository
import com.ethran.notable.data.copyImageToDatabase
import com.ethran.notable.data.datastore.GlobalAppSettings
import com.ethran.notable.data.datastore.ToolbarSide
import com.ethran.notable.data.db.Notebook
import com.ethran.notable.data.db.getPageIndex
import com.ethran.notable.data.db.getParentFolder
import com.ethran.notable.editor.EditorControlTower
import com.ethran.notable.editor.canvas.CanvasEventBus
import com.ethran.notable.editor.rail.GlyphState
import com.ethran.notable.editor.rail.PagePosition
import com.ethran.notable.editor.rail.RailContext
import com.ethran.notable.editor.rail.RailGlyph
import com.ethran.notable.editor.rail.RailGlyphs
import com.ethran.notable.editor.rail.RailItem
import com.ethran.notable.editor.rail.RailLayout
import com.ethran.notable.editor.rail.RailMetrics
import com.ethran.notable.editor.rail.RailVisibility
import com.ethran.notable.editor.state.EditorState
import com.ethran.notable.editor.state.Mode
import com.ethran.notable.editor.utils.Pen
import com.ethran.notable.editor.utils.PenSetting
import com.ethran.notable.io.ExportEngine
import com.ethran.notable.ui.convertDpToPixel
import com.ethran.notable.ui.dialogs.BackgroundSelector
import com.ethran.notable.ui.components.House
import com.ethran.notable.ui.components.houseTap
import com.ethran.notable.ui.views.BugReportDestination
import com.ethran.notable.ui.views.LibraryDestination
import com.ethran.notable.ui.views.PagesDestination
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode
import io.shipbook.shipbooksdk.ShipBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val log = ShipBook.getLogger("EditorToolRail")

/** Which popover is open beside the rail. */
internal enum class RailPopoverKind { Pen, Eraser, Highlighter, Line }

/**
 * The tool rail: a vertical strip of round buttons beside the page area (left by default, right for left-handed use).
 *
 * It sits in a gutter of constant width ([RailMetrics.GUTTER_DP], the width the old sidebar had when open). Folding it
 * into a handle changes only what is drawn in the gutter, never the width of the page area, so pages do not move
 * or rescale and the writing surface is not reconfigured while the pen is busy.
 *
 * @param onBack leave the editor without anything extra (on inbox pages the top bar's Save & Exit button exports).
 */
@Composable
fun EditorToolRail(
    exportEngine: ExportEngine,
    navController: NavController,
    appRepository: AppRepository,
    state: EditorState,
    controlTower: EditorControlTower,
    isPlannerPage: Boolean,
    isInboxPage: Boolean,
    onBack: () -> Unit,
) {
    val settings = GlobalAppSettings.current
    val side = settings.toolbarSide
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current

    // The e-ink controller does not repaint plain view changes while the pen layer is active: ask for it.
    fun refreshRail() {
        view.postInvalidate()
        try {
            val gutterPx = convertDpToPixel(RailMetrics.GUTTER_DP.dp, context).toInt()
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            val x = if (side == ToolbarSide.Left) loc[0] else loc[0] + view.width - gutterPx
            EpdController.refreshScreenRegion(view, x, loc[1], gutterPx, view.height, UpdateMode.GC)
        } catch (e: Exception) {
            log.w("E-ink rail refresh failed: ${e.message}")
        } catch (e: LinkageError) {
            log.w("E-ink rail refresh not available: ${e.message}")
        }
    }

    // Image picker: registered here, so it survives the menu that starts it.
    val pickMedia = rememberLauncherForActivityResult(contract = PickVisualMedia()) { uri ->
        if (uri == null) {
            log.w("PickVisualMedia: uri is null (user cancelled)")
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            try {
                val copiedFile = copyImageToDatabase(context, uri)
                log.i("Image copied to: ${copiedFile.toUri()}")
                CanvasEventBus.addImageByUri.value = copiedFile.toUri()
            } catch (e: Exception) {
                log.e("ImagePicker: copy failed: ${e.message}", e)
            }
        }
    }
    PageBackgroundDialog(state)
    LaunchedEffect(state.menuStates.isBackgroundSelectorModalOpen, state.menuStates.isMenuOpen) {
        state.checkForSelectionsAndMenus()
    }

    // --- folded / open -------------------------------------------------------------------------------------------
    val autoHide = settings.toolbarAutoHide
    val visibility = remember { RailVisibility(autoHide = autoHide, startFolded = !state.isToolbarOpen || isPlannerPage) }
    var folded by remember { mutableStateOf(visibility.folded) }
    var popover by remember { mutableStateOf<RailPopoverKind?>(null) }
    val busy by rememberUpdatedState(
        popover != null || state.menuStates.isMenuOpen || state.menuStates.isBackgroundSelectorModalOpen
    )

    LaunchedEffect(autoHide) { visibility.setAutoHide(autoHide) }
    // Planner pages start folded: the week and day pages have their own tab bar, zoom strip and label column.
    LaunchedEffect(isPlannerPage) {
        if (isPlannerPage) {
            visibility.fold(); folded = true
        }
    }
    // Other routes to show or hide the toolbar (two-finger swipe, the inbox bar's pen button).
    LaunchedEffect(Unit) {
        snapshotFlow { state.isToolbarOpen }.drop(1).collect { open ->
            if (open) visibility.unfold() else visibility.fold()
            folded = visibility.folded
            popover = null
            refreshRail()
        }
    }
    // Fold after about five seconds of writing.
    LaunchedEffect(autoHide) {
        if (!autoHide) return@LaunchedEffect
        var lastSeen = 0L
        while (true) {
            delay(500)
            val w = state.lastWritingMs
            if (w != 0L && w != lastSeen) {
                visibility.onPenWriting(w); lastSeen = w
            }
            if (visibility.tick(SystemClock.uptimeMillis(), busy)) {
                folded = true
                refreshRail()
            }
        }
    }

    fun unfold() {
        visibility.unfold(); folded = false
        if (!state.isToolbarOpen) state.isToolbarOpen = true
        refreshRail()
    }

    val gutter = Modifier
        .width(RailMetrics.GUTTER_DP.dp)
        .fillMaxHeight()
        .background(House.Paper)
        .drawBehind {
            // A thin black line on the edge facing the page.
            val x = if (side == ToolbarSide.Left) size.width - 0.5.dp.toPx() else 0.5.dp.toPx()
            drawLine(Color.Black, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        }

    if (folded) {
        FoldedHandle(gutter, side, onTap = ::unfold)
        return
    }

    // --- data for the buttons ------------------------------------------------------------------------------------
    val repository = appRepository.bookRepository
    var bookVersion by remember { mutableIntStateOf(0) }
    var book by remember(state.bookId) { mutableStateOf<Notebook?>(null) }
    LaunchedEffect(state.bookId, state.currentPageId, bookVersion) {
        book = state.bookId?.let { id -> withContext(Dispatchers.IO) { repository.getById(id) } }
    }
    val position = book?.let { PagePosition(it.getPageIndex(state.currentPageId), it.pageIds.size) }

    var pinned by remember(state.currentPageId) { mutableStateOf(false) }
    LaunchedEffect(state.currentPageId) {
        pinned = withContext(Dispatchers.IO) { appRepository.pageRepository.getById(state.currentPageId)?.pinned == 1 }
    }

    val zoomLevel by state.pageView.zoomLevel.collectAsState()
    val ctx = RailContext(
        isInboxPage = isInboxPage,
        isPlannerPage = isPlannerPage,
        inBook = state.bookId != null,
        clipboardHasContent = state.clipboard != null,
        // A fixed planner page is fitted to the screen and never moved by the user, so there is nothing to reset.
        viewMoved = state.pageView.fixedViewport == null && (state.pageView.scroll.x != 0f || zoomLevel != 1.0f),
    )

    // Drawing is paused while a popover or menu is open, and handed back to the editor state afterwards.
    var pausedForPopover by remember { mutableStateOf(false) }
    LaunchedEffect(popover) {
        if (popover != null) {
            state.isDrawing = false; pausedForPopover = true
        } else if (pausedForPopover) {
            pausedForPopover = false; state.checkForSelectionsAndMenus()
        }
    }

    // Remember which ink pen the "Pen" button means while the highlighter is the current pen.
    var inkPen by remember { mutableStateOf(if (state.pen == Pen.MARKER) Pen.FOUNTAIN else state.pen) }
    LaunchedEffect(state.pen) { if (state.pen != Pen.MARKER) inkPen = state.pen }

    fun penSettingsFor(pen: Pen, setting: PenSetting) {
        val map = state.penSettings.toMutableMap()
        map[pen.penName] = setting.copy()
        state.penSettings = map
    }

    fun choose(mode: Mode, pen: Pen? = null) {
        popover = null
        state.mode = mode
        if (pen != null) state.pen = pen
        refreshRail()
    }

    // --- layout ---------------------------------------------------------------------------------------------------
    val groups = RailLayout.topGroups(ctx)
    val bottom = RailLayout.bottom(ctx)
    val slots = groups.sumOf { it.size } + bottom.size
    val rules = groups.size - 1
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()

    BoxWithConstraints(gutter) {
        val circle = RailMetrics.circleDp(screenWidthDp, maxHeight.value, slots, rules)
        val content = RailMetrics.contentHeightDp(slots, rules, circle)
        val filler = (maxHeight.value - content).coerceAtLeast(2f)

        @Composable
        fun Item(item: RailItem) {
            val c = circle.dp
            fun glyph(it: RailItem): RailGlyph =
                RailGlyphs.forItem(it, GlyphState(inkPen = inkPen, eraser = state.eraser, lineDashed = state.lineDashed, pinned = pinned))
                    ?: RailGlyph.Page
            when (item) {
                RailItem.Back -> RailButton(
                    label = stringResource(R.string.rail_back),
                    circle = c, side = side, onClick = { popover = null; onBack() }
                ) { RailGlyphImage(glyph(item), it) }

                RailItem.Pen -> {
                    val selected = state.mode == Mode.Draw && state.pen != Pen.MARKER
                    Box {
                        RailButton(
                            label = stringResource(R.string.rail_pen), circle = c, side = side, selected = selected,
                            onClick = {
                                if (selected) popover = if (popover == RailPopoverKind.Pen) null else RailPopoverKind.Pen
                                else choose(Mode.Draw, inkPen)
                            }
                        ) { RailGlyphImage(glyph(item), it) }
                        if (popover == RailPopoverKind.Pen) {
                            RailPopover(side, onDismiss = { popover = null }) {
                                PenPopover(
                                    state = state, pen = state.pen,
                                    onPen = { state.mode = Mode.Draw; state.pen = it },
                                    onSetting = { pen, setting -> penSettingsFor(pen, setting) },
                                )
                            }
                        }
                    }
                }

                RailItem.Eraser -> {
                    val selected = state.mode == Mode.Erase
                    Box {
                        RailButton(
                            label = stringResource(R.string.rail_eraser), circle = c, side = side, selected = selected,
                            onClick = {
                                if (selected) popover = if (popover == RailPopoverKind.Eraser) null else RailPopoverKind.Eraser
                                else choose(Mode.Erase)
                            }
                        ) { RailGlyphImage(glyph(item), it) }
                        if (popover == RailPopoverKind.Eraser) {
                            RailPopover(side, onDismiss = { popover = null }) {
                                EraserPopover(
                                    eraser = state.eraser,
                                    onEraser = { state.eraser = it },
                                    scribbleToErase = GlobalAppSettings.current.scribbleToEraseEnabled,
                                    onScribbleToErase = { enabled ->
                                        scope.launch(Dispatchers.IO) {
                                            appRepository.kvProxy.setAppSettings(
                                                GlobalAppSettings.current.copy(scribbleToEraseEnabled = enabled)
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                RailItem.Highlighter -> {
                    val selected = state.mode == Mode.Draw && state.pen == Pen.MARKER
                    Box {
                        RailButton(
                            label = stringResource(R.string.rail_highlighter), circle = c, side = side, selected = selected,
                            onClick = {
                                if (selected) popover = if (popover == RailPopoverKind.Highlighter) null else RailPopoverKind.Highlighter
                                else choose(Mode.Draw, Pen.MARKER)
                            }
                        ) { RailGlyphImage(glyph(item), it) }
                        if (popover == RailPopoverKind.Highlighter) {
                            RailPopover(side, onDismiss = { popover = null }) {
                                PenPopover(
                                    state = state, pen = Pen.MARKER,
                                    onPen = {},
                                    onSetting = { pen, setting -> penSettingsFor(pen, setting) },
                                )
                            }
                        }
                    }
                }

                RailItem.Lasso -> RailButton(
                    label = stringResource(R.string.rail_lasso), circle = c, side = side, selected = state.mode == Mode.Select,
                    onClick = { choose(Mode.Select) }
                ) { RailGlyphImage(glyph(item), it) }

                RailItem.Line -> {
                    val selected = state.mode == Mode.Line
                    Box {
                        RailButton(
                            label = stringResource(R.string.rail_line), circle = c, side = side, selected = selected,
                            onClick = {
                                if (selected) popover = if (popover == RailPopoverKind.Line) null else RailPopoverKind.Line
                                else choose(Mode.Line)
                            }
                        ) { RailGlyphImage(glyph(item), it) }
                        if (popover == RailPopoverKind.Line) {
                            RailPopover(side, onDismiss = { popover = null }) {
                                LinePopover(
                                    dashed = state.lineDashed,
                                    setting = state.penSettings[state.pen.penName],
                                    onDashed = { state.lineDashed = it },
                                )
                            }
                        }
                    }
                }

                RailItem.Undo -> RailButton(
                    label = stringResource(R.string.rail_undo), circle = c, side = side,
                    onClick = { popover = null; controlTower.undo() }
                ) { RailGlyphImage(glyph(item), it) }

                RailItem.Redo -> RailButton(
                    label = stringResource(R.string.rail_redo), circle = c, side = side,
                    onClick = { popover = null; controlTower.redo() }
                ) { RailGlyphImage(glyph(item), it) }

                RailItem.PageCounter -> {
                    val label = if (position != null && position.known)
                        stringResource(R.string.rail_page_of, position.number, position.total)
                    else stringResource(R.string.rail_pages)
                    RailButton(
                        label = label, circle = c, side = side,
                        onClick = { popover = null; state.bookId?.let { navController.navigate(PagesDestination.createRoute(it)) } }
                    ) { tint ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (position?.known == true) position.number.toString() else "?",
                                color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = House.Sans, textAlign = TextAlign.Center
                            )
                            Text(
                                if (position?.known == true) stringResource(R.string.rail_page_short_of, position.total) else "",
                                color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = House.Sans, textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                RailItem.PrevPage -> RailButton(
                    label = stringResource(R.string.rail_prev_page), circle = c, side = side, enabled = position?.hasPrev == true,
                    onClick = { popover = null; controlTower.goToPreviousPage() }
                ) { RailGlyphImage(glyph(item), it) }

                RailItem.NextPage -> RailButton(
                    label = stringResource(R.string.rail_next_page), circle = c, side = side, enabled = position?.hasNext == true,
                    onClick = { popover = null; controlTower.goToNextPage() }
                ) { RailGlyphImage(glyph(item), it) }

                RailItem.NewPage -> RailButton(
                    label = stringResource(R.string.rail_new_page), circle = c, side = side,
                    onClick = {
                        popover = null
                        state.bookId?.let { bookId ->
                            scope.launch(Dispatchers.IO) {
                                val index = runCatching { appRepository.getPageNumber(bookId, state.currentPageId) }.getOrNull()
                                val id = index?.let { appRepository.newPageInBook(bookId, it + 1) }
                                if (id != null) {
                                    bookVersion++
                                    CanvasEventBus.changePage.emit(id)
                                }
                            }
                        }
                    }
                ) { RailGlyphImage(glyph(item), it) }

                RailItem.Favorite -> RailButton(
                    label = stringResource(if (pinned) R.string.rail_favorite_remove else R.string.rail_favorite_add),
                    circle = c, side = side,
                    onClick = {
                        popover = null
                        val id = state.currentPageId
                        val next = !pinned
                        pinned = next
                        scope.launch(Dispatchers.IO) { appRepository.pageRepository.setPinned(id, next) }
                    }
                ) { RailGlyphImage(glyph(item), it) }

                RailItem.More -> Box {
                    RailButton(
                        label = stringResource(R.string.rail_more), circle = c, side = side, selected = state.menuStates.isMenuOpen,
                        onClick = { popover = null; state.menuStates.isMenuOpen = !state.menuStates.isMenuOpen }
                    ) { RailGlyphImage(glyph(item), it) }
                    if (state.menuStates.isMenuOpen) {
                        RailMoreMenu(
                            ctx = ctx, side = side, state = state, controlTower = controlTower, exportEngine = exportEngine,
                            navController = navController, appRepository = appRepository, scope = scope,
                            onInsertImage = { pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                            onHide = { state.isToolbarOpen = false },
                        )
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = RailMetrics.PADDING_DP.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RailMetrics.GAP_DP.dp),
        ) {
            groups.forEachIndexed { i, group ->
                if (i > 0) RailRule()
                group.forEach { Item(it) }
            }
            Box(Modifier.height(filler.dp))
            bottom.forEach { Item(it) }
        }
    }
}

/** The rail folded away: only a thin bar at the screen edge; a tap anywhere in the gutter opens the rail again. */
@Composable
private fun FoldedHandle(gutter: Modifier, side: ToolbarSide, onTap: () -> Unit) {
    val description = stringResource(R.string.rail_show)
    Box(
        gutter
            .semantics { contentDescription = description }
            .houseTap(onTap)
    ) {
        Box(
            Modifier
                .align(if (side == ToolbarSide.Left) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 6.dp)
                .size(width = 8.dp, height = 96.dp)
                .background(House.Grey1, RoundedCornerShape(4.dp))
        )
    }
}

/** The background chooser, opened from the more menu. */
@Composable
internal fun PageBackgroundDialog(state: EditorState) {
    val scope = rememberCoroutineScope()
    if (!state.menuStates.isBackgroundSelectorModalOpen) return
    BackgroundSelector(
        initialPageBackgroundType = state.pageView.pageFromDb?.backgroundType ?: "native",
        initialPageBackground = state.pageView.pageFromDb?.background ?: "blank",
        initialPageNumberInPdf = state.pageView.getBackgroundPageNumber(),
        notebookId = state.pageView.pageFromDb?.notebookId,
        pageNumberInBook = state.pageView.currentPageNumber,
        onChange = { backgroundType, background ->
            val current = state.pageView.pageFromDb ?: return@BackgroundSelector
            val updatedPage = if (background == null) current.copy(backgroundType = backgroundType)
            else current.copy(background = background, backgroundType = backgroundType)
            state.pageView.updatePageSettings(updatedPage)
            scope.launch { CanvasEventBus.refreshUi.emit(Unit) }
        }
    ) {
        state.menuStates.isBackgroundSelectorModalOpen = false
    }
}

/**
 * The editor row: the rail's gutter on one side, the page area ([content], which takes the rest) on the other.
 * The gutter has the same width in every state, so the page area never changes size.
 */
@Composable
fun RailRow(
    side: ToolbarSide,
    rail: @Composable () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        if (side == ToolbarSide.Left) rail()
        content()
        if (side == ToolbarSide.Right) rail()
    }
}
