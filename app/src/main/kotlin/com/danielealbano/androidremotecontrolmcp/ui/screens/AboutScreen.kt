@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.BuildConfig
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixPage
import com.danielealbano.androidremotecontrolmcp.ui.components.NavigationPreference
import com.danielealbano.androidremotecontrolmcp.ui.components.rememberChangedSinceShown
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.UpdateCheckUiState
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.UpdateViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val openLink = stringResource(R.string.a11y_action_open_link)
    val openUrl: (String) -> Unit = { url ->
        // No-op if the device has no browser to handle ACTION_VIEW (never crash).
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    MiuixPage(title = stringResource(R.string.tab_about), modifier = modifier) {
        // App icon + name + version
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(vertical = 24.dp, horizontal = 16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(colorResource(R.color.ic_launcher_background)),
                    contentAlignment = Alignment.Center,
                ) {
                    // Decorative: the app's name is the text below
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MiuixTheme.textStyles.title3,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        // Updates section
        Column {
            SmallTitle(text = stringResource(R.string.about_updates_section))
            Card(modifier = Modifier.fillMaxWidth()) {
                UpdateSettingsSection(viewModel = updateViewModel, onOpenUrl = openUrl)
            }
        }

        // Author section
        Column {
            SmallTitle(text = stringResource(R.string.about_author_section))
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(title = stringResource(R.string.about_author_name))
                NavigationPreference(
                    title = stringResource(R.string.about_author_github),
                    onClick = { openUrl(AboutLinks.MAINTAINER_URL) },
                    onClickLabel = openLink,
                )
            }
        }

        // Fork section: the project this app is derived from and its author
        Column {
            SmallTitle(text = stringResource(R.string.about_fork_section))
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(R.string.about_fork_author_name),
                    summary = stringResource(R.string.about_fork_notice),
                )
                NavigationPreference(
                    title = stringResource(R.string.about_fork_upstream),
                    onClick = { openUrl(AboutLinks.UPSTREAM_URL) },
                    onClickLabel = openLink,
                )
            }
        }

        // Links section
        Column {
            SmallTitle(text = stringResource(R.string.about_links_section))
            Card(modifier = Modifier.fillMaxWidth()) {
                AboutLinkItem(R.string.about_link_github, Icons.Default.Code, AboutLinks.GITHUB_URL)
                AboutLinkItem(R.string.about_link_license, Icons.Default.Description, AboutLinks.LICENSE_URL)
                AboutLinkItem(
                    R.string.about_link_third_party_licenses,
                    Icons.AutoMirrored.Filled.LibraryBooks,
                    AboutLinks.THIRD_PARTY_LICENSES_URL,
                )
                AboutLinkItem(R.string.about_link_issues, Icons.Default.ReportProblem, AboutLinks.ISSUES_URL)
                AboutLinkItem(
                    R.string.about_link_tunnel_status,
                    Icons.Default.MonitorHeart,
                    AboutLinks.TUNNEL_STATUS_URL,
                )
                AboutLinkItem(
                    R.string.about_link_tunnel_server_repository,
                    Icons.Default.Dns,
                    AboutLinks.TUNNEL_SERVER_REPOSITORY_URL,
                )
            }
        }

        // Attributions section
        Column {
            SmallTitle(text = stringResource(R.string.about_attributions_section))
            Card(modifier = Modifier.fillMaxWidth()) {
                NavigationPreference(
                    title = stringResource(R.string.about_attribution_dbip),
                    onClick = { openUrl(AboutLinks.DBIP_URL) },
                    onClickLabel = openLink,
                )
                NavigationPreference(
                    title = stringResource(R.string.about_attribution_privacy_model),
                    onClick = { openUrl(AboutLinks.PRIVACY_MODEL_URL) },
                    onClickLabel = openLink,
                )
                BasicComponent(summary = stringResource(R.string.about_attribution_android_robot))
            }
        }

        // Footer
        Text(
            text = stringResource(R.string.about_footer),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** A "Links" row that opens [url] in the browser; a no-op when no app handles ACTION_VIEW (never crash). */
@Composable
private fun AboutLinkItem(
    @StringRes labelRes: Int,
    icon: ImageVector,
    url: String,
) {
    val context = LocalContext.current
    NavigationPreference(
        title = stringResource(labelRes),
        icon = icon,
        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
        onClickLabel = stringResource(R.string.a11y_action_open_link),
    )
}

@Composable
private fun UpdateSettingsSection(
    viewModel: UpdateViewModel,
    onOpenUrl: (String) -> Unit,
) {
    val autoCheckEnabled by viewModel.autoCheckEnabled.collectAsStateWithLifecycle()
    val checkState by viewModel.checkState.collectAsStateWithLifecycle()

    SwitchPreference(
        title = stringResource(R.string.about_auto_update_label),
        checked = autoCheckEnabled,
        onCheckedChange = viewModel::setAutoCheckEnabled,
    )

    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Button(
            onClick = viewModel::checkNow,
            enabled = checkState !is UpdateCheckUiState.Checking,
            colors = ButtonDefaults.buttonColorsPrimary(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (checkState is UpdateCheckUiState.Checking) {
                val checking = stringResource(R.string.about_update_checking)
                InfiniteProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = checking },
                    color = LocalContentColor.current,
                    size = 18.dp,
                )
            } else {
                Text(stringResource(R.string.about_check_updates_button))
            }
        }

        UpdateCheckStatusText(state = checkState, onOpenUrl = onOpenUrl)
    }
}

@Composable
private fun UpdateCheckStatusText(
    state: UpdateCheckUiState,
    onOpenUrl: (String) -> Unit,
) {
    val found = state as? UpdateCheckUiState.UpdateFound
    val message =
        when (state) {
            is UpdateCheckUiState.UpdateFound -> stringResource(R.string.about_update_found, state.update.versionName)
            UpdateCheckUiState.UpToDate -> stringResource(R.string.about_update_up_to_date)
            UpdateCheckUiState.Failed -> stringResource(R.string.about_update_check_failed)
            UpdateCheckUiState.DevBuild -> stringResource(R.string.about_update_dev_build)
            else -> null
        }
    if (message != null) {
        Spacer(Modifier.height(8.dp))
    }
    // Read as one node carrying the result itself, spoken without the sighted "tap" hint. A polite live region from
    // the first change on (rememberChangedSinceShown), so a new result is announced and coming back to About reads
    // nothing extra. While there is no result (every check goes through Checking) the node is empty and not exposed;
    // when the result appears, Compose reports a subtree change from this node, and TalkBack announces a live-region
    // node's content on any content change from it. The click goes before the clearing modifier: semantics are
    // collected from the end of the chain back, and clearAndSetSemantics drops what comes after it.
    val spoken =
        if (found != null) stringResource(R.string.about_update_found_spoken, found.update.versionName) else message
    val announceChanges = rememberChangedSinceShown(spoken)
    val openLink = stringResource(R.string.a11y_action_open_link)
    Box(
        modifier =
            Modifier
                .then(
                    if (found != null) {
                        Modifier.clickable(onClickLabel = openLink) { onOpenUrl(found.update.releaseUrl) }
                    } else {
                        Modifier
                    },
                ).clearAndSetSemantics {
                    spoken?.let { contentDescription = it }
                    if (announceChanges) {
                        liveRegion = LiveRegionMode.Polite
                    }
                },
    ) {
        if (message != null) {
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color =
                    if (found != null) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
            )
        }
    }
}
