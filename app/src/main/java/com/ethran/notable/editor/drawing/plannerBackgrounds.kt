package com.ethran.notable.editor.drawing

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import com.ethran.notable.planner.FocusBlock
import com.ethran.notable.planner.MiniCalendar
import com.ethran.notable.planner.PlannerDone
import com.ethran.notable.planner.PlannerFocusLines
import com.ethran.notable.planner.PlannerLayout
import com.ethran.notable.planner.PlannerLevel
import com.ethran.notable.planner.PlannerNames
import com.ethran.notable.planner.PlannerPagesLayout
import com.ethran.notable.planner.PlannerWeeks
import com.ethran.notable.planner.PlannerWritingDays
import com.ethran.notable.planner.WritingDays
import java.time.LocalDate
import java.time.YearMonth

// Paper-planner look for the e-ink screen: black rules, a day column on the left, check boxes.
// All sizes are in page pixels (device pixels at zoom 1); the screen is about 1860 x 2480.

private const val HEADER_H = 150f
private val loc get() = com.ethran.notable.i18n.AppStrings.locale
private fun text(@androidx.annotation.StringRes id: Int, vararg args: Any) = com.ethran.notable.i18n.AppStrings.get(id, *args)

private val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
private val sansBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

private fun paint(color: Int, width: Float = 1f, style: Paint.Style = Paint.Style.FILL, size: Float = 0f, face: Typeface? = null) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; strokeWidth = width; this.style = style
        if (size > 0f) textSize = size
        if (face != null) typeface = face
    }

private val ink = paint(Color.BLACK, 3f)
private val inkThin = paint(Color.BLACK, 2f)
private val guide = paint(Color.GRAY, 1.5f)
private val box = paint(Color.BLACK, 3f, Paint.Style.STROKE)
private val fillInk = paint(Color.BLACK)
private val titleP = paint(Color.BLACK, size = 67f, face = serifBold).apply { letterSpacing = -0.03f }
private val titleDayP = paint(Color.BLACK, size = 54f, face = serifBold).apply { letterSpacing = -0.03f }   // day names are long
private val summary = paint(Color.DKGRAY, size = 23f, face = sansBold).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.14f }
private val summaryBold = paint(Color.BLACK, size = 30f, face = sansBold).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.1f }
private val subP = paint(Color.DKGRAY, size = 34f, face = sansBold).apply { textAlign = Paint.Align.RIGHT; letterSpacing = 0.08f }
private val dayName = paint(Color.BLACK, size = 30f, face = sansBold).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.12f }
private val dayNameInv = paint(Color.WHITE, size = 30f, face = sansBold).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.12f }
private val dayNum = paint(Color.BLACK, size = 80f, face = serifBold).apply { textAlign = Paint.Align.CENTER }
private val dayNumInv = paint(Color.WHITE, size = 80f, face = serifBold).apply { textAlign = Paint.Align.CENTER }
private val sectionP = paint(Color.BLACK, size = 52f, face = serifBold)

private val strike = paint(Color.DKGRAY, 4f)
private val tick = paint(Color.WHITE, 4f)

private fun checkbox(canvas: Canvas, x: Float, bottom: Float, size: Float = 34f, done: Boolean = false) {
    if (done) {
        canvas.drawRect(x, bottom - size, x + size, bottom, fillInk)
        canvas.drawLine(x + size * 0.2f, bottom - size * 0.5f, x + size * 0.42f, bottom - size * 0.22f, tick)
        canvas.drawLine(x + size * 0.42f, bottom - size * 0.22f, x + size * 0.8f, bottom - size * 0.78f, tick)
    } else canvas.drawRect(x, bottom - size, x + size, bottom, box)
}

