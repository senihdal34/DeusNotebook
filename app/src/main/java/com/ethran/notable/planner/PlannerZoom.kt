package com.ethran.notable.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.R
import com.ethran.notable.data.AppRepository
import com.ethran.notable.i18n.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The levels of the planner, from the coarsest to the finest page. */
enum class ZoomLevel { YEAR, QUARTER, MONTH, WEEK, DAY }

/** First and last day of the page that shows [date] at [level]. */
fun zoomRange(level: ZoomLevel, date: LocalDate): ClosedRange<LocalDate> = when (level) {
    ZoomLevel.DAY -> date..date
    ZoomLevel.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it..it.plusDays(6) }
    ZoomLevel.MONTH -> date.withDayOfMonth(1).let { it..it.plusMonths(1).minusDays(1) }
    ZoomLevel.QUARTER -> date.withDayOfMonth(1).withMonth((date.monthValue - 1) / 3 * 3 + 1).let { it..it.plusMonths(3).minusDays(1) }
    ZoomLevel.YEAR -> date.withDayOfYear(1).let { it..it.plusYears(1).minusDays(1) }
}

/**
 * The level and the first date of a planner page from its `background` key, or null for any other page:
 * `planner-day:YYYY-MM-DD`, `planner-week:YYYY-MM-DD` (Monday), `planner-month:YYYY-MM`, `planner-quarter:YYYY-Qn`,
 * `planner-year:YYYY`. Tolerant about spaces and letter case; a malformed value gives null.
 */
fun levelAndDate(background: String): Pair<ZoomLevel, LocalDate>? {
    val colon = background.indexOf(':')
    if (colon < 0) return null
    val key = background.substring(0, colon).trim().lowercase()
    val value = background.substring(colon + 1).trim()
    return runCatching {
        when (key) {
            "planner-day" -> ZoomLevel.DAY to LocalDate.parse(value)
            "planner-week" -> ZoomLevel.WEEK to LocalDate.parse(value)
            "planner-month" -> {
                val m = Regex("""(\d{4})-(\d{1,2})(?:-\d{1,2})?""").matchEntire(value) ?: return null
                ZoomLevel.MONTH to LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), 1)
            }
            "planner-quarter" -> {
                val m = Regex("""(\d{4})-[qQ]([1-4])""").matchEntire(value) ?: return null
                ZoomLevel.QUARTER to LocalDate.of(m.groupValues[1].toInt(), (m.groupValues[2].toInt() - 1) * 3 + 1, 1)
            }
            "planner-year" -> {
                val m = Regex("""(\d{4})""").matchEntire(value) ?: return null
                ZoomLevel.YEAR to LocalDate.of(m.groupValues[1].toInt(), 1, 1)
            }
            else -> null
        }
    }.getOrNull()
}

/** Pass this as the focus date when nothing is remembered. */
val NO_FOCUS: LocalDate = LocalDate.MIN

/**
 * A date inside the page of level [to] that the user zooms to from the page [from] showing [fromDate].
 * The focus date is the one the user is looking at: on a day page it is that day; on any other page it is the
 * remembered [focus] when that lies inside the page, else [today] when that does, else the page's first day.
 * Zooming out and in again reuses the focus, so Day 12 Nov -> Month -> Day returns to 12 Nov.
 * A week without a remembered focus date ([NO_FOCUS]) belongs to the month, quarter and year of its Thursday (ISO),
 * so the week of Dec 29 2025 zooms out to January 2026.
 */
fun targetDate(from: ZoomLevel, fromDate: LocalDate, to: ZoomLevel, focus: LocalDate, today: LocalDate): LocalDate {
    if (from == ZoomLevel.DAY) return fromDate
    val range = zoomRange(from, fromDate)
    return when {
        focus in range -> focus
        from == ZoomLevel.WEEK && to < ZoomLevel.WEEK -> range.start.with(DayOfWeek.THURSDAY)
        today in range -> today
        else -> range.start
    }
}

/**
 * Finds and opens the planner page of a level. [ensure] creates the page when it is missing; the real one calls
 * PlannerService, tests pass a fake. The focus date is remembered only after the page exists.
 */
