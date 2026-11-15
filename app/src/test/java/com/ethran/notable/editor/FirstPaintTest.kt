package com.ethran.notable.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstPaintTest {
    @Test
    fun `before the page row is read the bitmap gets a stand-in that must be painted over`() {
        val kind = FirstPaint.initialBackground(pageKnown = false, isNativeBackground = false, plannerSheetNeedsScaling = false)
        assertEquals(InitialBackground.PLACEHOLDER, kind)
        assertTrue(FirstPaint.needsRepaintWhenPageKnown(kind))
    }

    @Test
    fun `an unknown page stays a stand-in even if the other facts look native`() {
        val kind = FirstPaint.initialBackground(pageKnown = false, isNativeBackground = true, plannerSheetNeedsScaling = true)
        assertEquals(InitialBackground.PLACEHOLDER, kind)
    }

    @Test
    fun `a known native page draws its lines straight away and needs no repaint`() {
        val kind = FirstPaint.initialBackground(pageKnown = true, isNativeBackground = true, plannerSheetNeedsScaling = false)
        assertEquals(InitialBackground.NATIVE, kind)
        assertFalse(FirstPaint.needsRepaintWhenPageKnown(kind))
    }

    @Test
    fun `a scaled planner sheet and non native pages are left to the full redraw`() {
        assertEquals(
            InitialBackground.PLAIN_WHITE,
            FirstPaint.initialBackground(pageKnown = true, isNativeBackground = true, plannerSheetNeedsScaling = true)
        )
        assertEquals(
            InitialBackground.PLAIN_WHITE,
            FirstPaint.initialBackground(pageKnown = true, isNativeBackground = false, plannerSheetNeedsScaling = false)
        )
        assertFalse(FirstPaint.needsRepaintWhenPageKnown(InitialBackground.PLAIN_WHITE))
    }
}
