package com.ethran.notable.planner

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Days the user wrote on, as an in-memory snapshot. The page drawing only reads it, it never touches the database:
 * whoever knows the activity (see [PlannerWritingDays]) fills the snapshot and redraws the page.
 */
interface WritingDays {
    fun hasInk(day: LocalDate): Boolean
}

/** Default snapshot: nothing written, no dots. */
object NoWritingDays : WritingDays {
    override fun hasInk(day: LocalDate) = false
}

/** The snapshot the month, quarter and year pages draw their dots from. Replace [current] to switch dots on. */
object PlannerWritingDays {
    @Volatile var current: WritingDays = NoWritingDays
}

/** The three zoom levels above the week. Each has its own notebook and its own number of focus lines. */
enum class PlannerLevel(val prefix: String, val focusKind: String) {
    Month(PlannerWeeks.MONTH_PREFIX, "month"),
    Quarter(PlannerWeeks.QUARTER_PREFIX, "quarter"),
    Year(PlannerWeeks.YEAR_PREFIX, "year");

    companion object {
        fun of(background: String): PlannerLevel? = entries.firstOrNull { background.startsWith(it.prefix) }
    }
}

/** A rectangle in page pixels. */
data class PageRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
}

/** What a tap on a month, quarter or year page does. */
sealed interface PageTap {
    data class OpenDay(val day: LocalDate) : PageTap
    data class OpenWeek(val monday: LocalDate) : PageTap
    data class OpenMonth(val month: YearMonth) : PageTap
    data class OpenQuarter(val year: Int, val quarter: Int) : PageTap
    data class OpenYear(val year: Int) : PageTap
    data class AddFocusLine(val level: PlannerLevel) : PageTap
}

data class TapZone(val rect: PageRect, val tap: PageTap)

/**
 * The `‹ PREVIOUS | THIS MONTH | NEXT ›` pill in the header of the month, quarter and year pages. Its three parts have
 * fixed widths (the drawing shrinks a long label to fit), so the pill sits in the same place in every language and
 * never meets the title on its left or the date block on its right.
 */
data class NavPill(val previous: PageRect, val current: PageRect, val next: PageRect) {
    val outline get() = PageRect(previous.left, previous.top, next.right, next.bottom)
}

/** "Focus" block: a label with a round + button on its right and [lines] ruled writing lines. */
data class FocusBlock(val level: PlannerLevel, val top: Float, val left: Float, val right: Float, val lines: Int) {
    val height get() = PlannerPagesLayout.focusHeight(lines)
    val bottom get() = top + height
    /** y of the rule under writing line [k] (0-based). */
    fun ruleY(k: Int) = top + PlannerPagesLayout.FOCUS_FIRST + k * PlannerPagesLayout.FOCUS_STEP
    val linesLeft get() = PlannerPagesLayout.FOCUS_LINES_X
    val labelLeft get() = left
    val plusCenterX get() = left + PlannerPagesLayout.FOCUS_LABEL_W + 38f
    val plusCenterY get() = top + 48f
    val canAdd get() = lines < PlannerFocusLines.MAX
    /** Tap zone of the + button; it ends before the writing lines start, and does not exist at the maximum. */
    val plusZone: PageRect? get() = if (!canAdd) null else PageRect(
        plusCenterX - 40f, top + 8f, min(plusCenterX + 40f, PlannerPagesLayout.FOCUS_LINES_X - 6f), top + 88f
    )
    val taps: List<TapZone> get() = listOfNotNull(plusZone?.let { TapZone(it, PageTap.AddFocusLine(level)) })
}

/** One day of the big month grid. */
data class DayCell(val date: LocalDate, val inMonth: Boolean, val rect: PageRect, val number: PageRect)

/** One week row of the month grid with its ISO week number on the left. */
data class WeekRow(val monday: LocalDate, val isoWeek: Int, val rect: PageRect, val weekZone: PageRect, val cells: List<DayCell>)

