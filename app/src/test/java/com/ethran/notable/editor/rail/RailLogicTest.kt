package com.ethran.notable.editor.rail

import com.ethran.notable.data.datastore.EditorSettingCacheManager
import com.ethran.notable.editor.state.Mode
import com.ethran.notable.editor.ui.HorizontalIndicator
import com.ethran.notable.editor.utils.Pen
import com.ethran.notable.editor.utils.PenSetting
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PenMigrationTest {
    private val red = android.graphics.Color.RED
    private val blue = android.graphics.Color.BLUE
    private val green = android.graphics.Color.GREEN

    @Test fun offeredPensStayAsTheyAre() {
        for (pen in listOf(Pen.BALLPEN, Pen.FOUNTAIN, Pen.MARKER)) {
            val m = PenMigration.migrate(pen, Pen.DEFAULT_SETTINGS)
            assertEquals(pen, m.pen)
            assertFalse(m.lineDashed)
        }
    }

    @Test fun coloursBecomeABallpointWithThatColour() {
        val saved = Pen.DEFAULT_SETTINGS + (Pen.REDBALLPEN.penName to PenSetting(8f, red))
        val m = PenMigration.migrate(Pen.REDBALLPEN, saved)
        assertEquals(Pen.BALLPEN, m.pen)
        assertEquals(PenSetting(8f, red), m.penSettings.getValue(Pen.BALLPEN.penName))
        assertEquals(blue, PenMigration.migrate(Pen.BLUEBALLPEN, Pen.DEFAULT_SETTINGS).penSettings.getValue(Pen.BALLPEN.penName).color)
        assertEquals(green, PenMigration.migrate(Pen.GREENBALLPEN, Pen.DEFAULT_SETTINGS).penSettings.getValue(Pen.BALLPEN.penName).color)
        assertEquals(Pen.BALLPEN, PenMigration.migrate(Pen.BLUEBALLPEN, Pen.DEFAULT_SETTINGS).pen)
    }

    @Test fun pencilAndBrushBecomeFountainAndKeepItsSettings() {
        val saved = Pen.DEFAULT_SETTINGS + (Pen.FOUNTAIN.penName to PenSetting(12f, android.graphics.Color.BLACK))
        for (old in listOf(Pen.PENCIL, Pen.BRUSH)) {
            val m = PenMigration.migrate(old, saved)
            assertEquals(Pen.FOUNTAIN, m.pen)
            assertEquals(PenSetting(12f, android.graphics.Color.BLACK), m.penSettings.getValue(Pen.FOUNTAIN.penName))
        }
    }

    @Test fun dashedPenBecomesBallpointWithDashedLines() {
        val m = PenMigration.migrate(Pen.DASHED, Pen.DEFAULT_SETTINGS)
        assertEquals(Pen.BALLPEN, m.pen)
        assertTrue(m.lineDashed)
    }

    @Test fun missingSettingsAreFilledSoNothingCrashesLater() {
        val m = PenMigration.migrate(Pen.REDBALLPEN, emptyMap())
        for (pen in listOf(Pen.BALLPEN, Pen.FOUNTAIN, Pen.MARKER)) assertNotNull(m.penSettings[pen.penName])
        assertEquals(red, m.penSettings.getValue(Pen.BALLPEN.penName).color)
    }

    @Test fun everyEnumValueMapsToAnOfferedPen() {
        for (pen in Pen.entries) {
            val m = PenMigration.migrate(pen, Pen.DEFAULT_SETTINGS)
            assertTrue("$pen", m.pen in setOf(Pen.BALLPEN, Pen.FOUNTAIN, Pen.MARKER))
        }
    }

    @Test fun savedEditorStateWithARedPenDecodesAndMaps() {
        // What an older version wrote: red ballpoint current, no lineDashed field.
        val json = """{"version":2,"isToolbarOpen":true,"pen":"REDBALLPEN","eraser":"PEN",
            "penSettings":{"REDBALLPEN":{"strokeSize":7.0,"color":-65536}},"mode":"Draw"}"""
        val saved = Json.decodeFromString<EditorSettingCacheManager.EditorSettings>(json)
        assertEquals(Mode.Draw, saved.mode)
        assertFalse(saved.lineDashed)
        val m = PenMigration.migrate(saved.pen, saved.penSettings)
        assertEquals(Pen.BALLPEN, m.pen)
        assertEquals(PenSetting(7f, red), m.penSettings.getValue(Pen.BALLPEN.penName))
    }
}

