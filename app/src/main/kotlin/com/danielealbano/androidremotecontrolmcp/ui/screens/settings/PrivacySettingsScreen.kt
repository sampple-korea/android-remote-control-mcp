@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.PlaceholderFormat
import com.danielealbano.androidremotecontrolmcp.data.model.RedactionMode
import com.danielealbano.androidremotecontrolmcp.privacy.PiiCategory
import com.danielealbano.androidremotecontrolmcp.privacy.PrivacyModeStatus
import com.danielealbano.androidremotecontrolmcp.privacy.model.DownloadState
import com.danielealbano.androidremotecontrolmcp.ui.components.AppDialog
import com.danielealbano.androidremotecontrolmcp.ui.components.CardContentPadding
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixPage
import com.danielealbano.androidremotecontrolmcp.ui.components.SwitchOnlyPreference
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.PrivacyViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivacyViewModel = hiltViewModel(),
) {
    val config by viewModel.privacyConfig.collectAsStateWithLifecycle()
    val status by viewModel.privacyStatus.collectAsStateWithLifecycle()
    val downloadState by viewModel.privacyDownloadState.collectAsStateWithLifecycle()
    val benchmarkEstimate by viewModel.privacyBenchmarkEstimate.collectAsStateWithLifecycle()
    val consentRequired by viewModel.consentRequired.collectAsStateWithLifecycle()
    val enableInProgress by viewModel.privacyEnableInProgress.collectAsStateWithLifecycle()
    val benchmarkRunning by viewModel.privacyBenchmarkRunning.collectAsStateWithLifecycle()

    val downloadInProgress = downloadState is DownloadState.Downloading || downloadState is DownloadState.Verifying

    if (consentRequired) {
        AppDialog(
            title = stringResource(R.string.privacy_consent_title),
            message = stringResource(R.string.privacy_consent_message),
            confirmText = stringResource(R.string.privacy_consent_confirm),
            onConfirm = { viewModel.confirmDownloadAndEnable() },
            dismissText = stringResource(R.string.privacy_consent_cancel),
            onDismissRequest = { viewModel.cancelConsent() },
        )
    }

    MiuixPage(
        title = stringResource(R.string.settings_privacy_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            // 1. Master enable toggle
            SwitchOnlyPreference(
                title = stringResource(R.string.privacy_enable_label),
                checked = config.enabled,
                onCheckedChange = { requested ->
                    if (requested) {
                        viewModel.requestEnablePrivacyMode()
                    } else {
                        viewModel.disablePrivacyMode()
                    }
                },
                // Disabled through the WHOLE enable window (request -> download -> warm-up), not
                // just while DownloadState reports progress; stays usable during the benchmark,
                // which finishes and stores its result even if Privacy Mode is toggled off.
                enabled = !downloadInProgress && !enableInProgress,
            )

            // 2. Status + download / warm-up / benchmark progress
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                PrivacyProgressSection(
                    status = status,
                    downloadState = downloadState,
                    enableInProgress = enableInProgress,
                    benchmarkRunning = benchmarkRunning,
                    benchmarkEstimate = benchmarkEstimate,
                )
            }
        }

        // 4. Protected categories
        Column {
            SmallTitle(text = stringResource(R.string.privacy_categories_header))
            Card(modifier = Modifier.fillMaxWidth()) {
                PiiCategory.entries.forEach { category ->
                    SwitchOnlyPreference(
                        title = stringResource(categoryLabel(category)),
                        checked = config.isCategoryEnabled(category),
                        onCheckedChange = { viewModel.updatePrivacyCategoryEnabled(category, it) },
                        // Categories are only editable while Privacy Mode is ON and not mid-enable:
                        // toggling them while disabled or during the download would silently change
                        // what gets protected once the mode comes up.
                        enabled = config.enabled && !downloadInProgress && !enableInProgress,
                    )
                }
            }
        }

        // 5. Redaction style + placeholder format
        Column {
            SmallTitle(text = stringResource(R.string.privacy_mode_header))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.selectableGroup()) {
                    RadioButtonPreference(
                        title = stringResource(R.string.privacy_mode_pseudonymize),
                        selected = config.redactionMode == RedactionMode.PSEUDONYMIZE,
                        onClick = { viewModel.updatePrivacyRedactionMode(RedactionMode.PSEUDONYMIZE) },
                    )
                    RadioButtonPreference(
                        title = stringResource(R.string.privacy_mode_redact),
                        selected = config.redactionMode == RedactionMode.REDACT,
                        onClick = { viewModel.updatePrivacyRedactionMode(RedactionMode.REDACT) },
                    )
                }
            }
        }

        AnimatedVisibility(visible = config.redactionMode == RedactionMode.PSEUDONYMIZE) {
            Column {
                SmallTitle(text = stringResource(R.string.privacy_format_header))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.selectableGroup()) {
                        RadioButtonPreference(
                            title = stringResource(R.string.privacy_format_hashed),
                            selected = config.placeholderFormat == PlaceholderFormat.HASHED,
                            onClick = { viewModel.updatePrivacyPlaceholderFormat(PlaceholderFormat.HASHED) },
                        )
                        RadioButtonPreference(
                            title = stringResource(R.string.privacy_format_numbered),
                            selected = config.placeholderFormat == PlaceholderFormat.NUMBERED,
                            onClick = { viewModel.updatePrivacyPlaceholderFormat(PlaceholderFormat.NUMBERED) },
                        )
                    }
                }
            }
        }

        // 6. Disclaimer
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardContentPadding) {
            Text(
                text = stringResource(R.string.privacy_disclaimer),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.privacy_shell_not_covered),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        // 7. Measured detection rates
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardContentPadding) {
            DetectionRatesSection()
        }
    }
}

