package com.ethran.notable.gestures

import androidx.compose.ui.input.pointer.PointerType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PenDownTest {
    @Test
    fun `a stylus touch is skipped, fingers and the eraser end are not`() {
        assertTrue(isPenDown(PointerType.Stylus))
        assertFalse(isPenDown(PointerType.Touch))
        assertFalse(isPenDown(PointerType.Eraser))
        assertFalse(isPenDown(PointerType.Mouse))
    }
}