class RailLayoutTest {
    private fun items(ctx: RailContext) = RailLayout.all(ctx)

    @Test fun anOrdinaryNotebookPageHasEveryButton() {
        val all = items(RailContext())
        assertEquals(
            listOf(
                RailItem.Back, RailItem.Pen, RailItem.Eraser, RailItem.Highlighter, RailItem.Lasso, RailItem.Line,
                RailItem.Undo, RailItem.Redo, RailItem.PageCounter, RailItem.PrevPage, RailItem.NextPage,
                RailItem.NewPage, RailItem.Favorite, RailItem.More
            ), all
        )
        assertEquals(3, RailLayout.topGroups(RailContext()).size)
    }

    @Test fun backAndToolsAreAlwaysThere() {
        for (ctx in listOf(RailContext(), RailContext(isInboxPage = true, inBook = false), RailContext(isPlannerPage = true))) {
            val all = items(ctx)
            for (i in listOf(RailItem.Back, RailItem.Pen, RailItem.Eraser, RailItem.Highlighter, RailItem.Lasso, RailItem.Line,
                RailItem.Undo, RailItem.Redo, RailItem.More)) assertTrue("$i in $ctx", i in all)
        }
    }

    @Test fun anInboxPageIsASinglePageSoNoPageCounterOrNewPage() {
        val all = items(RailContext(isInboxPage = true, inBook = false))
        assertFalse(RailItem.PageCounter in all || RailItem.PrevPage in all || RailItem.NextPage in all || RailItem.NewPage in all)
        assertTrue(RailItem.Favorite in all)
        assertEquals(2, RailLayout.topGroups(RailContext(inBook = false)).size)
    }

    @Test fun aPlannerPageHasNoPageGroupNewPageOrStar() {
        val all = items(RailContext(isPlannerPage = true))
        for (i in listOf(RailItem.PageCounter, RailItem.PrevPage, RailItem.NextPage, RailItem.NewPage, RailItem.Favorite)) {
            assertFalse("$i", i in all)
        }
    }

    @Test fun pasteOnlyWithClipboardAndResetOnlyWhenTheViewMoved() {
        val plain = RailLayout.moreItems(RailContext())
        assertFalse(MoreItem.Paste in plain)
        assertFalse(MoreItem.ResetView in plain)
        assertTrue(MoreItem.Paste in RailLayout.moreItems(RailContext(clipboardHasContent = true)))
        assertTrue(MoreItem.ResetView in RailLayout.moreItems(RailContext(viewMoved = true)))
    }

    @Test fun nothingOfTheOldMenuIsLost() {
        val more = RailLayout.moreItems(RailContext(clipboardHasContent = true, viewMoved = true))
        for (i in listOf(
            MoreItem.InsertImage, MoreItem.Paste, MoreItem.PageBackground, MoreItem.ResetView, MoreItem.Export,
            MoreItem.ClearPage, MoreItem.WikiLink, MoreItem.Tag, MoreItem.Library, MoreItem.BugReport, MoreItem.HideToolbar
        )) assertTrue("$i", i in more)
        // hiding is always the last entry
        assertEquals(MoreItem.HideToolbar, more.last())
    }

    @Test fun aPlannerPageKeepsItsBackgroundAndIsNotOfferedAChange() {
        assertFalse(MoreItem.PageBackground in RailLayout.moreItems(RailContext(isPlannerPage = true)))
    }
}

class RailVisibilityTest {
    @Test fun foldsAfterFiveSecondsOfWriting() {
        val v = RailVisibility()
        var t = 0L
        v.onPenWriting(t)
        // pen events every 300 ms for 4.8 s: still open
        while (t < 4_800) { t += 300; v.onPenWriting(t); assertFalse(v.tick(t)) }
        assertFalse(v.folded)
        t += 300; v.onPenWriting(t)
        assertTrue(v.tick(t))
        assertTrue(v.folded)
    }

    @Test fun aPauseEndsTheRunAndTheCountStartsAgain() {
        val v = RailVisibility()
        for (t in 0..4_000 step 500) v.onPenWriting(t.toLong())   // 4 s of writing
        // 3 s pause (more than the idle gap): that run is over, the 4 s do not count towards the next one
        for (t in 7_000..11_000 step 500) {
            v.onPenWriting(t.toLong())
            assertFalse("at $t", v.tick(t.toLong()))               // 4 s into the new run
        }
        v.onPenWriting(12_000)
        assertTrue(v.tick(12_000))                                  // 5 s into the new run
    }