@Composable
private fun DetectionRatesSection() {
    Text(
        text = stringResource(R.string.privacy_detection_rates_header),
        style = MiuixTheme.textStyles.headline2,
    )
    Spacer(Modifier.height(8.dp))
    PiiCategory.entries
        .map { category -> category to stringResource(categoryLabel(category)) }
        .sortedBy { (_, label) -> label }
        .forEach { (category, label) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = MEASURED_DETECTION_RATES.getValue(category),
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.privacy_detection_rates_caption),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun PrivacyProgressSection(
    status: PrivacyModeStatus,
    downloadState: DownloadState,
    enableInProgress: Boolean,
    benchmarkRunning: Boolean,
    benchmarkEstimate: Double?,
) {
    Text(
        text = statusText(status),
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
    when (downloadState) {
        is DownloadState.Downloading -> {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = downloadState.progressPercent / PERCENT_DIVISOR,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.privacy_download_progress, downloadState.progressPercent),
                style = MiuixTheme.textStyles.footnote1,
            )
        }

        is DownloadState.Verifying -> {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.privacy_download_verifying),
                style = MiuixTheme.textStyles.footnote1,
            )
        }

        is DownloadState.Failed -> {
            Text(
                text = stringResource(R.string.privacy_download_failed, downloadState.reason),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
            )
        }

        else -> {
            // Instant feedback for the enable phases DownloadState cannot see: the 1-2 s of
            // connection setup before Downloading fires (Idle) and the model warm-up after the
            // download (Completed).
            if (enableInProgress) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.privacy_enable_preparing),
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        }
    }

    if (benchmarkRunning) {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.privacy_benchmark_running),
            style = MiuixTheme.textStyles.footnote1,
        )
    }
    benchmarkEstimate?.let { estimate ->
        Spacer(Modifier.height(8.dp))
        Text(
            text =
                stringResource(
                    R.string.privacy_benchmark_estimate,
                    formatEstimate(estimate),
                ),
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

@Composable
private fun statusText(status: PrivacyModeStatus): String =
    when (status) {
        is PrivacyModeStatus.Ready -> stringResource(R.string.privacy_status_ready)
        is PrivacyModeStatus.ReadyDeterministicOnly -> stringResource(R.string.privacy_status_deterministic)
        is PrivacyModeStatus.Unavailable -> stringResource(R.string.privacy_status_unavailable, status.reason)
        is PrivacyModeStatus.Disabled -> stringResource(R.string.privacy_status_disabled)
    }

private fun categoryLabel(category: PiiCategory): Int =
    when (category) {
        PiiCategory.CREDENTIALS -> R.string.privacy_category_credentials
        PiiCategory.CARDS_AND_IBAN -> R.string.privacy_category_cards_iban
        PiiCategory.EMAILS -> R.string.privacy_category_emails
        PiiCategory.PHONE_NUMBERS -> R.string.privacy_category_phones
        PiiCategory.NAMES -> R.string.privacy_category_names
        PiiCategory.ADDRESSES -> R.string.privacy_category_addresses
        PiiCategory.NATIONAL_IDS -> R.string.privacy_category_national_ids
    }

private fun formatEstimate(estimate: Double): String = "%.1f".format(estimate)

private const val PERCENT_DIVISOR = 100f

/**
 * Per-category share of PII items detected by the FULL production pipeline, measured with
 * `make privacy-benchmark` on the UI-shaped corpus B (ui-synthetic, seed 20260803, run 2026-08-03).
 * ABSOLUTE RULE (see CLAUDE.md "Privacy detection effectiveness table"): whenever ANY privacy
 * detection logic changes, the benchmark MUST be re-run and these values updated from report.md.
 */
private val MEASURED_DETECTION_RATES: Map<PiiCategory, String> =
    mapOf(
        PiiCategory.CREDENTIALS to "90.9%",
        PiiCategory.CARDS_AND_IBAN to "75.3%",
        PiiCategory.EMAILS to "100.0%",
        PiiCategory.PHONE_NUMBERS to "84.4%",
        PiiCategory.NAMES to "0.8%",
        PiiCategory.ADDRESSES to "77.9%",
        PiiCategory.NATIONAL_IDS to "93.0%",
    )
