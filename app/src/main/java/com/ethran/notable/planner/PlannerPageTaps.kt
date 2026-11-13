package com.ethran.notable.planner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.ethran.notable.editor.canvas.CanvasEventBus
import com.ethran.notable.editor.state.EditorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.ethran.notable.activity.ActivityWritingDays

/**
 * Where a [PageTap] leads: the page to open (created when missing) or null when it only changes the open page.
 * Kept apart from the composable so the mapping can be tested without a device.
 */
internal fun PageTap.target(service: PlannerService): (suspend () -> PlannerService.Target)? = when (this) {
    is PageTap.OpenDay -> suspend { service.ensureDay(day) }
    is PageTap.OpenWeek -> suspend { service.ensureWeek(monday) }
    is PageTap.OpenMonth -> suspend { service.ensureMonth(month.atDay(1)) }
    is PageTap.OpenQuarter -> suspend { service.ensureQuarter(PlannerWeeks.YearQuarter(year, quarter).start) }
    is PageTap.OpenYear -> suspend { service.ensureYear(LocalDate.of(year, 1, 1)) }
    is PageTap.AddFocusLine -> null
}

/** True when this tap would open the page that is already open (the "this month" part of the pill on the current month). */
internal fun PageTap.opens(background: String): Boolean = when (this) {
    is PageTap.OpenMonth -> PlannerWeeks.parseMonth(background) == month
    is PageTap.OpenQuarter -> PlannerWeeks.parseQuarter(background) == PlannerWeeks.YearQuarter(year, quarter)
    is PageTap.OpenYear -> PlannerWeeks.parseYear(background) == year
    else -> false
}

/** The tap zones of the page with this background at this page size; empty for any other page (including unknown keys). */
fun pageTapZones(background: String?, width: Float, height: Float, month: Int = PlannerFocusLines.DEFAULT, quarter: Int = PlannerFocusLines.DEFAULT, year: Int = PlannerFocusLines.DEFAULT, today: LocalDate = LocalDate.now()): List<TapZone> {
    if (background == null) return emptyList()
    PlannerWeeks.parseMonth(background)?.let { return PlannerPagesLayout.month(it, width, height, month, today).taps }
    PlannerWeeks.parseQuarter(background)?.let { return PlannerPagesLayout.quarter(it, width, height, quarter, today).taps }
    PlannerWeeks.parseYear(background)?.let { return PlannerPagesLayout.year(it, width, height, year, today).taps }
    return emptyList()
}

/**
 * Invisible tap layer over a month, quarter or year page: date numbers, week numbers, month titles and Q labels open
 * that page, the round + button adds a focus line, the pill in the header goes to the previous, this or next period. It draws nothing and covers none of the writing areas. On every other
 * page it does nothing. [onOpen] is the editor's planner navigation: pages of other notebooks open with the editor
 * route, so Back returns to the page you came from.
 */
@Composable
fun PlannerPageTaps(editorState: EditorState, plannerService: PlannerService, onOpen: (suspend () -> PlannerService.Target) -> Unit) {
    val appRepository = editorState.appRepository
    val page = editorState.pageView
    val pageId = editorState.currentPageId
    val scope = rememberCoroutineScope()

    var background by remember(pageId) { mutableStateOf<String?>(null) }
    LaunchedEffect(pageId) {
        background = withContext(Dispatchers.IO) { runCatching { appRepository.pageRepository.getById(pageId)?.background }.getOrNull() }
    }
    val level = background?.let(PlannerLevel::of)
    // Compose copy of the stored counts: the tap zones must follow a new line at once; the drawing reads the same numbers from the snapshot.
    var counts by remember { mutableStateOf(PlannerFocusLines.counts) }
    val repository = remember { PlannerFocusRepository(KvProxyFocusStore(appRepository.kvProxy)) }

    fun redraw() {
        scope.launch {
            page.applyZoomAndRedraw(page.zoomLevel.value)
            CanvasEventBus.refreshUiImmediately.emit(Unit)
        }
    }

    LaunchedEffect(level) {
        if (level == null) return@LaunchedEffect
        val changed = withContext(Dispatchers.IO) { runCatching { repository.load() }.getOrDefault(false) }
        counts = PlannerFocusLines.counts
        if (changed) redraw()
    }

    // Dots of the days written on: refresh the in-memory snapshot on page open; redraw only when it changed.
    val context = LocalContext.current
    LaunchedEffect(level, pageId) {
        if (level == null) return@LaunchedEffect
        if (ActivityWritingDays.refresh(context)) redraw()
    }

    if (level == null) return
    val bg = background ?: return

    fun addLine(which: PlannerLevel) {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { repository.add(which) } }
            counts = PlannerFocusLines.counts
            redraw()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val today = remember(bg) { LocalDate.now() }
        val zones = pageTapZones(
            bg, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(),
            month = counts[PlannerLevel.Month] ?: PlannerFocusLines.DEFAULT,
            quarter = counts[PlannerLevel.Quarter] ?: PlannerFocusLines.DEFAULT,
            year = counts[PlannerLevel.Year] ?: PlannerFocusLines.DEFAULT,
            today = today
        )
        with(density) {
            for (zone in zones) {
                val r = zone.rect
                Box(
                    Modifier
                        .offset(r.left.toDp(), r.top.toDp())
                        .size(r.width.toDp(), r.height.toDp())
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            val tap = zone.tap
                            if (tap.opens(bg)) return@clickable   // already on this page
                            if (tap is PageTap.AddFocusLine) addLine(tap.level) else tap.target(plannerService)?.let(onOpen)
                        }
                )
            }
        }
    }
}
