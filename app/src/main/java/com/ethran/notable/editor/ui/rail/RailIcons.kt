package com.ethran.notable.editor.ui.rail

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.ethran.notable.editor.rail.RailGlyph
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The line icons of the tool rail: a 24 dp viewport, 2.2 stroke, round caps and joins, black (the button tints them).
 * Each tool has its own silhouette so it can be told apart at a glance: ballpoint (slim diagonal barrel), fountain pen
 * (upright nib), eraser (slanted block), highlighter (chisel marker over a thick stripe), lasso (dashed loop with a
 * tail), line (diagonal with end points). Dashes are drawn as separate short strokes because vectors have no dash effect.
 */
internal object RailIcons {
    private const val STROKE = 2.2f
    private val ink = SolidColor(Color.Black)

    /**
     * Builds a 24 dp icon: [strokes] are drawn as lines, [fills] as solid shapes (path data in SVG syntax).
     * Every path gets its own node list ([addPathNodes]): a shared PathParser hands out its internal list, so each new
     * parse would overwrite the paths added before it and only the last path of an icon would be drawn.
     */
    private fun icon(name: String, strokes: List<String>, fills: List<String> = emptyList(), filledStar: String? = null): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        filledStar?.let {
            b.addPath(addPathNodes(it), fill = ink, stroke = ink, strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
        strokes.forEach {
            b.addPath(addPathNodes(it), stroke = ink, strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
        fills.forEach { b.addPath(addPathNodes(it), fill = ink) }
        return b.build()
    }

    private fun dot(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    /** Dashes along an ellipse, as short polylines; used for the lasso loop. */
    private fun dashedEllipse(cx: Float, cy: Float, rx: Float, ry: Float, dashes: Int, startDeg: Float): String {
        val sb = StringBuilder()
        val step = 2 * PI / dashes
        val start = Math.toRadians(startDeg.toDouble())
        for (i in 0 until dashes) {
            val a0 = start + i * step
            for (k in 0..2) {
                val a = a0 + k * step * 0.28
                val x = cx + rx * cos(a).toFloat()
                val y = cy + ry * sin(a).toFloat()
                sb.append(if (k == 0) "M" else "L").append("%.2f %.2f".format(java.util.Locale.ROOT, x, y))
            }
        }
        return sb.toString()
    }

    private const val STAR = "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z"

    val Back by lazy { icon("rail_back", listOf("M19 12H5", "M11 6l-6 6 6 6")) }

    /** Ballpoint: a slim barrel on the diagonal with a band and a push button. */
    val PenBallpoint by lazy {
        icon("rail_pen_ballpoint", listOf("M16.5 3.5l4 4-12 12-5 1 1-5z", "M13.5 6.5l4 4"))
    }

    /** Fountain pen: an upright nib with shoulders, a slit and a breather hole. */
    val PenFountain by lazy {
        icon("rail_pen_fountain", listOf("M12 21.5L6.5 12 9 3h6l2.5 9z", "M12 21.5V15", "M9.5 3h5"), fills = listOf(dot(12f, 12f, 1.4f)))
    }

    /** Eraser: a slanted block with its rubber end and a line on the ground. */
    val Eraser by lazy {
        icon("rail_eraser", listOf("M9.5 19L3.5 13 14 2.5l6.5 6.5-10 10z", "M8.5 8l7.5 7.5", "M12.5 21.5h8.5"))
    }

    /** Area eraser: the four corners of a selection with a cross inside. */
    val EraserArea by lazy {
        icon(
            "rail_eraser_area",
            listOf(
                "M4 9V5a1 1 0 011-1h4", "M15 4h4a1 1 0 011 1v4", "M20 15v4a1 1 0 01-1 1h-4", "M9 20H5a1 1 0 01-1-1v-4",
                "M9.5 9.5l5 5", "M14.5 9.5l-5 5",
            )
        )
    }

    /** Highlighter: a chisel marker above a thick stripe of colour. */
    val Highlighter by lazy {
        icon(
            "rail_highlighter",
            listOf("M15.5 2.5l6 6-9 9-6-6z", "M6.5 11.5L4 17.5l1.5 1.5 6-2.5"),
            fills = listOf("M3.5 20.5h17v2.5h-17z")
        )
    }

    /** Lasso: a dashed loop with the tail it is drawn with. */
    val Lasso by lazy {
        icon("rail_lasso", listOf(dashedEllipse(12f, 10f, 9f, 6.5f, 9, 140f), "M9.5 16c.5 2.5-.5 4-3 5"))
    }

    val Line by lazy {
        icon("rail_line", listOf("M6 18L18 6"), fills = listOf(dot(5f, 19f, 1.7f), dot(19f, 5f, 1.7f)))
    }

    val LineDashed by lazy {
        icon("rail_line_dashed", listOf("M4.5 19.5l2.5-2.5", "M10.7 13.3l2.6-2.6", "M17 7l2.5-2.5"))
    }

    val Undo by lazy { icon("rail_undo", listOf("M9 14L4 9l5-5", "M4 9h10.5a5.5 5.5 0 010 11H11")) }
    val Redo by lazy { icon("rail_redo", listOf("M15 14l5-5-5-5", "M20 9H9.5a5.5 5.5 0 000 11H13")) }
    val Plus by lazy { icon("rail_plus", listOf("M12 5v14", "M5 12h14")) }
    val Star by lazy { icon("rail_star", listOf(STAR)) }
    val StarFilled by lazy { icon("rail_star_filled", emptyList(), filledStar = STAR) }
    val More by lazy {
        icon("rail_more", emptyList(), fills = listOf(dot(5f, 12f, 1.9f), dot(12f, 12f, 1.9f), dot(19f, 12f, 1.9f)))
    }
    val ChevronLeft by lazy { icon("rail_chevron_left", listOf("M14.5 6l-6 6 6 6")) }
    val ChevronRight by lazy { icon("rail_chevron_right", listOf("M9.5 6l6 6-6 6")) }
    val ChevronUp by lazy { icon("rail_chevron_up", listOf("M6 14.5l6-6 6 6")) }
    val ChevronDown by lazy { icon("rail_chevron_down", listOf("M6 9.5l6 6 6-6")) }

    /** A sheet with a folded corner. */
    val Page by lazy {
        icon("rail_page", listOf("M7 3h7l5 5v11a2 2 0 01-2 2H7a2 2 0 01-2-2V5a2 2 0 012-2z", "M14 3v5h5"))
    }

    fun of(glyph: RailGlyph): ImageVector = when (glyph) {
        RailGlyph.Back -> Back
        RailGlyph.PenBallpoint -> PenBallpoint
        RailGlyph.PenFountain -> PenFountain
        RailGlyph.Eraser -> Eraser
        RailGlyph.EraserArea -> EraserArea
        RailGlyph.Highlighter -> Highlighter
        RailGlyph.Lasso -> Lasso
        RailGlyph.Line -> Line
        RailGlyph.LineDashed -> LineDashed
        RailGlyph.Undo -> Undo
        RailGlyph.Redo -> Redo
        RailGlyph.Plus -> Plus
        RailGlyph.Star -> Star
        RailGlyph.StarFilled -> StarFilled
        RailGlyph.More -> More
        RailGlyph.ChevronLeft -> ChevronLeft
        RailGlyph.ChevronRight -> ChevronRight
        RailGlyph.ChevronUp -> ChevronUp
        RailGlyph.ChevronDown -> ChevronDown
        RailGlyph.Page -> Page
    }
}