/** Check boxes and writing lines of one day starting at [y0]; ticked lines get a strike through the text. */
private fun drawDayLines(canvas: Canvas, y0: Float, lines: Int, step: Float, w: Float, done: Set<Int>, lastLineDrawn: Boolean) {
    val x = PlannerLayout.BOX_X
    for (k in 1..lines) {
        val ly = y0 + k * step
        val isDone = k in done
        checkbox(canvas, x, PlannerLayout.boxBottom(y0, k, step), PlannerLayout.boxSize(step), isDone)
        if (isDone) canvas.drawLine(x + step * 0.46f + 14f, ly - step * 0.38f, w - 60f, ly - step * 0.38f, strike)
        if (k < lines || lastLineDrawn) canvas.drawLine(x, ly, w - 40f, ly, guide)
    }
}

// Logo in the header, loaded once. Sizes match the Home header (logo 52 dp, title 36 sp, small block on the right)
// so moving between Home and the planner feels like one page.
private var logoBitmap: android.graphics.Bitmap? = null
private fun logo(context: android.content.Context): android.graphics.Bitmap? {
    logoBitmap?.let { return it }
    return runCatching {
        val src = android.graphics.BitmapFactory.decodeResource(context.resources, com.ethran.notable.R.drawable.logo_deusnote)
        android.graphics.Bitmap.createScaledBitmap(src, 98, 98, true)
    }.getOrNull().also { logoBitmap = it }
}

private fun drawHeader(
    context: android.content.Context, canvas: Canvas, w: Float, oy: Float, title: String, line1: String, line2: String, percent: Int,
    withLogo: Boolean = true, complete: String? = null
) {
    // A day page has the back button (drawn by the editor) in the logo's place.
    if (withLogo) logo(context)?.let { canvas.drawBitmap(it, 0f, 26f + oy, null) }
    canvas.drawText(title, if (withLogo) 132f else 290f, 97f + oy, if (withLogo) titleP else titleDayP)
    val right = w - PlannerLayout.RIGHT_MARGIN
    canvas.drawText(line1, right, 48f + oy, summary)
    canvas.drawText(line2, right, 80f + oy, summary)
    canvas.drawText((complete ?: text(com.ethran.notable.R.string.planner_year_complete, percent)).uppercase(loc), right, 112f + oy, summary)
    val barW = 280f
    canvas.drawRect(right - barW, 122f + oy, right, 132f + oy, box)
    canvas.drawRect(right - barW, 122f + oy, right - barW + barW * percent / 100f, 132f + oy, fillInk)
    canvas.drawRect(0f, PlannerLayout.HEADER_H - 8f + oy, w, PlannerLayout.HEADER_H + oy, fillInk)
    canvas.drawRect(0f, PlannerLayout.ROWS_TOP - 3f + oy, w, PlannerLayout.ROWS_TOP + oy, fillInk)   // under the tab bar
}

/**
 * Weekly planner page: seven day rows that exactly fill the screen (the page does not scroll), every row with at
 * least five check-box lines. Tapping a date opens that day's page. Only the black label block marks today.
 */
fun drawPlannerWeekBg(
    context: android.content.Context, canvas: Canvas, scale: Float, monday: LocalDate, today: LocalDate = LocalDate.now()
) {
    val w = canvas.width / scale
    val h = canvas.height / scale
    canvas.drawColor(Color.WHITE)
    drawHeader(
        context, canvas, w, 0f, text(com.ethran.notable.R.string.planner_title),
        text(com.ethran.notable.R.string.planner_week_title, PlannerWeeks.weekOfYear(monday)).uppercase(loc), "${PlannerWeeks.rangeLabel(monday)} · ${monday.plusDays(3).year}".uppercase(loc),
        PlannerWeeks.yearPercent(today)
    )
    val layout = PlannerLayout.week(h)
    val rowH = layout.rowH
    val step = layout.step
    val labelW = PlannerLayout.LABEL_W
    for (i in 0 until 7) {
        val day = monday.plusDays(i.toLong())
        val y0 = layout.rowTop(i)
        val y1 = y0 + rowH
        drawDayLabel(canvas, labelW, y0, rowH, day, i, day == today)
        canvas.drawLine(0f, y1, w, y1, ink)
        drawDayLines(canvas, y0, layout.lines, step, w, PlannerDone.lines(day), lastLineDrawn = false)
    }
    canvas.drawLine(labelW, PlannerLayout.ROWS_TOP, labelW, PlannerLayout.ROWS_TOP + 7 * rowH, ink)
}