/** Month page: focus block, weekday header and always six week rows. */
data class MonthLayout(
    val month: YearMonth, val focus: FocusBlock, val weekdayRow: PageRect, val gridLeft: Float, val gridRight: Float,
    val gridTop: Float, val gridBottom: Float, val rows: List<WeekRow>, val nav: List<TapZone> = emptyList()
) {
    val taps: List<TapZone>
        get() = nav + focus.taps + rows.flatMap { r ->
            listOf(TapZone(r.weekZone, PageTap.OpenWeek(r.monday))) + r.cells.map { TapZone(it.number, PageTap.OpenDay(it.date)) }
        }

    fun tapAt(x: Float, y: Float): PageTap? = taps.firstOrNull { it.rect.contains(x, y) }?.tap
}

data class MiniCell(val date: LocalDate, val inMonth: Boolean, val cx: Float, val cy: Float)
data class MiniRow(val monday: LocalDate, val isoWeek: Int, val cy: Float, val cells: List<MiniCell>)

/** A small calendar of one month (quarter and year pages). */
data class MiniCalendar(
    val month: YearMonth, val left: Float, val top: Float, val width: Float, val titleBaseline: Float, val titleZone: PageRect,
    val weekdayCy: Float, val columnX: List<Float>, val weekNumberX: Float?, val rows: List<MiniRow>, val pitch: Float
)

/** One month of the quarter page: its calendar, a vertical band for the Goals area and the rules in it. */
data class QuarterMonth(
    val month: YearMonth, val block: PageRect, val calendar: MiniCalendar, val goalsLabelBaseline: Float, val goalsLeft: Float,
    val goalsRight: Float, val goalRules: List<Float>
)

data class QuarterLayout(val quarter: PlannerWeeks.YearQuarter, val portrait: Boolean, val focus: FocusBlock, val months: List<QuarterMonth>, val areaTop: Float, val areaBottom: Float, val nav: List<TapZone> = emptyList()) {
    val taps: List<TapZone>
        get() = nav + focus.taps + months.map { TapZone(it.calendar.titleZone, PageTap.OpenMonth(it.month)) }

    fun tapAt(x: Float, y: Float): PageTap? = taps.firstOrNull { it.rect.contains(x, y) }?.tap
}

/** One of the 12 months of the year page. */
data class YearMonthCell(val month: YearMonth, val rect: PageRect, val calendar: MiniCalendar, val quarterTag: PageRect?)

data class YearLayout(
    val year: Int, val portrait: Boolean, val columns: Int, val rowsCount: Int, val gridLeft: Float, val gridTop: Float, val gridBottom: Float,
    val cells: List<YearMonthCell>, val quarterStripes: List<PageRect>, val focus: FocusBlock, val nav: List<TapZone> = emptyList()
) {
    val taps: List<TapZone>
        get() = nav + focus.taps + cells.map { TapZone(it.calendar.titleZone, PageTap.OpenMonth(it.month)) } +
            cells.mapNotNull { c -> c.quarterTag?.let { TapZone(it, PageTap.OpenQuarter(year, (c.month.monthValue - 1) / 3 + 1)) } } +
            quarterStripes.mapIndexed { i, r -> TapZone(r, PageTap.OpenQuarter(year, i + 1)) }

    fun tapAt(x: Float, y: Float): PageTap? = taps.firstOrNull { it.rect.contains(x, y) }?.tap
}

/**
 * Geometry of the month, quarter and year pages in page pixels. Pure: the drawing and the tap layer both read it, so
 * what is drawn and what is tappable cannot drift apart. The top of the content is always [PlannerLayout.ROWS_TOP].
 */
object PlannerPagesLayout {
    const val SIDE = 100f
    const val BOTTOM = 80f

    // Focus block
    const val FOCUS_STEP = 60f
    const val FOCUS_FIRST = 64f
    const val FOCUS_TAIL = 20f
    const val FOCUS_LABEL_W = 170f
    const val FOCUS_LINES_X = 358f

    // Month grid
    const val WEEKDAY_H = 57f
    const val WEEKNO_W = 88f
    const val ROWS = 6

    fun isPortrait(w: Float, h: Float) = h >= w
    fun right(w: Float) = w - PlannerLayout.RIGHT_MARGIN

    fun focusHeight(lines: Int) = FOCUS_FIRST + (lines - 1) * FOCUS_STEP + FOCUS_TAIL

