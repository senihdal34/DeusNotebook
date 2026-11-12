package com.ethran.notable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.i18n.AppStrings

/**
 * The look shared by Home, Reading and Settings: black, white and two greys on warm paper, serif titles, small
 * spaced capitals, round outlined buttons, thick black rules. No shadows, no gradients, no animation (e-ink).
 */
object House {
    val Ink = Color(0xFF111111)
    val Grey1 = Color(0xFF555555)
    val Grey2 = Color(0xFF999999)
    val Paper = Color(0xFFF6F6F2)
    val Sans = FontFamily.SansSerif
    val Serif = FontFamily.Serif
    val Pill = RoundedCornerShape(50)
}

fun Modifier.houseTap(onClick: () -> Unit): Modifier =
    clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick)

/** Small spaced capitals, like the labels on Home. */
@Composable
fun HouseCaps(text: String, size: Int = 12, color: Color = House.Grey1, weight: FontWeight = FontWeight.Bold, modifier: Modifier = Modifier) =
    Text(
        text = text.uppercase(AppStrings.locale), fontFamily = House.Sans, fontSize = size.sp, fontWeight = weight,
        letterSpacing = (size * 0.14f).sp, color = color, modifier = modifier
    )

@Composable
fun HouseRule(thickness: Dp = 1.5.dp, color: Color = House.Ink) =
    Box(Modifier.fillMaxWidth().height(thickness).background(color))

/** Round outlined button with an icon (settings, back). */
@Composable
fun HouseIconButton(icon: ImageVector, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(46.dp).clip(House.Pill).border(1.5.dp, House.Ink, House.Pill).houseTap(onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = description, tint = House.Ink, modifier = Modifier.size(24.dp)) }
}

/** Outlined pill with spaced capitals; [filled] turns it black (the one main action). */
@Composable
fun HousePillButton(label: String, modifier: Modifier = Modifier, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp).clip(House.Pill).border(1.5.dp, House.Ink, House.Pill)
            .background(if (filled) House.Ink else Color.Transparent).houseTap(onClick).padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) { HouseCaps(label, 12, if (filled) Color.White else House.Ink) }
}
