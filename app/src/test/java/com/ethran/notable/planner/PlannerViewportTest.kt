package com.ethran.notable.planner

import androidx.compose.ui.geometry.Offset
import com.ethran.notable.data.db.Stroke
import com.ethran.notable.data.db.StrokePoint
import com.ethran.notable.editor.utils.Pen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PlannerViewportTest {
    // The tablet: 1860 x 2480 screen, 300 dpi (density 1.875), tool rail gutter 56 dp = 105 px.
    private val gutter = 105f
    private val page = PlannerViewport.portraitPage(1860, 2480, gutter)

    private fun portraitBox() = PlannerViewport.of(1860 - gutter, 2480f, page)
    private fun landscapeBox() = PlannerViewport.of(2480 - gutter, 1860f, page)

    // ---------------------------------------------------------------------------------------------- the page
    @Test fun `the portrait page is the portrait editor box, whichever way the screen is turned`() {
        assertEquals(PlannerPageSize(1755f, 2480f), page)
        assertEquals(page, PlannerViewport.portraitPage(2480, 1860, gutter))   // landscape reading of the same screen
    }

    // -------------------------------------------------------------------------------------------- portrait
    @Test fun `portrait is the identity, nothing is scaled or moved`() {
        val v = portraitBox()
        assertTrue(v.isIdentity)
        assertEquals(1f, v.scale, 0f)
        assertEquals(0f, v.offsetX, 0f)
        assertEquals(0f, v.offsetY, 0f)
        assertEquals(1755f, v.pageW, 0f)
        assertEquals(2480f, v.pageH, 0f)
        assertEquals(PageRect(0f, 0f, 1755f, 2480f), v.sheet)
        assertEquals(Offset(123f, 456f), v.toPage(Offset(123f, 456f)))
        assertEquals(Offset(123f, 456f), v.toScreen(Offset(123f, 456f)))
        assertEquals(Offset.Zero.x, v.scroll.x + 0f, 0f)
        assertEquals(0f, v.scroll.y + 0f, 0f)
    }

    @Test fun `a box one or two pixels off the page by rounding is still the page, and the box is the page`() {
        val v = PlannerViewport.of(1754f, 2479f, page)
        assertTrue(v.isIdentity)
        assertEquals(1754f, v.pageW, 0f)   // what is drawn and what is tapped use the box, as before
        assertEquals(2479f, v.pageH, 0f)
        assertTrue(PlannerViewport.of(1757f, 2482f, page).isIdentity)
        assertFalse(PlannerViewport.of(1760f, 2480f, page).isIdentity)
    }

    // -------------------------------------------------------------------------------------------- landscape
    @Test fun `landscape shows the whole page scaled to the box height and centred`() {
        val v = landscapeBox()
        assertEquals(0.75f, v.scale, 1e-6f)
        assertEquals(1755f, v.pageW, 0f)
        assertEquals(2480f, v.pageH, 0f)
        val sheet = v.sheet
        assertEquals(1755f * 0.75f, sheet.width, 0.01f)
        assertEquals(1860f, sheet.height, 0.01f)
        assertEquals(0f, v.offsetY, 0.001f)
        // centred: equal margin left and right
        assertEquals(sheet.left, 2375f - sheet.right, 0.01f)
        assertEquals(1187.5f, sheet.centerX, 0.01f)
        assertTrue(sheet.left > 400f)   // a real margin on both sides
        assertFalse(v.isIdentity)
    }

    @Test fun `screen and page coordinates map to each other both ways`() {
        for (v in listOf(landscapeBox(), PlannerViewport.of(1000f, 1860f, page), PlannerViewport.of(700f, 900f, 560f, 960f))) {
            for (p in listOf(Offset(0f, 0f), Offset(1755f, 2480f), Offset(300f, 1234f), Offset(-20f, 3000f))) {
                val back = v.toPage(v.toScreen(p))
                assertEquals(p.x, back.x, 0.01f)
                assertEquals(p.y, back.y, 0.01f)
            }
            val s = v.sheet
            assertEquals(0f, v.toPageX(s.left), 0.01f)
            assertEquals(v.pageW, v.toPageX(s.right), 0.01f)
            assertEquals(0f, v.toPageY(s.top), 0.01f)
            assertEquals(v.pageH, v.toPageY(s.bottom), 0.01f)
        }
    }

    @Test fun `the page's zoom and scroll reproduce the viewport`() {
        // The editor shows the page with zoom = scale and scroll = -offset / scale: screen = (page - scroll) * zoom.
        val v = landscapeBox()
        val scroll = v.scroll
        for (p in listOf(Offset(0f, 0f), Offset(400f, 900f), Offset(1755f, 2480f))) {
            assertEquals(v.toScreenX(p.x), (p.x - scroll.x) * v.scale, 0.01f)
            assertEquals(v.toScreenY(p.y), (p.y - scroll.y) * v.scale, 0.01f)
        }
        // and the pen path (screen / zoom + scroll) lands on the page point
        val pen = Offset(v.sheet.left + 100f, 500f)
        assertEquals(v.toPageX(pen.x), pen.x / v.scale + scroll.x, 0.01f)
        assertEquals(v.toPageY(pen.y), pen.y / v.scale + scroll.y, 0.01f)
    }

    @Test fun `the margin is not the sheet`() {
        val v = landscapeBox()
        assertFalse(v.sheetContains(10f, 500f))
        assertFalse(v.sheetContains(2370f, 500f))
        assertTrue(v.sheetContains(1187f, 500f))
        assertTrue(portraitBox().sheetContains(10f, 10f))
    }

    // ------------------------------------------------------------------------------------------- odd sizes
    @Test fun `a narrow split screen scales to the width and centres vertically`() {
        val v = PlannerViewport.of(1000f, 1860f, page)
        assertEquals(1000f / 1755f, v.scale, 1e-6f)
        assertEquals(0f, v.offsetX, 0.001f)
        assertTrue(v.offsetY > 0f)
        val s = v.sheet
        assertTrue(s.left >= -0.001f && s.right <= 1000.01f && s.top >= -0.001f && s.bottom <= 1860.01f)
        assertEquals(s.top, 1860f - s.bottom, 0.01f)
    }

    @Test fun `a small device fits its page into its box`() {
        val small = PlannerViewport.portraitPage(800, 1280, 48f)   // 752 x 1280
        val v = PlannerViewport.of(1280f - 48f, 800f, small)       // turned to landscape
        assertEquals(800f / 1280f, v.scale, 1e-6f)
        val s = v.sheet
        assertTrue(s.left >= 0f && s.right <= 1232.01f && s.bottom <= 800.01f)
    }

    @Test fun `a box bigger than the page gets a scale above one and still fits`() {
        val v = PlannerViewport.of(2000f, 3000f, 1000f, 1500f)
        assertEquals(2f, v.scale, 1e-6f)
        assertEquals(PageRect(0f, 0f, 2000f, 3000f), v.sheet)
    }

    @Test fun `sizes that make no sense never give NaN or a negative scale`() {
        val bad = listOf(
            PlannerViewport.of(0f, 0f, 1755f, 2480f), PlannerViewport.of(-5f, 100f, 1755f, 2480f), PlannerViewport.of(100f, 100f, 0f, 0f),
            PlannerViewport.of(Float.NaN, 100f, 1755f, 2480f), PlannerViewport.of(100f, 100f, Float.POSITIVE_INFINITY, 2480f)
        )
        for (v in bad) {
            assertTrue(v.isIdentity)
            assertTrue(v.scale == 1f && v.offsetX == 0f && v.offsetY == 0f)
            assertFalse(v.boxW.isNaN() || v.boxH.isNaN() || v.boxW < 0f || v.boxH < 0f)
        }
    }

    // ---------------------------------------------------- taps hit the zone that is drawn (screen -> page)
    @Test fun `a tap on a month cell on screen hits that cell's tap zone in page pixels`() {
        val v = landscapeBox()
        val layout = PlannerPagesLayout.month(YearMonth.of(2026, 11), v.pageW, v.pageH, today = LocalDate.of(2026, 11, 13))
        val cell = layout.rows[2].cells[3]
        val centreOnScreen = v.toScreen(Offset(cell.number.centerX, cell.number.centerY))
        assertTrue(v.sheetContains(centreOnScreen.x, centreOnScreen.y))
        val page = v.toPage(centreOnScreen)
        assertEquals(PageTap.OpenDay(cell.date), layout.tapAt(page.x, page.y))
        // the same screen point in the margin is no page point of any zone
        val margin = v.toPage(Offset(20f, centreOnScreen.y))
        assertNull(layout.tapAt(margin.x, margin.y))
    }

    @Test fun `the week's rows are the same rows on screen as in the page`() {
        val v = landscapeBox()
        val week = PlannerLayout.week(v.pageH)
        // row 4 starts where the drawing puts it, on screen
        val screenTop = v.toScreenY(week.rowTop(4))
        assertEquals(week.rowTop(4) * 0.75f, screenTop, 0.01f)
    }

    // ---------------------------------------------- the ink migration and everything else use the fixed page height
    private fun stroke(id: String, cy: Float) = Stroke(
        id = id, size = 3f, pen = Pen.BALLPEN, top = cy - 5f, bottom = cy + 5f, left = 300f, right = 400f,
        points = listOf(StrokePoint(300f, cy - 5f), StrokePoint(400f, cy + 5f)), pageId = "week"
    )

    @Test fun `a week first opened in landscape is migrated exactly like one opened in portrait`() = runBlocking {
        // The page's height comes from the screen, never from the shape of the box: the same value for both.
        val portraitHeight = PlannerViewport.portraitPage(1860, 2480, gutter).height
        val landscapeHeight = PlannerViewport.portraitPage(2480, 1860, gutter).height
        assertEquals(portraitHeight, landscapeHeight, 0f)
        assertEquals(landscapeBox().pageH, portraitHeight, 0f)

        val old = PlannerInkMigration.oldWeek(portraitHeight)
        val ink = (0..6).map { stroke("w$it", old.rowTop(it) + 90f) }.toTypedArray()
        val inPortrait = FakePlannerStore(*ink).apply { strict = true }
        val inLandscape = FakePlannerStore(*ink).apply { strict = true }
        PlannerInkMigration.ensure(inPortrait, "week", PlannerInkMigration.Kind.Week, portraitBox().pageH)
        PlannerInkMigration.ensure(inLandscape, "week", PlannerInkMigration.Kind.Week, landscapeBox().pageH)
        assertEquals(inPortrait.all.toMap(), inLandscape.all.toMap())

        // the rows of the page it lands on are those of the portrait page: every stroke sits inside its own row
        val rows = PlannerLayout.week(portraitHeight)
        for (i in 0..6) {
            val s = inLandscape.all.getValue("w$i")
            val centre = (s.top + s.bottom) / 2f
            assertTrue("row $i", centre >= rows.rowTop(i) && centre < rows.rowTop(i + 1))
        }
    }

    @Test fun `with the box height instead of the page height the same ink would land wrong`() = runBlocking {
        // This is the bug the fixed page height prevents: 1860 is the landscape box height.
        val h = 2480f
        val old = PlannerInkMigration.oldWeek(h)
        val ink = stroke("w3", old.rowTop(3) + 90f)
        val right = FakePlannerStore(ink).apply { strict = true }
        val wrong = FakePlannerStore(ink).apply { strict = true }
        PlannerInkMigration.ensure(right, "week", PlannerInkMigration.Kind.Week, h)
        PlannerInkMigration.ensure(wrong, "week", PlannerInkMigration.Kind.Week, 1860f)
        assertTrue(right.all.getValue("w3").top != wrong.all.getValue("w3").top)
    }
}
