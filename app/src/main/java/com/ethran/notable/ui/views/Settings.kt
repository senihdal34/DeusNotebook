package com.ethran.notable.ui.views

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ethran.notable.BuildConfig
import com.ethran.notable.R
import com.ethran.notable.ui.components.House
import com.ethran.notable.ui.components.SyncSettings
import com.ethran.notable.ui.components.HouseCaps
import com.ethran.notable.ui.components.HouseIconButton
import com.ethran.notable.ui.components.HousePillButton
import com.ethran.notable.ui.components.HouseRule
import com.ethran.notable.ui.components.houseTap
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import com.ethran.notable.data.datastore.AppSettings
import com.ethran.notable.navigation.NavigationDestination
import com.ethran.notable.ui.SnackState
import com.ethran.notable.ui.components.DebugSettings
import com.ethran.notable.ui.components.GeneralSettings
import com.ethran.notable.ui.components.GesturesSettings
import com.ethran.notable.ui.theme.InkaTheme
import com.ethran.notable.ui.viewmodels.GestureRowModel
import com.ethran.notable.ui.viewmodels.SettingsViewModel
import com.ethran.notable.utils.isNext


object SettingsDestination : NavigationDestination {
    override val route = "settings"
}

@Composable
fun SettingsView(
    onBack: () -> Unit,
    goToWelcome: () -> Unit,
    goToSystemInfo: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings = viewModel.settings

    LaunchedEffect(Unit) {
        viewModel.checkUpdate(context, force = false)
    }

    @Suppress("KotlinConstantConditions") val versionString = remember {
        "v${BuildConfig.VERSION_NAME}${if (isNext) " [NEXT]" else ""}"
    }

    SettingsContent(
        versionString = versionString,
        settings = settings,
        isLatestVersion = viewModel.isLatestVersion,
        onBack = onBack,
        goToWelcome = goToWelcome,
        goToSystemInfo = goToSystemInfo,
        onCheckUpdate = { force ->
            viewModel.checkUpdate(context, force)
        },
        onUpdateSettings = { viewModel.updateSettings(it) },
        onClearAllPages = { onComplete -> viewModel.clearAllPages(onComplete) },
        onAppLanguageChange = { language ->
            viewModel.setAppLanguage(context, language) { com.ethran.notable.i18n.restartApp(context) }
        },
        listOfGestures = viewModel.getGestureRows(),
        availableGestures = viewModel.availableGestures
    )
}

/** Lets another screen (the welcome page) open Settings on a given tab: set [request], navigate, Settings takes it once. */
object SettingsTabs {
    const val GENERAL = 0
    const val SYNC = 1
    @Volatile var request: Int? = null
    fun take(): Int? = request.also { request = null }
}

@Composable
fun SettingsContent(
    versionString: String,
    settings: AppSettings,
    isLatestVersion: Boolean,
    onBack: () -> Unit,
    goToWelcome: () -> Unit,
    goToSystemInfo: () -> Unit,
    onCheckUpdate: (Boolean) -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
    onClearAllPages: ((onComplete: () -> Unit) -> Unit)? = null,
    onAppLanguageChange: (com.ethran.notable.i18n.AppLanguage) -> Unit = {},
    selectedTabInitial: Int = 0,
    listOfGestures: List<GestureRowModel> = emptyList(),
    availableGestures: List<Pair<AppSettings.GestureAction?, Any>> = emptyList()
) {
    var selectedTab by remember { mutableIntStateOf(SettingsTabs.take() ?: selectedTabInitial) }
    val tabs = listOf(
        stringResource(R.string.settings_tab_general_name),
        stringResource(R.string.sync_tab_name),
        stringResource(R.string.settings_tab_gestures_name),
        stringResource(R.string.settings_tab_debug_name)
    )

    Surface(
        modifier = Modifier.fillMaxSize(), color = House.Paper
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp)
        ) {
            SettingsTitleBar(versionString, onBack)

            SettingsTabRow(tabs, selectedTab) { selectedTab = it }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                    0 -> GeneralSettings(settings, onUpdateSettings, onClearAllPages, onAppLanguageChange)
                    1 -> SyncSettings(settings, goToGeneral = { selectedTab = 0 })
                    2 -> GesturesSettings(
                        settings, onUpdateSettings, listOfGestures, availableGestures
                    )

                    3 -> DebugSettings(settings, onUpdateSettings, goToWelcome, goToSystemInfo)
                }
            }

            if (selectedTab == 0) {
                Column(
                    modifier = Modifier.padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GitHubSponsorButton(Modifier.padding(vertical = 8.dp))
                    UpdateActions(
                        isLatestVersion = isLatestVersion, onCheckUpdate = onCheckUpdate,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTitleBar(versionString: String, onBack: () -> Unit) {
    // Same header as Home and Reading: logo, serif title, small block on the right; here the back button replaces the logo's place on the left.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HouseIconButton(FeatherIcons.ArrowLeft, stringResource(R.string.settings_back), onClick = onBack)
        Spacer(Modifier.width(18.dp))
        Text(
            text = stringResource(R.string.settings_title),
            fontFamily = House.Serif, fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = (-1).sp,
            color = House.Ink, maxLines = 1, modifier = Modifier.weight(1f)
        )
        HouseCaps(versionString, 12)
    }
    HouseRule(5.dp)
}

@Composable
private fun SettingsTabRow(tabs: List<String>, selectedTab: Int, onTabSelected: (Int) -> Unit) {
    // The same tab strip as Planner | Notes | Favorites | Reading.
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, title ->
            val on = selectedTab == index
            Box(
                Modifier.height(54.dp).houseTap { onTabSelected(index) }
                    .background(if (on) House.Ink else Color.Transparent).padding(horizontal = 26.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    title, fontFamily = House.Sans, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp, color = if (on) Color.White else House.Ink
                )
            }
            if (index < tabs.lastIndex && !on && selectedTab != index + 1) {
                Box(Modifier.width(1.5.dp).height(26.dp).background(House.Grey2))
            }
        }
    }
    HouseRule()
}

