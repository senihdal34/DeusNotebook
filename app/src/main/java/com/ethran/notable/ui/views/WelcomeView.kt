package com.ethran.notable.ui.views

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ethran.notable.PACKAGE_NAME
import com.ethran.notable.R
import com.ethran.notable.editor.utils.getCurRefreshModeString
import com.ethran.notable.editor.utils.isRecommendedRefreshMode
import com.ethran.notable.editor.utils.setRecommendedMode
import com.ethran.notable.i18n.LanguagePrefs
import com.ethran.notable.i18n.restartApp
import com.ethran.notable.navigation.NavigationDestination
import com.ethran.notable.ui.components.House
import com.ethran.notable.ui.components.HouseCaps
import com.ethran.notable.ui.components.HousePillButton
import com.ethran.notable.ui.components.HouseRule
import com.ethran.notable.ui.components.LanguageSettings
import com.ethran.notable.ui.viewmodels.WelcomeViewModel
import com.ethran.notable.utils.hasFilePermission
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


object WelcomeDestination : NavigationDestination {
    override val route = "welcome"
}

@Composable
fun WelcomeView(
    goToLibrary: () -> Unit = {},
    goToSyncSetup: () -> Unit = {},
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var filePermissionGranted by remember { mutableStateOf(hasFilePermission(context)) }
    var recommendedRefreshMode by remember { mutableStateOf(isRecommendedRefreshMode()) }
    var refreshModeString by remember { mutableStateOf(getCurRefreshModeString()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                filePermissionGranted = hasFilePermission(context)
                recommendedRefreshMode = isRecommendedRefreshMode()
                refreshModeString = getCurRefreshModeString()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    @RequiresApi(Build.VERSION_CODES.R)
    fun requestManageAllFilesPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = Uri.fromParts("package", PACKAGE_NAME, null)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    context as Activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1001
                )
            }
        } else if (!Environment.isExternalStorageManager()) {
            requestManageAllFilesPermission()
        }
    }

    // The welcome page is done: it must not come back after the next restart.
    fun finishWelcome() {
        LanguagePrefs.setResumeWelcome(context, false)
        scope.launch(Dispatchers.IO) { viewModel.removeWelcome() }
    }

    WelcomeContent(
        progress = WelcomeProgress(filePermissionGranted, recommendedRefreshMode),
        refreshModeString = refreshModeString,
        // The same two dropdowns as in Settings. A new app language restarts the app; the page opens again.
        languageContent = {
            LanguageSettings(onAppLanguageChange = { language ->
                viewModel.setAppLanguage(context, language) { restartApp(context) }
            })
        },
        onFilePermissionRequest = { requestPermissions() },
        onRefreshModeRequest = { setRecommendedMode() },
        onContinue = {
            finishWelcome()
            goToLibrary()
        },
        onSetupSync = {
            finishWelcome()
            goToSyncSetup()
        }
    )
}

