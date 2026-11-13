package com.ethran.notable.editor.drawing

import com.ethran.notable.data.db.Stroke
import com.ethran.notable.editor.utils.Pen

/**
 * Order in which the strokes of a page are painted.
 *
 * Like a real highlighter, every MARKER stroke goes under the ink: all markers first, then every
 * other stroke (ballpoint, fountain, line, dashed ...). It is a stable partition, so each group
 * keeps its stored order. The pen type decides it at draw time; stored rows are never reordered.
 *
 * Every path that paints strokes (page redraw, selection bitmap, thumbnails, exports) must go
 * through this function so none of them forgets the rule.
 */
fun drawOrder(strokes: List<Stroke>): List<Stroke> {
    if (strokes.size < 2) return strokes
    val (markers, others) = strokes.partition { it.pen == Pen.MARKER }
    return if (markers.isEmpty() || others.isEmpty()) strokes else markers + others
}