    @Test fun doesNotFoldWhenThePenStoppedBeforeTheTickArrives() {
        val v = RailVisibility()
        for (t in 0..6_000 step 500) v.onPenWriting(t.toLong())
        assertFalse(v.tick(9_000)) // pen idle for 3 s: no collapse mid-pause
        assertFalse(v.folded)
    }

    @Test fun handleTapOpensAndTheCountStartsAnew() {
        val v = RailVisibility()
        for (t in 0..5_000 step 500) v.onPenWriting(t.toLong())
        assertTrue(v.tick(5_000))
        v.unfold()
        assertFalse(v.folded)
        v.onPenWriting(5_500)
        assertFalse(v.tick(6_000))
        for (t in 6_000..10_500 step 500) v.onPenWriting(t.toLong())
        assertTrue(v.tick(10_500))
    }

    @Test fun neverFoldsWhenAutoHideIsOff() {
        val v = RailVisibility(autoHide = false)
        for (t in 0..60_000 step 500) {
            v.onPenWriting(t.toLong())
            assertFalse(v.tick(t.toLong()))
        }
        assertFalse(v.folded)
    }

    @Test fun switchingAutoHideOffStopsTheCount() {
        val v = RailVisibility()
        for (t in 0..4_000 step 500) v.onPenWriting(t.toLong())
        v.setAutoHide(false)
        v.onPenWriting(5_000)
        assertFalse(v.tick(5_100))
        v.setAutoHide(true)
        assertFalse(v.tick(5_200)) // the old run was dropped
    }

    @Test fun neverFoldsWhileAPopoverIsOpen() {
        val v = RailVisibility()
        for (t in 0..8_000 step 500) {
            v.onPenWriting(t.toLong())
            assertFalse(v.tick(t.toLong(), popoverOpen = true))
        }
        assertFalse(v.folded)
    }

    @Test fun plannerPagesStartFoldedAndOpenOnTap() {
        val v = RailVisibility(startFolded = true)
        assertTrue(v.folded)
        v.unfold()
        assertFalse(v.folded)
        v.fold()
        assertTrue(v.folded)
    }

    @Test fun noWritingNoFolding() {
        val v = RailVisibility()
        assertFalse(v.tick(100_000))
    }
}

class RailMetricsTest {
    @Test fun theGutterIsAsWideAsTheOldSidebarWasWhenOpen() {
        // The page area is the screen minus this gutter, open or folded: pages keep their size.
        assertEquals(56, RailMetrics.GUTTER_DP)
    }

    @Test fun circlesFollowTheMockupProportionButNeverGoBelow48dp() {
        assertEquals(48f, RailMetrics.preferredCircleDp(930f), 0.01f)   // 88/1860 of 930 = 44 -> 48
        assertEquals(48f, RailMetrics.preferredCircleDp(400f), 0.01f)
        assertEquals(52f, RailMetrics.preferredCircleDp(2000f), 0.01f)  // capped to fit the gutter
    }

    @Test fun roomyScreensGetThePreferredSizeAndShortOnesShrinkToTheFloor() {
        val slots = 14; val rules = 2
        assertEquals(48f, RailMetrics.circleDp(930f, 1200f, slots, rules), 0.01f)
        val short = RailMetrics.circleDp(930f, 600f, slots, rules)
        assertTrue(short in RailMetrics.FLOOR_CIRCLE_DP..48f)
        assertEquals(RailMetrics.FLOOR_CIRCLE_DP, RailMetrics.circleDp(930f, 300f, slots, rules), 0.01f)
    }

    @Test fun pagePositionKnowsItsNeighbours() {
        val p = PagePosition(0, 3)
        assertFalse(p.hasPrev); assertTrue(p.hasNext); assertEquals(1, p.number)
        val last = PagePosition(2, 3)
        assertTrue(last.hasPrev); assertFalse(last.hasNext)
        assertFalse(PagePosition(-1, 3).known)
        assertFalse(PagePosition(0, 1).hasNext)
    }
}

class RailGlyphTest {
    @Test fun everyButtonButTheCounterHasASymbol() {
        for (item in RailItem.values()) {
            val glyph = RailGlyphs.forItem(item)
            if (item == RailItem.PageCounter) assertEquals(null, glyph) else assertNotNull(item.name, glyph)
        }
    }