private fun drawDayLabel(canvas: Canvas, labelW: Float, y0: Float, rowH: Float, day: LocalDate, idx: Int, isToday: Boolean) {
    if (isToday) canvas.drawRect(0f, y0, labelW, y0 + rowH, fillInk)
    canvas.drawText(PlannerWeeks.dayName(idx).uppercase(loc), labelW / 2f, y0 + rowH * 0.26f, if (isToday) dayNameInv else dayName)
    canvas.drawText(day.dayOfMonth.toString(), labelW / 2f, y0 + rowH * 0.66f, if (isToday) dayNumInv else dayNum)
    canvas.drawText(PlannerWeeks.monthName(day).uppercase(loc), labelW / 2f, y0 + rowH * 0.9f, if (isToday) dayNameInv else dayName)
}

/**
 * A day opened from the week page: the same row as on the week page first (its five lines are kept equal to the
 * week row), then more lines down to the bottom. The page does not scroll.
 */
fun drawPlannerDayBg(
    context: android.content.Context, canvas: Canvas, scale: Float, day: LocalDate, today: LocalDate = LocalDate.now()
) {
    val w = canvas.width / scale
    val h = canvas.height / scale
    canvas.drawColor(Color.WHITE)
    val idx = day.dayOfWeek.value - 1
    drawHeader(
        context, canvas, w, 0f, PlannerWeeks.dayFull(idx),
        PlannerWeeks.shortDate(day).uppercase(loc), "${text(com.ethran.notable.R.string.planner_week_title, PlannerWeeks.weekOfYear(PlannerWeeks.mondayOf(day))).uppercase(loc)} · ${day.year}",
        PlannerWeeks.yearPercent(today), withLogo = false
    )
    val layout = PlannerLayout.week(h)
    val lines = PlannerLayout.dayLines(h)
    val labelW = PlannerLayout.LABEL_W
    val top = PlannerLayout.ROWS_TOP
    drawDayLabel(canvas, labelW, top, layout.rowH, day, idx, day == today)
    drawDayLines(canvas, top, lines, layout.step, w, PlannerDone.lines(day), lastLineDrawn = true)
    canvas.drawLine(0f, top + layout.rowH, w, top + layout.rowH, ink)   // the lines above are mirrored on the week page
    canvas.drawLine(labelW, top, labelW, top + lines * layout.step, ink)
}

/** Second page of the week: a long check-box list and a ruled notes area (like the paper planner). */
fun drawPlannerTodoBg(canvas: Canvas, scroll: Offset, scale: Float, monday: LocalDate) {
    val w = canvas.width / scale
    val h = canvas.height / scale
    canvas.drawColor(Color.WHITE)
    val oy = -scroll.y

    canvas.drawText(text(com.ethran.notable.R.string.planner_todo), 60f, 100f + oy, titleP)
    canvas.drawText(text(com.ethran.notable.R.string.planner_week_title, PlannerWeeks.weekNumber(monday)).uppercase(loc), w - 60f, 98f + oy, subP)
    canvas.drawRect(0f, HEADER_H - 8f + oy, w, HEADER_H + oy, fillInk)

    val step = 112f
    val items = 14
    for (k in 0 until items) {
        val ly = HEADER_H + (k + 1) * step + oy
        checkbox(canvas, 70f, ly - 22f, 38f)
        canvas.drawLine(170f, ly, w - 40f, ly, guide)
    }
    val listEnd = HEADER_H + items * step + oy
    canvas.drawLine(150f, HEADER_H + oy, 150f, listEnd, ink)

    val notesTop = listEnd + 90f
    canvas.drawRect(0f, notesTop - 62f, w, notesTop - 54f, fillInk)
    canvas.drawText(text(com.ethran.notable.R.string.planner_notes), 60f, notesTop, sectionP)
    var y = notesTop + 80f
    while (y < h + 80f) { canvas.drawLine(60f, y, w - 40f, y, guide); y += 80f }
}

