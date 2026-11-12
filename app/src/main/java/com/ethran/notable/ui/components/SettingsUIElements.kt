package com.ethran.notable.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.editor.ui.SelectMenu

/** A row label in the house style: plain sans, dark. */
@Composable
private fun RowLabel(text: String, modifier: Modifier = Modifier, maxLines: Int = 2) =
    Text(text, modifier = modifier, fontFamily = House.Sans, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = House.Ink, maxLines = maxLines)

@Composable
fun <T> SelectorRow(
    label: String,
    options: List<Pair<T, String>>,
    value: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelMaxLines: Int = 2
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowLabel(label, Modifier.weight(1f), labelMaxLines)
        // The current value sits in a round outline, like the buttons on Home.
        Box(Modifier.clip(House.Pill).border(1.5.dp, House.Ink, House.Pill).padding(horizontal = 18.dp, vertical = 8.dp)) {
            SelectMenu(options = options, value = value, onChange = onValueChange)
        }
    }
    SettingsDivider()
}


@Composable
fun SettingToggleRow(
    label: String, value: Boolean, onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(label, Modifier.weight(1f))
        OnOffSwitch(
            checked = value,
            onCheckedChange = onToggle,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
    SettingsDivider()
}

@Composable
fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(House.Grey2.copy(alpha = 0.5f)))
}
