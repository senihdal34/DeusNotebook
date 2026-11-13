package com.ethran.notable.editor.drawing

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortraitMarginTest {
    @Test
    fun `notes keep the portrait guide and planner pages do not`() {
        listOf("inbox", "lined", "blank", "dotted").forEach { assertTrue(shouldDrawPortraitMargin(it), it) }
        listOf(
            "planner-week:2026-11-09", "planner-day:2026-11-12", "planner-month:2026-11", "planner-quarter:2026-Q4", "planner-year:2026"
        ).forEach { assertFalse(shouldDrawPortraitMargin(it), it) }
    }
}