    // Header pill
    const val NAV_LEFT = 830f
    const val NAV_TOP = 52f
    const val NAV_H = 53f
    const val NAV_PREVIOUS_W = 160f
    const val NAV_THIS_W = 220f
    const val NAV_NEXT_W = 140f
    /** The tap zones are taller than the drawn pill, a finger is bigger than 53 pixels. */
    const val NAV_TAP_PAD = 14f

    fun navPill(): NavPill {
        val a = NAV_LEFT
        val b = a + NAV_PREVIOUS_W
        val c = b + NAV_THIS_W
        val d = c + NAV_NEXT_W
        return NavPill(PageRect(a, NAV_TOP, b, NAV_TOP + NAV_H), PageRect(b, NAV_TOP, c, NAV_TOP + NAV_H), PageRect(c, NAV_TOP, d, NAV_TOP + NAV_H))
    }

    private fun navZones(previous: PageTap, current: PageTap, next: PageTap): List<TapZone> {
        val pill = navPill()
        fun zone(r: PageRect, tap: PageTap) = TapZone(PageRect(r.left, r.top - NAV_TAP_PAD, r.right, r.bottom + NAV_TAP_PAD), tap)
        return listOf(zone(pill.previous, previous), zone(pill.current, current), zone(pill.next, next))
    }

    /** Previous month, the month [today] is in, next month. */
    fun monthNav(month: YearMonth, today: LocalDate): List<TapZone> =
        navZones(PageTap.OpenMonth(month.minusMonths(1)), PageTap.OpenMonth(YearMonth.from(today)), PageTap.OpenMonth(month.plusMonths(1)))

    /** Previous quarter, the quarter [today] is in, next quarter. */
    fun quarterNav(quarter: PlannerWeeks.YearQuarter, today: LocalDate): List<TapZone> {
        fun open(q: PlannerWeeks.YearQuarter) = PageTap.OpenQuarter(q.year, q.quarter)
        return navZones(open(quarter.plusQuarters(-1)), open(PlannerWeeks.YearQuarter.of(today)), open(quarter.plusQuarters(1)))
    }

    /** Previous year, the year [today] is in, next year. */
    fun yearNav(year: Int, today: LocalDate): List<TapZone> =
        navZones(PageTap.OpenYear(year - 1), PageTap.OpenYear(today.year), PageTap.OpenYear(year + 1))

    /** True when the page of this period is the one that holds [today]: the "this …" part of the pill is then drawn black. */
    fun isCurrent(month: YearMonth, today: LocalDate) = month == YearMonth.from(today)
    fun isCurrent(quarter: PlannerWeeks.YearQuarter, today: LocalDate) = quarter == PlannerWeeks.YearQuarter.of(today)
    fun isCurrent(year: Int, today: LocalDate) = year == today.year

    // ---- calendar arithmetic ------------------------------------------------------------------------------------------

    /** Monday of the week the first day of [month] is in. */
    fun gridStart(month: YearMonth): LocalDate = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Weeks (Monday first) that show [month]: 4 to 6. */
    fun weeksNeeded(month: YearMonth): Int {
        val offset = month.atDay(1).dayOfWeek.value - 1
        return (offset + month.lengthOfMonth() + 6) / 7
    }

    /** The month page always shows six rows of seven days, so every month has the same grid. */
    fun monthWeeks(month: YearMonth): List<List<LocalDate>> = weeks(month, ROWS)

    fun miniWeeks(month: YearMonth): List<List<LocalDate>> = weeks(month, weeksNeeded(month))

    private fun weeks(month: YearMonth, rows: Int): List<List<LocalDate>> {
        val start = gridStart(month)
        return (0 until rows).map { r -> (0 until 7).map { start.plusDays((r * 7 + it).toLong()) } }
    }

    /** Share of [start]..[end] (both included) that is over at the end of [today], 0..100. */
    fun periodPercent(start: LocalDate, end: LocalDate, today: LocalDate): Int {
        if (today.isBefore(start)) return 0
        if (!today.isBefore(end)) return 100
        val total = ChronoUnit.DAYS.between(start, end) + 1
        val done = ChronoUnit.DAYS.between(start, today) + 1
        return (done * 100f / total).roundToInt().coerceIn(0, 100)
    }