// ---- Month, quarter and year pages -----------------------------------------------------------------------------------
// Geometry (and the tap zones) come from PlannerPagesLayout; this file only draws. Everything starts at
// PlannerLayout.ROWS_TOP, under the header and the navigation strip.

private val weekdayP = paint(Color.DKGRAY, size = 24f, face = sansBold).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.08f }
private val bigNum = paint(Color.BLACK, size = 40f, face = serifBold)
private val bigNumDim = paint(0xFF999999.toInt(), size = 40f, face = Typeface.SERIF)
private val bigNumInv = paint(Color.WHITE, size = 40f, face = serifBold)
private val weekNoP = paint(Color.DKGRAY, size = 22f, face = sansBold).apply { textAlign = Paint.Align.CENTER }
private val labelP = paint(Color.DKGRAY, size = 23f, face = sansBold).apply { letterSpacing = 0.12f }
private val metaP = paint(Color.DKGRAY, size = 22f, face = sansBold).apply { letterSpacing = 0.12f }
private val monthTitleP = paint(Color.BLACK, size = 54f, face = serifBold)
private val monthTitleSmallP = paint(Color.BLACK, size = 42f, face = serifBold)
private val miniNum = paint(Color.BLACK, size = 30f, face = sansBold).apply { textAlign = Paint.Align.CENTER }
private val miniNumSmall = paint(Color.BLACK, size = 27f, face = sansBold).apply { textAlign = Paint.Align.CENTER }
private val miniNumDim = paint(0xFFB4B4B4.toInt(), size = 30f, face = sansBold).apply { textAlign = Paint.Align.CENTER }
private val miniNumDimSmall = paint(0xFFB4B4B4.toInt(), size = 27f, face = sansBold).apply { textAlign = Paint.Align.CENTER }
private val miniNumInv = paint(Color.WHITE, size = 30f, face = sansBold).apply { textAlign = Paint.Align.CENTER }
private val miniNumInvSmall = paint(Color.WHITE, size = 27f, face = sansBold).apply { textAlign = Paint.Align.CENTER }
private val miniWeekNo = paint(0xFF888888.toInt(), size = 20f, face = sansBold).apply { textAlign = Paint.Align.CENTER }
private val miniWeekday = paint(Color.DKGRAY, size = 22f, face = sansBold).apply { textAlign = Paint.Align.CENTER }
private val quarterLabelP = paint(Color.BLACK, size = 48f, face = serifBold).apply { textAlign = Paint.Align.CENTER }
private val quarterTagP = paint(Color.BLACK, size = 32f, face = serifBold).apply { textAlign = Paint.Align.CENTER }
private val hatchP = paint(0xFFCFCFCF.toInt(), 1.5f)
private val plusP = paint(Color.BLACK, 3f, Paint.Style.STROKE)
private val whiteFill = paint(Color.WHITE)

/** Draws [text] in at most [maxW] pixels: on one line when it fits, else on two, shrinking only when a word is too wide. */
private fun drawWrapped(canvas: Canvas, text: String, x: Float, baseline: Float, maxW: Float, base: Paint) {
    val p = Paint(base)
    if (p.measureText(text) <= maxW) { canvas.drawText(text, x, baseline, p); return }
    val words = text.split(' ')
    var lines = listOf(text)
    if (words.size >= 2) {
        // Split where the longer of the two lines is shortest.
        lines = (1 until words.size).map { i -> words.take(i).joinToString(" ") to words.drop(i).joinToString(" ") }
            .minByOrNull { (a, b) -> maxOf(p.measureText(a), p.measureText(b)) }!!.toList()
    }
    while (p.textSize > 14f && lines.any { p.measureText(it) > maxW }) p.textSize -= 1f
    lines.forEachIndexed { i, l -> canvas.drawText(l, x, baseline + i * p.textSize * 1.3f, p) }
}

private fun drawPlus(canvas: Canvas, cx: Float, cy: Float, r: Float = 22f) {
    canvas.drawCircle(cx, cy, r, whiteFill)
    canvas.drawCircle(cx, cy, r, plusP)
    canvas.drawLine(cx - r * 0.5f, cy, cx + r * 0.5f, cy, plusP)
    canvas.drawLine(cx, cy - r * 0.5f, cx, cy + r * 0.5f, plusP)
}

