package com.ethran.notable.editor.drawing

import com.ethran.notable.data.db.Stroke
import com.ethran.notable.editor.utils.Pen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawOrderTest {

    private fun stroke(id: String, pen: Pen) = Stroke(
        id = id, size = 3f, pen = pen, pageId = "p",
        top = 0f, bottom = 1f, left = 0f, right = 1f, points = emptyList(),
    )

    private fun ids(strokes: List<Stroke>) = strokes.map { it.id }

    @Test
    fun emptyListStaysEmpty() {
        assertTrue(drawOrder(emptyList()).isEmpty())
    }

    @Test
    fun allMarkersKeepStoredOrder() {
        val input = listOf(stroke("a", Pen.MARKER), stroke("b", Pen.MARKER), stroke("c", Pen.MARKER))
        assertEquals(listOf("a", "b", "c"), ids(drawOrder(input)))
    }

    @Test
    fun withoutMarkersNothingMoves() {
        val input = listOf(stroke("a", Pen.BALLPEN), stroke("b", Pen.FOUNTAIN), stroke("c", Pen.DASHED))
        assertSame(input, drawOrder(input))
    }

    @Test
    fun markerWrittenAfterTextGoesUnderIt() {
        val input = listOf(stroke("text1", Pen.BALLPEN), stroke("text2", Pen.FOUNTAIN), stroke("hl", Pen.MARKER))
        assertEquals(listOf("hl", "text1", "text2"), ids(drawOrder(input)))
    }

    @Test
    fun mixedPensAreStablePartition() {
        val input = listOf(
            stroke("1", Pen.BALLPEN),
            stroke("2", Pen.MARKER),
            stroke("3", Pen.DASHED),
            stroke("4", Pen.MARKER),
            stroke("5", Pen.PENCIL),
            stroke("6", Pen.BRUSH),
            stroke("7", Pen.REDBALLPEN),
            stroke("8", Pen.MARKER),
            stroke("9", Pen.FOUNTAIN),
        )
        val out = drawOrder(input)
        assertEquals(listOf("2", "4", "8", "1", "3", "5", "6", "7", "9"), ids(out))
        assertEquals(input.size, out.size)
    }

    @Test
    fun everyMarkerComesBeforeEveryOtherStroke() {
        val pens = Pen.entries
        val input = (0 until 40).map { stroke("s$it", pens[(it * 7) % pens.size]) }
        val out = drawOrder(input)
        val lastMarker = out.indexOfLast { it.pen == Pen.MARKER }
        val firstOther = out.indexOfFirst { it.pen != Pen.MARKER }
        assertTrue(lastMarker < firstOther)
    }

    @Test
    fun inputListIsNotMutated() {
        val input = listOf(stroke("a", Pen.BALLPEN), stroke("b", Pen.MARKER))
        drawOrder(input)
        assertEquals(listOf("a", "b"), ids(input))
    }
}
