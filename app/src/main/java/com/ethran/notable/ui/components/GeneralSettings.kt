package com.ethran.notable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.R
import com.ethran.notable.data.datastore.AppSettings
import com.ethran.notable.io.VaultTagScanner


@Composable
fun GeneralSettings(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onClearAllPages: ((onComplete: () -> Unit) -> Unit)? = null,
    onAppLanguageChange: (com.ethran.notable.i18n.AppLanguage) -> Unit = {}
) {
    Column {
        LanguageSettings(onAppLanguageChange)

        // Capture settings
        InboxCaptureSettings(settings, onSettingsChange)

        Spacer(modifier = Modifier.height(8.dp))

        SelectorRow(
            label = stringResource(R.string.toolbar_position), options = listOf(
                AppSettings.Position.Top to stringResource(R.string.toolbar_position_top),
                AppSettings.Position.Bottom to stringResource(
                    R.string.toolbar_position_bottom
                )
            ), value = settings.toolbarPosition, onValueChange = { newPosition ->
                onSettingsChange(settings.copy(toolbarPosition = newPosition))
            })

        SettingToggleRow(
            label = stringResource(R.string.start_in_planner),
            value = settings.startInPlanner,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(startInPlanner = isChecked))
            })

        SettingToggleRow(
            label = stringResource(R.string.use_onyx_neotools_may_cause_crashes),
            value = settings.neoTools,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(neoTools = isChecked))
            })

        SettingToggleRow(
            label = stringResource(R.string.enable_scribble_to_erase),
            value = settings.scribbleToEraseEnabled,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(scribbleToEraseEnabled = isChecked))
            })

        SettingToggleRow(
            label = stringResource(R.string.enable_smooth_scrolling),
            value = settings.smoothScroll,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(smoothScroll = isChecked))
            })

        SettingToggleRow(
            label = stringResource(R.string.continuous_zoom),
            value = settings.continuousZoom,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(continuousZoom = isChecked))
            })
        SettingToggleRow(
            label = stringResource(R.string.continuous_stroke_slider),
            value = settings.continuousStrokeSlider,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(continuousStrokeSlider = isChecked))
            })
        SettingToggleRow(
            label = stringResource(R.string.monochrome_mode) + " " + stringResource(R.string.work_in_progress),
            value = settings.monochromeMode,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(monochromeMode = isChecked))
            })

        SettingToggleRow(
            label = stringResource(R.string.paginate_pdf),
            value = settings.paginatePdf,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(paginatePdf = isChecked))
            })

        SettingToggleRow(
            label = stringResource(R.string.preview_pdf_pagination),
            value = settings.visualizePdfPagination,
            onToggle = { isChecked ->
                onSettingsChange(settings.copy(visualizePdfPagination = isChecked))
            })

        if (onClearAllPages != null) {
            Spacer(modifier = Modifier.height(16.dp))
            ClearAllPagesButton(onClearAllPages)
        }
    }
}

@Composable
private fun ClearAllPagesButton(onClearAllPages: (onComplete: () -> Unit) -> Unit) {
    var confirmState by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }

    if (isClearing) {
        Text(
            stringResource(R.string.settings_clearing),
            fontFamily = House.Serif, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 20.sp, color = House.Grey1,
            modifier = Modifier.padding(vertical = 12.dp)
        )
    } else if (confirmState) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.settings_delete_all_q),
                fontFamily = House.Sans, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = House.Ink,
                modifier = Modifier.weight(1f)
            )
            HousePillButton(stringResource(R.string.settings_yes_delete_all), filled = true) {
                isClearing = true
                onClearAllPages {
                    isClearing = false
                    confirmState = false
                }
            }
            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
            HousePillButton(stringResource(R.string.action_cancel)) { confirmState = false }
        }
    } else {
        HousePillButton(stringResource(R.string.settings_clear_all_pages)) { confirmState = true }
    }
}

@Composable
private fun InboxCaptureSettings(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var pathInput by remember { mutableStateOf(settings.obsidianInboxPath) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, House.Ink, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Text(
            stringResource(R.string.settings_capture_title),
            fontFamily = House.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = House.Ink
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            stringResource(R.string.settings_capture_description),
            fontFamily = House.Serif, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp,
            color = House.Grey1
        )

        Spacer(modifier = Modifier.height(16.dp))

        HouseCaps(stringResource(R.string.settings_vault_folder), 12, House.Ink)

        Spacer(modifier = Modifier.height(6.dp))

        BasicTextField(
            value = pathInput,
            onValueChange = { pathInput = it },
            textStyle = TextStyle(fontFamily = House.Sans, fontSize = 18.sp, color = House.Ink),
            singleLine = true,
            cursorBrush = SolidColor(House.Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onSettingsChange(settings.copy(obsidianInboxPath = pathInput))
                VaultTagScanner.refreshCache(pathInput)
                focusManager.clearFocus()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, House.Ink, House.Pill)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            stringResource(R.string.settings_vault_folder_hint),
            fontFamily = House.Sans, fontSize = 14.sp, color = House.Grey1
        )
    }
}