/** Label, round + button (until the maximum) and the ruled lines of a focus block. */
private fun drawFocus(canvas: Canvas, f: FocusBlock, label: String) {
    drawWrapped(canvas, label.uppercase(loc), f.labelLeft, f.top + 38f, PlannerPagesLayout.FOCUS_LABEL_W, labelP)
    if (f.canAdd) drawPlus(canvas, f.plusCenterX, f.plusCenterY)
    for (k in 0 until f.lines) canvas.drawLine(f.linesLeft, f.ruleY(k), f.right, f.ruleY(k), guide)
}

private fun hatch(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
    canvas.save()
    canvas.clipRect(l, t, r, b)
    var x = l - (b - t)
    while (x < r) { canvas.drawLine(x, b, x + (b - t), t, hatchP); x += 16f }
    canvas.restore()
}

private fun dateRange(start: LocalDate, end: LocalDate) = "${PlannerWeeks.shortDate(start)} – ${PlannerWeeks.shortDate(end)}"

/**
 * Month page: focus lines, then a grid of six week rows by seven Monday-first days. Days of the neighbour months are
 * dimmed and hatched, the left column carries the ISO week numbers, today is a black box.
 */
fun drawPlannerMonthBg(
    context: android.content.Context, canvas: Canvas, scale: Float, month: YearMonth, today: LocalDate = LocalDate.now(),
    writing: WritingDays = PlannerWritingDays.current, focusLines: Int = PlannerFocusLines.lines(PlannerLevel.Month)
) {
    val w = canvas.width / scale
    val h = canvas.height / scale
    canvas.drawColor(Color.WHITE)
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val percent = PlannerPagesLayout.periodPercent(first, last, today)
    drawHeader(
        context, canvas, w, 0f, text(com.ethran.notable.R.string.planner_month_title), PlannerNames.monthTitle(month).uppercase(loc),
        dateRange(first, last).uppercase(loc), percent, complete = text(com.ethran.notable.R.string.planner_month_complete, percent)
    )
    val lay = PlannerPagesLayout.month(month, w, h, focusLines)
    drawFocus(canvas, lay.focus, text(com.ethran.notable.R.string.planner_focus_month))
    val right = lay.gridRight
    val left = PlannerPagesLayout.SIDE
    canvas.drawLine(left, lay.weekdayRow.top, right, lay.weekdayRow.top, inkThin)
    canvas.drawText(text(com.ethran.notable.R.string.planner_week_abbr).uppercase(loc), left + PlannerPagesLayout.WEEKNO_W / 2f, lay.weekdayRow.centerY + 8f, weekNoP)
    val colW = (right - lay.gridLeft) / 7f
    for (c in 0 until 7) canvas.drawText(PlannerWeeks.dayName(c), lay.gridLeft + (c + 0.5f) * colW, lay.weekdayRow.centerY + 8f, weekdayP)
    canvas.drawLine(left, lay.weekdayRow.bottom, right, lay.weekdayRow.bottom, ink)

    for (c in 1 until 7) canvas.drawLine(lay.gridLeft + c * colW, lay.gridTop, lay.gridLeft + c * colW, lay.gridBottom, guide)
    for (row in lay.rows) {
        for (cell in row.cells) {
            val r = cell.rect
            if (!cell.inMonth) hatch(canvas, r.left + 1f, r.top + 1f, r.right - 1f, r.bottom - 1f)
            val label = cell.date.dayOfMonth.toString()
            val tx = r.left + 16f
            val base = r.top + 48f
            when {
                cell.date == today -> {
                    canvas.drawRect(tx - 8f, base - 40f, tx + bigNum.measureText(label) + 8f, base + 10f, fillInk)
                    canvas.drawText(label, tx, base, bigNumInv)
                }
                cell.inMonth -> canvas.drawText(label, tx, base, bigNum)
                else -> canvas.drawText(label, tx, base, bigNumDim)
            }
            if (writing.hasInk(cell.date)) canvas.drawCircle(r.right - 24f, r.top + 28f, 6f, fillInk)
        }
        canvas.drawText(text(com.ethran.notable.R.string.planner_week_abbr).uppercase(loc) + row.isoWeek, left + PlannerPagesLayout.WEEKNO_W / 2f, row.rect.top + 40f, weekNoP)
        canvas.drawLine(left, row.rect.bottom, right, row.rect.bottom, ink)
    }
    canvas.drawLine(lay.gridLeft, lay.weekdayRow.top, lay.gridLeft, lay.gridBottom, ink)
}