/** Stateless page: easy to preview, and the step logic it uses is in WelcomeSteps.kt. */
@Composable
fun WelcomeContent(
    progress: WelcomeProgress,
    refreshModeString: String,
    languageContent: @Composable () -> Unit,
    onFilePermissionRequest: () -> Unit,
    onRefreshModeRequest: () -> Unit,
    onContinue: () -> Unit,
    onSetupSync: () -> Unit = {}
) {
    val appName = stringResource(R.string.app_name)
    Surface(modifier = Modifier.fillMaxSize(), color = House.Paper) {
        Column(Modifier.fillMaxSize()) {
            // Scrolls in portrait and landscape; the column keeps a readable width on a wide screen.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp)
                ) {
                    WelcomeHeader(appName)

                    WelcomeStepBlock(WelcomeStep.LANGUAGE, stepStatus(WelcomeStep.LANGUAGE, progress), stringResource(R.string.welcome_language_title)) {
                        BodyText(stringResource(R.string.welcome_language_body))
                        languageContent()
                    }

                    WelcomeStepBlock(WelcomeStep.FILE_ACCESS, stepStatus(WelcomeStep.FILE_ACCESS, progress), stringResource(R.string.welcome_view_file_access)) {
                        BodyText(stringResource(R.string.welcome_view_file_access_explanation, appName))
                        val granted = progress.fileAccessGranted
                        HousePillButton(
                            if (granted) stringResource(R.string.welcome_view_permissions_granted)
                            else stringResource(R.string.welcome_view_permissions_button),
                            Modifier.padding(top = 14.dp), enabled = !granted, onClick = onFilePermissionRequest
                        )
                    }

                    WelcomeStepBlock(WelcomeStep.REFRESH_MODE, stepStatus(WelcomeStep.REFRESH_MODE, progress), stringResource(R.string.welcome_view_refresh_mode)) {
                        BodyText(stringResource(R.string.welcome_view_refresh_mode_details))
                        val good = progress.refreshModeRecommended
                        HousePillButton(
                            if (good) stringResource(R.string.welcome_view_refresh_mode_applied, refreshModeString)
                            else stringResource(R.string.welcome_view_refresh_mode_set_hd_mode, refreshModeString),
                            Modifier.padding(top = 14.dp), enabled = !good, onClick = onRefreshModeRequest
                        )
                    }

                    WelcomeStepBlock(WelcomeStep.UNFREEZE, stepStatus(WelcomeStep.UNFREEZE, progress), stringResource(R.string.welcome_view_prevent_frozen, appName)) {
                        BodyText(stringResource(R.string.welcome_view_prevent_frozen_details))
                        Column(
                            Modifier.padding(top = 14.dp).fillMaxWidth()
                                .border(1.5.dp, House.Ink, RoundedCornerShape(14.dp)).padding(16.dp)
                        ) {
                            HouseCaps(stringResource(R.string.welcome_view_prevent_frozen_instructions_title), 12, House.Ink)
                            Spacer(Modifier.height(8.dp))
                            BodyText(stringResource(R.string.welcome_view_prevent_frozen_instructions, appName))
                        }
                        Text(
                            stringResource(R.string.welcome_view_prevent_frozen_recommended),
                            fontFamily = House.Sans, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = House.Ink,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    WelcomeStepBlock(WelcomeStep.SYNC, stepStatus(WelcomeStep.SYNC, progress), stringResource(R.string.welcome_sync_title), optional = true) {
                        BodyText(stringResource(R.string.welcome_sync_body))
                        val enabled = canSetUpSync(progress)
                        HousePillButton(
                            stringResource(R.string.welcome_sync_button),
                            Modifier.padding(top = 14.dp), enabled = enabled, onClick = onSetupSync
                        )
                        if (!enabled) {
                            Text(
                                stringResource(R.string.welcome_sync_needs_access),
                                fontFamily = House.Sans, fontSize = 15.sp, color = House.Grey1,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    WelcomeStepBlock(WelcomeStep.GUIDE, stepStatus(WelcomeStep.GUIDE, progress), stringResource(R.string.welcome_view_quick_start_title), last = true) {
                        GuideSection(R.string.welcome_view_quick_start_navigation, R.string.welcome_view_quick_start_navigation_details)
                        GuideSection(R.string.welcome_view_quick_start_selection_editing_title, R.string.welcome_view_quick_start_selection_editing_details)
                        GuideSection(R.string.welcome_view_quick_start_tips_title, R.string.welcome_view_quick_start_tips_details, appName)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Always in reach, whatever the scroll position.
            HouseRule(1.5.dp)
            Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                val ready = canContinue(progress)
                HousePillButton(
                    if (ready) stringResource(R.string.welcome_view_continue) else stringResource(R.string.welcome_view_complete_setup_first),
                    Modifier.widthIn(max = 800.dp).fillMaxWidth(), filled = true, enabled = ready, onClick = onContinue
                )
            }
        }
    }
}

@Composable
private fun WelcomeHeader(appName: String) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp)) {
        Text(
            stringResource(R.string.welcome_view_title, appName),
            fontFamily = House.Serif, fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = (-1).sp, color = House.Ink
        )
        Spacer(Modifier.height(8.dp))
        BodyText(stringResource(R.string.welcome_view_subtitle))
        Spacer(Modifier.height(16.dp))
        HouseRule(5.dp)
    }
}

/**
 * One numbered step: a round marker (a check when the app can see the step is done), a small "Step n" label, a serif
 * title, then the step's own content under the title. Steps are separated by a hairline.
 */
@Composable
private fun WelcomeStepBlock(
    step: WelcomeStep,
    status: StepStatus,
    title: String,
    optional: Boolean = false,
    last: Boolean = false,
    content: @Composable () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 22.dp)) {
        val done = status == StepStatus.DONE
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(if (done) House.Ink else Color.Transparent)
                .border(1.5.dp, House.Ink, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Icon(FeatherIcons.Check, contentDescription = stringResource(R.string.welcome_step_done), tint = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(step.number.toString(), fontFamily = House.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = House.Ink)
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HouseCaps(stringResource(R.string.welcome_step_label, step.number), 12)
                if (optional) {
                    Spacer(Modifier.width(10.dp))
                    HouseCaps(stringResource(R.string.welcome_optional), 12, House.Grey2)
                }
            }
            Text(
                title, fontFamily = House.Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = House.Ink,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            content()
        }
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(House.Grey2.copy(alpha = 0.5f)))
}

@Composable
private fun BodyText(text: String, modifier: Modifier = Modifier) =
    Text(text, fontFamily = House.Sans, fontSize = 17.sp, lineHeight = 25.sp, color = House.Ink, modifier = modifier)

@Composable
private fun GuideSection(titleRes: Int, detailsRes: Int, vararg args: Any) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        HouseCaps(stringResource(titleRes), 12, House.Ink)
        Spacer(Modifier.height(6.dp))
        BodyText(stringResource(detailsRes, *args))
    }
}


// ----------------------------------- //
// --------      Previews      ------- //
// ----------------------------------- //

@Preview(showBackground = true, name = "Setup Required")
@Composable
fun WelcomePreviewSetup() {
    MaterialTheme {
        WelcomeContent(
            progress = WelcomeProgress(fileAccessGranted = false, refreshModeRecommended = false),
            refreshModeString = "Normal",
            languageContent = { BodyText("English / English") },
            onFilePermissionRequest = {},
            onRefreshModeRequest = {},
            onContinue = {}
        )
    }
}

@Preview(showBackground = true, name = "Ready to Go")
@Composable
fun WelcomePreviewReady() {
    MaterialTheme {
        WelcomeContent(
            progress = WelcomeProgress(fileAccessGranted = true, refreshModeRecommended = true),
            refreshModeString = "Extreme",
            languageContent = { BodyText("English / English") },
            onFilePermissionRequest = {},
            onRefreshModeRequest = {},
            onContinue = {}
        )
    }
}