@Composable
fun GitHubSponsorButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    HousePillButton(stringResource(R.string.sponsor_button_text), modifier) {
        openInBrowser(context, "https://github.com/sponsors/ethran")
    }
}

@Composable
fun UpdateActions(
    isLatestVersion: Boolean, onCheckUpdate: (Boolean) -> Unit, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (!isLatestVersion) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.app_new_version),
                fontFamily = House.Serif, fontStyle = FontStyle.Italic, fontSize = 22.sp, color = House.Ink
            )
            Spacer(Modifier.height(10.dp))
            HousePillButton(stringResource(R.string.app_see_release), filled = true) {
                openInBrowser(context, "https://github.com/ethran/notable/releases")
            }
        }
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            HousePillButton(stringResource(R.string.app_check_updates)) { onCheckUpdate(true) }
        }
    }
}





fun openInBrowser(context: Context, uriString: String) {
    val urlIntent = Intent(Intent.ACTION_VIEW, uriString.toUri())
    try {
        context.startActivity(urlIntent)
    } catch (_: ActivityNotFoundException) {
        // log and show error
        SnackState.logAndShowError(
            "openInBrowser",
            "No application can handle this request. Please install a web browser.",
            Log::w
        )
    }
}

// ----------------------------------- //
// --------      Previews      ------- //
// ----------------------------------- //


@Preview(showBackground = true)
@Composable
fun SettingsPreviewGeneral() {
    InkaTheme {
        SettingsContent(
            versionString = "v1.0.0",
            settings = AppSettings(version = 1),
            isLatestVersion = true,
            onBack = {},
            goToWelcome = {},
            goToSystemInfo = {},
            onCheckUpdate = {},
            onUpdateSettings = {},
            selectedTabInitial = 0
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreviewGestures() {
    val dummyRows = listOf(
        GestureRowModel(
            R.string.gestures_double_tap_action, null, AppSettings.defaultDoubleTapAction
        ) { }, GestureRowModel(
            R.string.gestures_two_finger_tap_action,
            AppSettings.GestureAction.Undo,
            AppSettings.defaultTwoFingerTapAction
        ) { })
    InkaTheme {
        SettingsContent(
            versionString = "v1.0.0",
            settings = AppSettings(version = 1),
            isLatestVersion = true,
            onBack = {},
            goToWelcome = {},
            goToSystemInfo = {},
            onCheckUpdate = {},
            onUpdateSettings = {},
            selectedTabInitial = 1,
            listOfGestures = dummyRows
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreviewDebug() {
    InkaTheme {
        SettingsContent(
            versionString = "v1.0.0",
            settings = AppSettings(version = 1),
            isLatestVersion = true,
            onBack = {},
            goToWelcome = {},
            goToSystemInfo = {},
            onCheckUpdate = {},
            onUpdateSettings = {},
            selectedTabInitial = 2
        )
    }
}