private fun drawMini(
    canvas: Canvas, cal: MiniCalendar, today: LocalDate, writing: WritingDays, small: Boolean, title: String, meta: String?
) {
    val titleP = if (small) monthTitleSmallP else monthTitleP
    canvas.drawText(title, cal.left, cal.titleBaseline, titleP)
    if (meta != null) canvas.drawText(meta, cal.left + titleP.measureText(title) + 14f, cal.titleBaseline, metaP)
    for (c in 0 until 7) {
        val letter = java.time.DayOfWeek.of(c + 1).getDisplayName(java.time.format.TextStyle.NARROW, loc).uppercase(loc)
        canvas.drawText(letter, cal.columnX[c], cal.weekdayCy + 8f, miniWeekday)
    }
    val numP = if (small) miniNumSmall else miniNum
    val dimP = if (small) miniNumDimSmall else miniNumDim
    val invP = if (small) miniNumInvSmall else miniNumInv
    val radius = if (small) 20f else 24f
    for (row in cal.rows) {
        cal.weekNumberX?.let { canvas.drawText(row.isoWeek.toString(), it, row.cy + 7f, miniWeekNo) }
        for (cell in row.cells) {
            val label = cell.date.dayOfMonth.toString()
            when {
                cell.date == today -> {
                    canvas.drawCircle(cell.cx, cell.cy, radius, fillInk)
                    canvas.drawText(label, cell.cx, cell.cy + 10f, invP)
                }
                cell.inMonth -> canvas.drawText(label, cell.cx, cell.cy + 10f, numP)
                else -> canvas.drawText(label, cell.cx, cell.cy + 10f, dimP)
            }
            if (writing.hasInk(cell.date) && cell.date != today) canvas.drawCircle(cell.cx, cell.cy + radius + 3f, 3.5f, fillInk)
        }
    }
}

/** Quarter page: focus lines, then the three months, each with a small calendar and a ruled Goals area. */
fun drawPlannerQuarterBg(
    context: android.content.Context, canvas: Canvas, scale: Float, quarter: PlannerWeeks.YearQuarter, today: LocalDate = LocalDate.now(),
    writing: WritingDays = PlannerWritingDays.current, focusLines: Int = PlannerFocusLines.lines(PlannerLevel.Quarter)
) {
    val w = canvas.width / scale
    val h = canvas.height / scale
    canvas.drawColor(Color.WHITE)
    val percent = PlannerPagesLayout.periodPercent(quarter.start, quarter.end, today)
    drawHeader(
        context, canvas, w, 0f, text(com.ethran.notable.R.string.planner_quarter_title), PlannerNames.quarterTitle(quarter.year, quarter.quarter).uppercase(loc),
        dateRange(quarter.start, quarter.end).uppercase(loc), percent, complete = text(com.ethran.notable.R.string.planner_quarter_complete, percent)
    )
    val lay = PlannerPagesLayout.quarter(quarter, w, h, focusLines)
    drawFocus(canvas, lay.focus, text(com.ethran.notable.R.string.planner_focus_quarter))
    val right = PlannerPagesLayout.right(w)
    canvas.drawLine(PlannerPagesLayout.SIDE, lay.areaTop, right, lay.areaTop, inkThin)
    lay.months.forEachIndexed { i, m ->
        if (lay.portrait && i > 0) canvas.drawLine(PlannerPagesLayout.SIDE, m.block.top, right, m.block.top, inkThin)
        if (!lay.portrait && i > 0) canvas.drawLine(m.block.left, lay.areaTop, m.block.left, lay.areaBottom, guide)
        drawMini(canvas, m.calendar, today, writing, small = false, PlannerNames.monthLabel(m.month),
            text(com.ethran.notable.R.string.planner_days_count, m.month.lengthOfMonth()).uppercase(loc))
        canvas.drawText(text(com.ethran.notable.R.string.planner_goals).uppercase(loc), m.goalsLeft, m.goalsLabelBaseline, labelP)
        for (y in m.goalRules) canvas.drawLine(m.goalsLeft, y, m.goalsRight, y, guide)
    }
}