    // ---- focus block --------------------------------------------------------------------------------------------------

    fun focus(level: PlannerLevel, top: Float, w: Float, lines: Int): FocusBlock =
        FocusBlock(level, top, SIDE, right(w), PlannerFocusLines.clamp(lines))

    // ---- month page ---------------------------------------------------------------------------------------------------

    fun month(month: YearMonth, w: Float, h: Float, focusLines: Int = PlannerFocusLines.DEFAULT, today: LocalDate = LocalDate.now()): MonthLayout {
        val focus = focus(PlannerLevel.Month, PlannerLayout.ROWS_TOP, w, focusLines)
        val right = right(w)
        val gridLeft = SIDE + WEEKNO_W
        val weekdayRow = PageRect(gridLeft, focus.bottom, right, focus.bottom + WEEKDAY_H)
        val gridTop = weekdayRow.bottom
        val gridBottom = max(gridTop + ROWS * 100f, h - BOTTOM)
        val rowH = (gridBottom - gridTop) / ROWS
        val colW = (right - gridLeft) / 7f
        val rows = monthWeeks(month).mapIndexed { r, days ->
            val top = gridTop + r * rowH
            val cells = days.mapIndexed { c, d ->
                val left = gridLeft + c * colW
                DayCell(
                    d, YearMonth.from(d) == month, PageRect(left, top, left + colW, top + rowH),
                    PageRect(left + 6f, top + 6f, left + 90f, top + 64f)
                )
            }
            WeekRow(days.first(), PlannerWeeks.weekNumber(days.first()), PageRect(SIDE, top, right, top + rowH), PageRect(SIDE, top + 6f, gridLeft - 4f, top + 64f), cells)
        }
        return MonthLayout(month, focus, weekdayRow, gridLeft, right, gridTop, gridBottom, rows, monthNav(month, today))
    }

    // ---- mini calendars -----------------------------------------------------------------------------------------------

    /**
     * A small calendar whose top left corner is ([left], [top]). Title band [titleH], weekday letters [weekdayH], then
     * the weeks every [pitch] pixels. With [weekNumbers] a narrow column on the left carries the ISO week numbers.
     */
    fun miniCalendar(
        month: YearMonth, left: Float, top: Float, width: Float, titleH: Float, weekdayH: Float, pitch: Float,
        weekNumbers: Boolean, titleW: Float
    ): MiniCalendar {
        val weekNoW = if (weekNumbers) 54f else 0f
        val colW = (width - weekNoW) / 7f
        val firstCx = left + weekNoW + colW / 2f
        val columnX = (0 until 7).map { firstCx + it * colW }
        val rows = miniWeeks(month).mapIndexed { r, days ->
            val cy = top + titleH + weekdayH + pitch * (r + 0.5f)
            MiniRow(days.first(), PlannerWeeks.weekNumber(days.first()), cy,
                days.mapIndexed { c, d -> MiniCell(d, YearMonth.from(d) == month, columnX[c], cy) })
        }
        return MiniCalendar(
            month, left, top, width, top + titleH * 0.82f, PageRect(left, top, left + min(titleW, width), top + titleH),
            top + titleH + weekdayH / 2f, columnX, if (weekNumbers) left + weekNoW * 0.35f else null, rows, pitch
        )
    }

    // ---- quarter page -------------------------------------------------------------------------------------------------

    const val Q_TITLE_H = 66f
    const val Q_WEEKDAY_H = 54f
    const val Q_PITCH = 60f
    const val Q_GOALS_STEP = 60f

