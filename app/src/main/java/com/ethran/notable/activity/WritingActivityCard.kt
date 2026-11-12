package com.ethran.notable.activity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ethran.notable.R
import com.ethran.notable.i18n.AppStrings
import java.time.format.TextStyle as MonthStyle
import java.util.Locale

// Same palette as the Notes screen: black, white and greys, no colour, no shadows, no animation (e-ink).
private val Ink = Color(0xFF111111)
private val Grey1 = Color(0xFF555555)
private val Paper = Color(0xFFF6F6F2)
private val Sans = FontFamily.SansSerif
private val Serif = FontFamily.Serif
private val Pill = RoundedCornerShape(50)

/** Day cell colours for levels 0..4, light to dark. */
private val Levels = listOf(Color(0xFFE3E3E0), Color(0xFFC4C4C0), Color(0xFF8E8E8B), Color(0xFF4A4A48), Ink)

private val MaxCell = 18.dp
private val CellGap = 4.dp
private val LabelRow = 18.dp

/**
 * The Writing activity card of the Notes screen: a heatmap of the days with ink, a period selector and three numbers.
 * It reads its own view model and counts again whenever Home is resumed (back from a note, for instance).
 */
@Composable
fun WritingActivityCard(modifier: Modifier = Modifier, viewModel: WritingActivityViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    WritingActivityCard(ui, viewModel::selectPeriod, modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WritingActivityCard(ui: ActivityUi, onSelect: (ActivityPeriod) -> Unit, modifier: Modifier) {
    val view = ui.view
    val locale = AppStrings.locale
    val phrase = when (val p = ui.period) {
        is ActivityPeriod.Months -> stringResource(R.string.activity_period_months, p.count)
        is ActivityPeriod.Year -> stringResource(R.string.activity_period_year, p.year)
    }
    val subtitle = pluralStringResource(R.plurals.activity_subtitle, view.daysWithInk, view.daysWithInk, phrase)
    val gridDescription = stringResource(R.string.activity_grid_description) + ". " + subtitle

    Column(modifier.clip(RoundedCornerShape(14.dp)).border(1.5.dp, Ink, RoundedCornerShape(14.dp)).background(Paper).padding(horizontal = 20.dp, vertical = 16.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.activity_title), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = Ink, maxLines = 1)
                Text(subtitle, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 18.sp, color = Grey1, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
            }
            PeriodChips(ui, onSelect)
        }
        Spacer(Modifier.height(10.dp))
        val labels = remember(view.grid.months, locale) {
            view.grid.months.map { it.column to it.month.getDisplayName(MonthStyle.SHORT, locale) }
        }
        Heatmap(view.grid, labels, Modifier.fillMaxWidth().semantics { contentDescription = gridDescription })
        Spacer(Modifier.height(10.dp))
        if (ui.loaded && view.isEmpty) {
            Text(stringResource(R.string.activity_empty), fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 20.sp, color = Grey1, modifier = Modifier.padding(bottom = 8.dp))
        }
        FlowRow(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically
        ) {
            view.currentStreak?.let { Stat(it.toString(), stringResource(R.string.activity_stat_streak)) }
            Stat(view.longestStreak.toString(), stringResource(R.string.activity_stat_longest))
            Stat(formatPerWeek(view.daysPerWeek, locale), stringResource(R.string.activity_stat_per_week))
            Legend()
        }
    }
}

private fun formatPerWeek(value: Double, locale: Locale): String = String.format(locale, "%.1f", value)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodChips(ui: ActivityUi, onSelect: (ActivityPeriod) -> Unit) {
    val description = stringResource(R.string.activity_period_selector)
    val choices = remember(ui.years) { ActivityPeriod.MONTH_CHOICES + ui.years.map { ActivityPeriod.Year(it) } }
    FlowRow(
        Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        choices.forEach { choice ->
            val label = when (choice) {
                is ActivityPeriod.Months -> stringResource(R.string.activity_chip_months, choice.count)
                is ActivityPeriod.Year -> choice.year.toString()
            }
            val on = choice == ui.period
            Box(
                Modifier.height(38.dp).clip(Pill).border(1.5.dp, Ink, Pill).background(if (on) Ink else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(choice) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label.uppercase(AppStrings.locale), fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp, color = if (on) Color.White else Ink)
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Ink)
        Text(label, fontFamily = Serif, fontSize = 20.sp, color = Grey1, modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun Legend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.activity_legend_less).uppercase(AppStrings.locale), fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.5.sp, color = Grey1)
        Levels.forEach { Box(Modifier.size(16.dp).background(it)) }
        Text(stringResource(R.string.activity_legend_more).uppercase(AppStrings.locale), fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.5.sp, color = Grey1)
    }
}

/** The whole grid is one Canvas: no composable per cell, and it is drawn again only when the grid or the labels change. */
@Composable
private fun Heatmap(grid: WeekGrid, labels: List<Pair<Int, String>>, modifier: Modifier) {
    val measurer: TextMeasurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Grey1) }
    val measured = remember(labels, measurer) {
        labels.map { (column, text) -> column to measurer.measure(text, labelStyle, maxLines = 1, softWrap = false) }
    }
    Canvas(modifier.height(LabelRow + (MaxCell + CellGap) * 7)) {
        if (grid.columns == 0) return@Canvas
        val gap = CellGap.toPx()
        val cell = minOf(MaxCell.toPx(), (size.width - gap * (grid.columns - 1)) / grid.columns)
        val top = LabelRow.toPx()
        val radius = CornerRadius(2.dp.toPx())
        for ((column, text) in measured) {
            val x = (column * (cell + gap)).coerceAtMost(size.width - text.size.width)
            drawText(text, topLeft = Offset(x, 0f))
        }
        val cellSize = Size(cell, cell)
        for (i in grid.levels.indices) {
            val level = grid.levels[i]
            if (level == WritingActivityStats.OUTSIDE) continue
            val origin = Offset((i / 7) * (cell + gap), top + (i % 7) * (cell + gap))
            drawRoundRect(Levels[level.coerceIn(0, Levels.lastIndex)], origin, cellSize, radius)
        }
        if (grid.todayCell >= 0) {
            // Today: a black ring around the cell, drawn in the gap so it also shows on a black cell.
            val ring = 2.dp.toPx()
            val origin = Offset((grid.todayCell / 7) * (cell + gap) - ring / 2, top + (grid.todayCell % 7) * (cell + gap) - ring / 2)
            drawRoundRect(Ink, origin, Size(cell + ring, cell + ring), radius, style = Stroke(ring))
        }
    }
}