/** Year page: twelve small calendars, Q1..Q4 labels and a ruled year focus at the bottom. */
fun drawPlannerYearBg(
    context: android.content.Context, canvas: Canvas, scale: Float, year: Int, today: LocalDate = LocalDate.now(),
    writing: WritingDays = PlannerWritingDays.current, focusLines: Int = PlannerFocusLines.lines(PlannerLevel.Year)
) {
    val w = canvas.width / scale
    val h = canvas.height / scale
    canvas.drawColor(Color.WHITE)
    val start = LocalDate.of(year, 1, 1)
    val end = LocalDate.of(year, 12, 31)
    val percent = PlannerPagesLayout.periodPercent(start, end, today)
    drawHeader(
        context, canvas, w, 0f, text(com.ethran.notable.R.string.planner_year_title), PlannerNames.yearTitle(year), dateRange(start, end).uppercase(loc), percent
    )
    val lay = PlannerPagesLayout.year(year, w, h, focusLines)
    val right = PlannerPagesLayout.right(w)
    val left = PlannerPagesLayout.SIDE
    for (r in 1 until lay.rowsCount) {
        val y = lay.gridTop + r * (lay.gridBottom - lay.gridTop) / lay.rowsCount
        canvas.drawLine(left, y, right, y, guide)
    }
    for (c in 1 until lay.columns) {
        val x = lay.gridLeft + c * (right - lay.gridLeft) / lay.columns
        canvas.drawLine(x, lay.gridTop, x, lay.gridBottom, guide)
    }
    canvas.drawLine(left, lay.gridBottom, right, lay.gridBottom, guide)
    if (lay.portrait) {
        canvas.drawLine(lay.gridLeft, lay.gridTop, lay.gridLeft, lay.gridBottom, ink)
        lay.quarterStripes.forEachIndexed { i, r ->
            canvas.save()
            canvas.rotate(-90f, r.centerX, r.centerY)
            canvas.drawText("Q${i + 1}", r.centerX, r.centerY + 16f, quarterLabelP)
            canvas.restore()
        }
    }
    for (cell in lay.cells) {
        drawMini(canvas, cell.calendar, today, writing, small = true, PlannerNames.monthShort(cell.month), "%02d".format(java.util.Locale.ROOT, cell.month.monthValue))
        cell.quarterTag?.let { tag ->
            canvas.drawRect(tag.left, tag.top + 4f, tag.right, tag.bottom - 4f, box)
            canvas.drawText("Q${(cell.month.monthValue - 1) / 3 + 1}", tag.centerX, tag.centerY + 11f, quarterTagP)
        }
    }
    drawFocus(canvas, lay.focus, text(com.ethran.notable.R.string.planner_focus_year))
}

/**
 * Draws a month, quarter or year page. Any other `planner-` key (a page type from a newer version of the app) is left
 * blank instead of failing. Returns false when [background] is not a planner key at all.
 */
fun drawPlannerPagesBg(context: android.content.Context, canvas: Canvas, scale: Float, background: String): Boolean {
    PlannerWeeks.parseMonth(background)?.let { drawPlannerMonthBg(context, canvas, scale, it); return true }
    PlannerWeeks.parseQuarter(background)?.let { drawPlannerQuarterBg(context, canvas, scale, it); return true }
    PlannerWeeks.parseYear(background)?.let { drawPlannerYearBg(context, canvas, scale, it); return true }
    if (PlannerWeeks.isPlanner(background)) { canvas.drawColor(Color.WHITE); return true }
    return false
}
