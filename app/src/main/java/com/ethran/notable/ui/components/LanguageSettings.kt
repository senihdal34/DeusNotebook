package com.ethran.notable.ui.components

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.R
import com.ethran.notable.i18n.AppLanguage
import com.ethran.notable.i18n.LanguagePrefs
import com.ethran.notable.i18n.WritingLanguage
import com.ethran.notable.i18n.WritingLanguageAvailability
import com.ethran.notable.io.OnyxHWREngine

/**
 * The two language choices. App language is the language of menus, labels and dates; writing language is the
 * language of the handwriting recognizer. They are independent: e.g. English app, Turkish handwriting.
 */
@Composable
fun LanguageSettings(onAppLanguageChange: (AppLanguage) -> Unit) {
    val context = LocalContext.current
    var app by remember { mutableStateOf(LanguagePrefs.app(context)) }
    var writing by remember { mutableStateOf(LanguagePrefs.writing(context)) }

    SelectorRow(
        label = stringResource(R.string.app_language),
        options = AppLanguage.entries.map { it to it.nativeName },
        value = app,
        onValueChange = { chosen ->
            if (chosen != app) {
                app = chosen
                onAppLanguageChange(chosen)
            }
        }
    )
    // The installed set is read again whenever the screen comes back (the user may have downloaded a language).
    var installed by remember { mutableStateOf(WritingLanguageAvailability.installed()) }
    var pending by remember { mutableStateOf(LanguagePrefs.pendingWriting(context)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                installed = WritingLanguageAvailability.installed()
                WritingLanguageAvailability.promotePending(context, installed)?.let { writing = it }
                pending = LanguagePrefs.pendingWriting(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val download = stringResource(R.string.writing_language_download)
    val ordered = remember(installed) {
        // Languages the tablet can recognize first, the rest after them.
        WritingLanguage.all.sortedBy { if (WritingLanguageAvailability.isAvailable(it.code, installed)) 0 else 1 }
    }
    SelectorRow(
        label = stringResource(R.string.writing_language),
        options = ordered.map {
            it to if (WritingLanguageAvailability.isAvailable(it.code, installed)) it.nativeName else "${it.nativeName} ($download)"
        },
        value = pending ?: writing,
        onValueChange = { chosen ->
            if (WritingLanguageAvailability.isAvailable(chosen.code, installed)) {
                // Installed: select it now.
                LanguagePrefs.setPendingWriting(context, null)
                LanguagePrefs.setWriting(context, chosen)
                OnyxHWREngine.setLanguage(chosen.code)
                writing = chosen
                pending = null
            } else {
                // Not on the tablet: keep the current language, remember the wish and send the user to the download.
                LanguagePrefs.setPendingWriting(context, chosen)
                pending = chosen
                WritingLanguageAvailability.openBooxNotes(context)
            }
        }
    )
    val waiting = pending
    Text(
        text = if (waiting != null) stringResource(R.string.writing_language_waiting_hint, waiting.nativeName)
        else stringResource(R.string.writing_language_hint),
        fontFamily = House.Sans, fontSize = 15.sp, color = House.Grey1,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 10.dp)
    )
    if (waiting != null) {
        HousePillButton(stringResource(R.string.writing_language_open_boox), Modifier.padding(start = 4.dp, bottom = 10.dp)) {
            WritingLanguageAvailability.openBooxNotes(context)
        }
    }
}