    @Test fun toolsHaveDistinctSymbols() {
        val tools = listOf(RailItem.Pen, RailItem.Eraser, RailItem.Highlighter, RailItem.Lasso, RailItem.Line, RailItem.Undo, RailItem.Redo, RailItem.Back)
        val glyphs = tools.map { RailGlyphs.forItem(it) }
        assertEquals(glyphs.size, glyphs.toSet().size)
    }

    @Test fun symbolFollowsTheState() {
        assertEquals(RailGlyph.PenBallpoint, RailGlyphs.forItem(RailItem.Pen, GlyphState(inkPen = Pen.BALLPEN)))
        assertEquals(RailGlyph.PenFountain, RailGlyphs.forItem(RailItem.Pen, GlyphState(inkPen = Pen.FOUNTAIN)))
        assertEquals(RailGlyph.Eraser, RailGlyphs.forItem(RailItem.Eraser, GlyphState(eraser = com.ethran.notable.editor.utils.Eraser.PEN)))
        assertEquals(RailGlyph.EraserArea, RailGlyphs.forItem(RailItem.Eraser, GlyphState(eraser = com.ethran.notable.editor.utils.Eraser.SELECT)))
        assertEquals(RailGlyph.Line, RailGlyphs.forItem(RailItem.Line, GlyphState(lineDashed = false)))
        assertEquals(RailGlyph.LineDashed, RailGlyphs.forItem(RailItem.Line, GlyphState(lineDashed = true)))
        assertEquals(RailGlyph.Star, RailGlyphs.forItem(RailItem.Favorite, GlyphState(pinned = false)))
        assertEquals(RailGlyph.StarFilled, RailGlyphs.forItem(RailItem.Favorite, GlyphState(pinned = true)))
    }

    @Test fun everySymbolHasAnIconOfTheRightSize() {
        for (glyph in RailGlyph.values()) {
            val v = com.ethran.notable.editor.ui.rail.RailIcons.of(glyph)
            assertEquals(glyph.name, 24f, v.viewportWidth, 0f)
            assertEquals(glyph.name, 24f, v.viewportHeight, 0f)
            assertTrue(glyph.name, v.root.size > 0)
        }
    }

    @Test fun inboxPageKeepsTheSameButtons() {
        // The inbox bar no longer has a Back or a pen toggle: the rail's Back and the rail itself stay.
        val inbox = RailLayout.all(RailContext(isInboxPage = true, inBook = false))
        assertTrue(RailItem.Back in inbox)
        assertTrue(RailItem.Pen in inbox)
    }
}

class PopoverPlacementTest {
    private fun y(anchorTop: Int, anchorBottom: Int = anchorTop + 100, h: Int = 400, window: Int = 2480, inset: Int = 0, bottom: Boolean = false) =
        PopoverPlacement.y(anchorTop, anchorBottom, h, window, inset, bottom)

    @Test fun startsLevelWithItsButton() = assertEquals(300, y(300))

    @Test fun neverStartsAboveTheBarOnTop() {
        assertEquals(300, y(300, inset = 190))
        assertEquals(190, y(120, inset = 190))
    }

    @Test fun bottomAlignedEndsLevelWithItsButtonButStaysBelowTheBar() {
        assertEquals(2000 - 400, y(1900, anchorBottom = 2000, bottom = true))
        assertEquals(190, y(100, anchorBottom = 300, inset = 190, bottom = true))
    }

    @Test fun staysInsideTheWindow() {
        assertEquals(2480 - 400, y(2300))
    }

    @Test fun aPopoverTallerThanTheSpaceStartsAtTheBar() = assertEquals(190, y(500, h = 3000, inset = 190))
}

class HorizontalIndicatorTest {
    @Test fun noBarWhenThePageIsNotScrolledSideways() {
        assertEquals(null, HorizontalIndicator.fractions(1748, 0f))
        assertEquals(null, HorizontalIndicator.fractions(1748, -3f))
    }

    @Test fun noBarForAnEmptyViewport() = assertEquals(null, HorizontalIndicator.fractions(0, 100f))

    @Test fun barSitsAtTheRightEdgeWhenScrolled() {
        val (start, size) = HorizontalIndicator.fractions(1000, 500f)!!
        assertEquals(1f / 3f, start, 1e-5f)
        assertEquals(2f / 3f, size, 1e-5f)
        assertEquals(1f, start + size, 1e-5f)
    }
}
