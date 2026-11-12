package com.ethran.notable.ui.components

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ethran.notable.R
import com.ethran.notable.data.datastore.AppSettings
import com.ethran.notable.io.ExportedExcerpts
import com.ethran.notable.io.ExportStatus
import com.ethran.notable.io.SyncthingApps
import com.ethran.notable.ui.views.openInBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Guide for getting the notes onto a computer: where they are written, a five step setup with Syncthing, and how an
 * AI tool reads them. It also shows the live state (folder, number of exported notes, Syncthing installed or not).
 */
@Composable
fun SyncSettings(settings: AppSettings, goToGeneral: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var status by remember { mutableStateOf<ExportStatus?>(null) }
    var syncthing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(refresh, settings.obsidianInboxPath) {
        withContext(Dispatchers.IO) {
            status = ExportStatus.of(ExportedExcerpts.inboxDir(settings.obsidianInboxPath))
            syncthing = SyncthingApps.installed { pkg -> isInstalled(context, pkg) }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.sync_intro),
            fontFamily = House.Serif, fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 27.sp, color = House.Ink,
            modifier = Modifier.padding(bottom = 18.dp)
        )
        StatusCard(status, syncthing != null)
        Spacer(Modifier.height(10.dp))
        Step(1, R.string.sync_step1_title, R.string.sync_step1_body) {
            HousePillButton(stringResource(R.string.sync_step1_button), onClick = goToGeneral)
        }
        Step(2, R.string.sync_step2_title, R.string.sync_step2_body) {
            val installed = syncthing
            if (installed != null) {
                HousePillButton(stringResource(R.string.sync_step2_open), filled = true) { openApp(context, installed) }
            } else {
                HousePillButton(stringResource(R.string.sync_step2_get), filled = true) { openInBrowser(context, SyncthingApps.ANDROID_DOWNLOAD_URL) }
            }
        }
        Step(3, R.string.sync_step3_title, R.string.sync_step3_body)
        Step(4, R.string.sync_step4_title, R.string.sync_step4_body) {
            HousePillButton(stringResource(R.string.sync_step4_button)) { openInBrowser(context, SyncthingApps.COMPUTER_DOWNLOAD_URL) }
        }
        Step(5, R.string.sync_step5_title, R.string.sync_step5_body)
        Text(
            stringResource(R.string.sync_private_note),
            fontFamily = House.Sans, fontSize = 15.sp, color = House.Grey1,
            modifier = Modifier.padding(vertical = 14.dp)
        )
    }
}

@Composable
private fun StatusCard(status: ExportStatus?, syncthingInstalled: Boolean) {
    Column(
        Modifier.fillMaxWidth().border(1.5.dp, House.Ink, RoundedCornerShape(20.dp)).padding(20.dp)
    ) {
        HouseCaps(stringResource(R.string.sync_status_title), 12, House.Ink)
        Spacer(Modifier.height(10.dp))
        HouseCaps(stringResource(R.string.sync_status_folder), 11, House.Grey1)
        Text(
            status?.folder?.path ?: "…",
            fontFamily = House.Sans, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = House.Ink,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        val last = status?.lastExportMillis
        val line = when {
            status == null -> ""
            last == null -> stringResource(R.string.sync_status_never)
            else -> stringResource(R.string.sync_status_count, status.noteCount) + "  ·  " +
                stringResource(R.string.sync_status_last, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last)))
        }
        if (line.isNotEmpty()) Text(line, fontFamily = House.Sans, fontSize = 17.sp, color = House.Ink, modifier = Modifier.padding(bottom = 6.dp))
        Text(
            stringResource(if (syncthingInstalled) R.string.sync_status_syncthing_on else R.string.sync_status_syncthing_off),
            fontFamily = House.Sans, fontSize = 17.sp, color = House.Grey1
        )
    }
}

@Composable
private fun Step(number: Int, title: Int, body: Int, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.Start) {
        Text(
            number.toString(),
            fontFamily = House.Serif, fontWeight = FontWeight.Bold, fontSize = 44.sp, color = House.Ink,
            modifier = Modifier.width(52.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontFamily = House.Sans, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = House.Ink)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(body), fontFamily = House.Sans, fontSize = 17.sp, lineHeight = 24.sp, color = House.Grey1)
            if (action != null) {
                Spacer(Modifier.height(12.dp))
                action()
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    SettingsDivider()
}

private fun isInstalled(context: Context, pkg: String): Boolean =
    try { context.packageManager.getPackageInfo(pkg, 0); true } catch (_: PackageManager.NameNotFoundException) { false }

private fun openApp(context: Context, pkg: String) {
    context.packageManager.getLaunchIntentForPackage(pkg)?.let {
        context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