class PlannerZoomNavigator(
    private val ensure: suspend (ZoomLevel, LocalDate) -> PlannerService.Target,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val onFocus: (LocalDate) -> Unit = { PlannerFocus.date = it },
    private val focus: () -> LocalDate? = { PlannerFocus.date },
) {
    constructor(service: PlannerService) : this(ensure = { level, date ->
        when (level) {
            ZoomLevel.DAY -> service.ensureDay(date)
            ZoomLevel.WEEK -> service.ensureWeek(PlannerWeeks.mondayOf(date))
            ZoomLevel.MONTH -> service.ensureMonth(date)
            ZoomLevel.QUARTER -> service.ensureQuarter(date)
            ZoomLevel.YEAR -> service.ensureYear(date)
        }
    })

    /** The page of level [to] around the focus date, zooming from the page [from] showing [fromDate]. */
    suspend fun go(from: ZoomLevel, fromDate: LocalDate, to: ZoomLevel): PlannerService.Target {
        val now = today()
        val date = targetDate(from, fromDate, to, focus() ?: NO_FOCUS, now)
        return ensure(to, date).also { onFocus(date) }
    }
}

/** The strip's own page-pixel layout is [PlannerLayout.ZOOM_H]; this is its look: a hairline pill, the current level filled black. */
@Composable
fun PlannerZoomStrip(current: ZoomLevel?, onLevel: (ZoomLevel) -> Unit, modifier: Modifier = Modifier) {
    val ink = Color(0xFF111111)
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(50)).border(1.5.dp, ink, RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val levels = ZoomLevel.values()
        for ((i, level) in levels.withIndex()) {
            val on = level == current
            Box(
                Modifier.height(40.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onLevel(level) }
                    .background(if (on) ink else Color.Transparent).padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(zoomLabel(level)).uppercase(AppStrings.locale),
                    fontFamily = FontFamily.SansSerif, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.6.sp, color = if (on) Color.White else ink, maxLines = 1
                )
            }
            if (i < levels.lastIndex && !on && levels[i + 1] != current) Box(Modifier.width(1.dp).height(40.dp).background(Color(0xFF999999)))
        }
    }
}

private fun zoomLabel(level: ZoomLevel): Int = when (level) {
    ZoomLevel.YEAR -> R.string.zoom_year
    ZoomLevel.QUARTER -> R.string.zoom_quarter
    ZoomLevel.MONTH -> R.string.zoom_month
    ZoomLevel.WEEK -> R.string.zoom_week
    ZoomLevel.DAY -> R.string.zoom_day
}

/**
 * The zoom strip of the editor: reads the open page's level, draws the strip at y = HEADER_H + TABS_H (between the
 * tab bar and the page content, never over a tap zone) and calls [onGo] with a page finder when a level is tapped.
 * Draws nothing on pages that are not planner pages.
 */
@Composable
fun PlannerZoomHost(pageId: String, appRepository: AppRepository, service: PlannerService, onGo: (suspend () -> PlannerService.Target) -> Unit) {
    var page by remember(pageId) { mutableStateOf<Pair<ZoomLevel, LocalDate>?>(null) }
    LaunchedEffect(pageId) {
        val background = withContext(Dispatchers.IO) { runCatching { appRepository.pageRepository.getById(pageId)?.background }.getOrNull() }
        page = background?.let(::levelAndDate)
        // Opening a day page is the clearest focus there is (a day tap, the week tool, a swipe).
        page?.takeIf { it.first == ZoomLevel.DAY }?.let { PlannerFocus.date = it.second }
    }
    val (level, date) = page ?: return
    val navigator = remember(service) { PlannerZoomNavigator(service) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        with(LocalDensity.current) {
            Box(
                Modifier.offset(0.dp, (PlannerLayout.HEADER_H + PlannerLayout.TABS_H).toDp())
                    .fillMaxWidth().height(PlannerLayout.ZOOM_H.toDp()),
                contentAlignment = Alignment.Center
            ) {
                PlannerZoomStrip(current = level, onLevel = { to -> if (to != level) onGo { navigator.go(level, date, to) } })
            }
        }
    }
}