    fun quarter(quarter: PlannerWeeks.YearQuarter, w: Float, h: Float, focusLines: Int = PlannerFocusLines.DEFAULT, today: LocalDate = LocalDate.now()): QuarterLayout {
        val portrait = isPortrait(w, h)
        val focus = focus(PlannerLevel.Quarter, PlannerLayout.ROWS_TOP, w, focusLines)
        val right = right(w)
        val areaTop = focus.bottom
        val areaBottom = max(areaTop + 600f, h - BOTTOM)
        val calendarH = Q_TITLE_H + Q_WEEKDAY_H + ROWS * Q_PITCH
        val months = quarter.months.mapIndexed { i, ym ->
            if (portrait) {
                val blockH = (areaBottom - areaTop) / 3f
                val top = areaTop + i * blockH
                val block = PageRect(SIDE, top, right, top + blockH)
                val calW = (right - SIDE) * 0.485f
                val cal = miniCalendar(ym, SIDE, top + 8f, calW, Q_TITLE_H, Q_WEEKDAY_H, Q_PITCH, true, 360f)
                val goalsLeft = SIDE + calW + 34f
                val first = top + 108f
                val count = max(1, floor((block.bottom - 30f - first) / Q_GOALS_STEP).toInt() + 1)
                QuarterMonth(ym, block, cal, top + 40f, goalsLeft, right, (0 until count).map { first + it * Q_GOALS_STEP })
            } else {
                val colW = (right - SIDE) / 3f
                val left = SIDE + i * colW
                val block = PageRect(left, areaTop, left + colW, areaBottom)
                val innerLeft = left + if (i == 0) 0f else 30f
                val innerW = colW - 60f
                val cal = miniCalendar(ym, innerLeft, areaTop + 8f, innerW, Q_TITLE_H, Q_WEEKDAY_H, Q_PITCH, true, 360f)
                val labelY = areaTop + 8f + calendarH + 36f
                val first = labelY + 38f
                val count = max(1, floor((areaBottom - 20f - first) / Q_GOALS_STEP).toInt() + 1)
                QuarterMonth(ym, block, cal, labelY, innerLeft, innerLeft + innerW, (0 until count).map { first + it * Q_GOALS_STEP })
            }
        }
        return QuarterLayout(quarter, portrait, focus, months, areaTop, areaBottom, quarterNav(quarter, today))
    }

    // ---- year page ----------------------------------------------------------------------------------------------------

    const val Y_STRIPE_W = 70f
    const val Y_TITLE_H = 54f
    const val Y_WEEKDAY_H = 44f
    const val Y_PITCH = 46f

    fun year(year: Int, w: Float, h: Float, focusLines: Int = PlannerFocusLines.DEFAULT, today: LocalDate = LocalDate.now()): YearLayout {
        val portrait = isPortrait(w, h)
        val right = right(w)
        val n = PlannerFocusLines.clamp(focusLines)
        val focusTop = h - BOTTOM - focusHeight(n)
        val focus = FocusBlock(PlannerLevel.Year, focusTop, SIDE, right, n)
        val gridTop = PlannerLayout.ROWS_TOP
        val gridBottom = max(gridTop + 600f, focusTop - 4f)
        val cols = if (portrait) 3 else 4
        val rowCount = if (portrait) 4 else 3
        val gridLeft = if (portrait) SIDE + Y_STRIPE_W else SIDE
        val cellW = (right - gridLeft) / cols
        val cellH = (gridBottom - gridTop) / rowCount
        val pitch = min(Y_PITCH, (cellH - Y_TITLE_H - Y_WEEKDAY_H - 12f) / ROWS)
        val cells = (1..12).map { m ->
            val ym = YearMonth.of(year, m)
            val r = (m - 1) / cols
            val c = (m - 1) % cols
            val rect = PageRect(gridLeft + c * cellW, gridTop + r * cellH, gridLeft + (c + 1) * cellW, gridTop + (r + 1) * cellH)
            val cal = miniCalendar(ym, rect.left + 24f, rect.top + 8f, cellW - 48f, Y_TITLE_H, Y_WEEKDAY_H, pitch, false, 190f)
            val firstOfQuarter = (m - 1) % 3 == 0
            val tag = if (!portrait && firstOfQuarter) PageRect(rect.right - 120f, rect.top + 8f, rect.right - 14f, rect.top + 8f + Y_TITLE_H) else null
            YearMonthCell(ym, rect, cal, tag)
        }
        val stripes = if (portrait) (0 until 4).map { PageRect(SIDE, gridTop + it * cellH, gridLeft, gridTop + (it + 1) * cellH) } else emptyList()
        return YearLayout(year, portrait, cols, rowCount, gridLeft, gridTop, gridBottom, cells, stripes, focus, yearNav(year, today))
    }
}
