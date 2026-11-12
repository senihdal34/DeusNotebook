// SPDX-License-Identifier: AGPL-3.0-only
package com.ethran.notable.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethran.notable.BuildConfig
import com.ethran.notable.R
import com.ethran.notable.navigation.NavigationDestination
import com.ethran.notable.ui.components.House
import com.ethran.notable.ui.components.HouseCaps
import com.ethran.notable.ui.components.HouseIconButton
import com.ethran.notable.ui.components.HouseRule
import com.ethran.notable.ui.components.houseTap
import com.ethran.notable.ui.theme.InkaTheme
import com.ethran.notable.utils.LegalInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft

object AboutDestination : NavigationDestination {
    override val route = "about"
}

/**
 * About and legal notices. Always reachable (from Settings and from the Reading tab menu), needs no account and no
 * admin rights. It shows the notice that BookOrbit's additional terms require (section 1), the statement for modified
 * versions (section 3) and where the complete Corresponding Source of this exact version is (section 2, AGPL-3.0
 * section 13). The texts are English in every language on purpose, see values/strings_legal.xml.
 */
@Composable
fun AboutView(onBack: () -> Unit) {
    val context = LocalContext.current
    AboutContent(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        gitSha = BuildConfig.GIT_SHA,
        onBack = onBack,
        onOpenUrl = { openInBrowser(context, it) }
    )
}

@Composable
fun AboutContent(
    versionName: String,
    versionCode: Int,
    gitSha: String?,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val commit = LegalInfo.commitOrNull(gitSha)
    val commitUrl = LegalInfo.commitUrl(gitSha)
    Surface(Modifier.fillMaxSize(), color = House.Paper) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HouseIconButton(FeatherIcons.ArrowLeft, stringResource(R.string.legal_back), onClick = onBack)
                Spacer(Modifier.width(18.dp))
                Text(
                    stringResource(R.string.legal_title),
                    fontFamily = House.Serif, fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = (-1).sp,
                    color = House.Ink, maxLines = 2, modifier = Modifier.weight(1f)
                )
            }
            HouseRule(5.dp)

            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Column(Modifier.widthIn(max = 880.dp).padding(bottom = 40.dp)) {
                    Section(stringResource(R.string.legal_app_heading)) {
                        Text(
                            stringResource(R.string.app_name), fontFamily = House.Serif, fontWeight = FontWeight.Bold,
                            fontSize = 26.sp, color = House.Ink
                        )
                        Body(stringResource(R.string.legal_version, versionName, versionCode))
                        if (commit != null && commitUrl != null) {
                            LinkText(stringResource(R.string.legal_commit, commit), commitUrl, onOpenUrl)
                        } else {
                            Body(stringResource(R.string.legal_commit_unknown))
                        }
                        Body(stringResource(R.string.legal_app_license))
                        LinkText(stringResource(R.string.legal_license_link), LegalInfo.blobUrl("LICENSE"), onOpenUrl)
                    }

                    Section(stringResource(R.string.legal_source_heading)) {
                        Body(stringResource(R.string.legal_source_text))
                        LinkText(LegalInfo.SOURCE_URL, LegalInfo.SOURCE_URL, onOpenUrl)
                    }

                    // The notice required by BookOrbit's additional terms, section 1. Keep the wording as it is.
                    Section(null) {
                        LinkText(
                            stringResource(R.string.legal_bookorbit_title), LegalInfo.BOOKORBIT_URL, onOpenUrl,
                            serif = true, bold = true, size = 30
                        )
                        Body(stringResource(R.string.legal_bookorbit_copyright))
                        Body(stringResource(R.string.legal_bookorbit_contributors))
                        LinkText(stringResource(R.string.legal_bookorbit_original), LegalInfo.BOOKORBIT_URL, onOpenUrl)
                        Body(stringResource(R.string.legal_bookorbit_license))
                        Spacer(Modifier.height(14.dp))
                        Body(stringResource(R.string.legal_bookorbit_modified), bold = true)
                        Body(stringResource(R.string.legal_bookorbit_modified_on, LegalInfo.MODIFIED_ON))
                        Body(stringResource(R.string.legal_bookorbit_scope))
                        LinkText(
                            stringResource(R.string.legal_bookorbit_terms_link),
                            LegalInfo.blobUrl("licenses/bookorbit/ADDITIONAL_TERMS.md"), onOpenUrl
                        )
                    }

                    Section(stringResource(R.string.legal_upstream_heading)) {
                        Body(stringResource(R.string.legal_upstream_text))
                        LinkText(stringResource(R.string.legal_upstream_notable), LegalInfo.NOTABLE_URL, onOpenUrl)
                        LinkText(stringResource(R.string.legal_upstream_aragonite), LegalInfo.ARAGONITE_URL, onOpenUrl)
                        LinkText(stringResource(R.string.legal_notice_link), LegalInfo.blobUrl("NOTICE.md"), onOpenUrl)
                    }

                    Section(stringResource(R.string.legal_third_party_heading)) {
                        Body(stringResource(R.string.legal_third_party_text))
                        Body(stringResource(R.string.legal_third_party_onyx))
                        LinkText(
                            stringResource(R.string.legal_third_party_link),
                            LegalInfo.blobUrl("THIRD_PARTY_NOTICES.md"), onOpenUrl
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) HouseCaps(title, 13)
        content()
    }
    Spacer(Modifier.height(18.dp))
    HouseRule(1.5.dp, House.Grey2)
}

@Composable
private fun Body(text: String, bold: Boolean = false) =
    Text(
        text, fontFamily = House.Serif, fontSize = 20.sp, lineHeight = 29.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = House.Ink
    )

/** Underlined text that opens [url] in the browser. At least 48 dp high, so a finger finds it. */
@Composable
private fun LinkText(
    text: String, url: String, onOpenUrl: (String) -> Unit,
    serif: Boolean = false, bold: Boolean = false, size: Int = 20
) {
    Box(
        Modifier.heightIn(min = 48.dp).houseTap { onOpenUrl(url) }.semantics { role = Role.Button },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text, fontFamily = if (serif) House.Serif else House.Sans, fontSize = size.sp, lineHeight = (size * 1.4f).sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline, color = House.Ink
        )
    }
}

@Preview(showBackground = true, widthDp = 900, heightDp = 1200)
@Composable
fun AboutPreview() {
    InkaTheme {
        AboutContent(versionName = "0.2.0", versionCode = 36, gitSha = "abc1234", onBack = {}, onOpenUrl = {})
    }
}
