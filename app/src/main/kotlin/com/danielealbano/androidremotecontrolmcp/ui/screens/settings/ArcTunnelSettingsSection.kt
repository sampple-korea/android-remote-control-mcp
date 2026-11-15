@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel.ArcTunnelEnrollmentState
import com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel.ArcTunnelRenewalState
import com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel.ArcTunnelStatusPage
import com.danielealbano.androidremotecontrolmcp.ui.components.AppDialog
import com.danielealbano.androidremotecontrolmcp.ui.components.AppTextField
import com.danielealbano.androidremotecontrolmcp.ui.components.ArcTunnelRenewalBlock
import com.danielealbano.androidremotecontrolmcp.ui.components.SwitchOnlyPreference
import com.danielealbano.androidremotecontrolmcp.ui.components.hideSoftKeyboardOnFocus
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ArcTunnelRenewalViewModel
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ArcTunnelUrlState
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ArcTunnelViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PROGRESS_SIZE_DP = 16

data class ArcTunnelSettingsState(
    val tunnelEnabled: Boolean,
    val enabled: Boolean,
    val ctCheckEnabled: Boolean,
    val statusCheckEnabled: Boolean,
    val baseAddress: String,
    val baseAddressValid: Boolean,
    val url: ArcTunnelUrlState,
    val enrollmentState: ArcTunnelEnrollmentState,
    val enrollmentRequested: Boolean,
    val renewal: ArcTunnelRenewalState = ArcTunnelRenewalState.Idle,
)

data class ArcTunnelSettingsActions(
    val onBaseAddressChange: (String) -> Unit,
    val onBaseAddressCommit: () -> Unit,
    val onCtCheckChange: (Boolean) -> Unit,
    val onStatusCheckChange: (Boolean) -> Unit,
    val onEnroll: () -> Unit,
    val onCancelEnrollment: () -> Unit,
    val onRotateName: () -> Unit,
    val onCancelRenewal: () -> Unit,
    val onRetryRenewal: () -> Unit,
)

/**
 * The dialog shows only for a pending request, with Remote Access on, nothing enrolled, nothing running,
 * server stopped.
 */
internal fun arcTunnelEnrollDialogVisible(state: ArcTunnelSettingsState): Boolean =
    state.enrollmentRequested &&
        state.tunnelEnabled &&
        state.url is ArcTunnelUrlState.NotEnrolled &&
        state.enrollmentState !is ArcTunnelEnrollmentState.Enrolling &&
        state.enabled

/** "Set up tunnel" covers the enabled-but-not-enrolled idle state (a failure shows Retry instead). */
internal fun arcTunnelSetUpVisible(state: ArcTunnelSettingsState): Boolean =
    state.url is ArcTunnelUrlState.NotEnrolled &&
        state.tunnelEnabled &&
        state.enrollmentState is ArcTunnelEnrollmentState.Idle

/** Stateful entry point: collects the ViewModel and hands plain state + callbacks to [ArcTunnelSettingsContent]. */
@Composable
fun ArcTunnelSettingsSection(
    tunnelEnabled: Boolean,
    enabled: Boolean,
    ctCheckEnabled: Boolean,
    statusCheckEnabled: Boolean,
    onStopServer: () -> Unit,
    viewModel: ArcTunnelViewModel,
    renewalViewModel: ArcTunnelRenewalViewModel,
) {
    val baseAddress by viewModel.baseAddressInput.collectAsStateWithLifecycle()
    val baseAddressValid by viewModel.baseAddressValid.collectAsStateWithLifecycle()
    val url by viewModel.url.collectAsStateWithLifecycle()
    val enrollmentState by viewModel.enrollmentState.collectAsStateWithLifecycle()
    val enrollmentRequested by viewModel.enrollmentRequested.collectAsStateWithLifecycle()
    val renewal by renewalViewModel.renewal.collectAsStateWithLifecycle()
    ArcTunnelSettingsContent(
        state =
            ArcTunnelSettingsState(
                tunnelEnabled = tunnelEnabled,
                enabled = enabled,
                ctCheckEnabled = ctCheckEnabled,
                statusCheckEnabled = statusCheckEnabled,
                baseAddress = baseAddress,
                baseAddressValid = baseAddressValid,
                url = url,
                enrollmentState = enrollmentState,
                enrollmentRequested = enrollmentRequested,
                renewal = renewal,
            ),
        actions =
            ArcTunnelSettingsActions(
                onBaseAddressChange = viewModel::updateBaseAddressInput,
                onBaseAddressCommit = viewModel::commitBaseAddress,
                onCtCheckChange = viewModel::updateCtCheckEnabled,
                onStatusCheckChange = viewModel::updateStatusCheckEnabled,
                onEnroll = viewModel::enroll,
                onCancelEnrollment = viewModel::cancelEnrollment,
                onRotateName = viewModel::rotateName,
                onCancelRenewal = onStopServer,
                onRetryRenewal = renewalViewModel::retry,
            ),
    )
}

@Composable
private fun ArcTunnelSwitchRow(
    @StringRes label: Int,
    @StringRes description: Int,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SwitchOnlyPreference(
        title = stringResource(label),
        summary = stringResource(description),
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        insideMargin = PaddingValues(vertical = 10.dp),
    )
}

/** The status page is that of the public service: a self-hosted base address has its own, or none. */
internal fun arcTunnelIsPublicService(baseAddress: String): Boolean {
    val address = baseAddress.trim()
    return address == ServerConfig.DEFAULT_ARC_TUNNEL_BASE_ADDRESS
}

@Composable
fun ArcTunnelSettingsContent(
    state: ArcTunnelSettingsState,
    actions: ArcTunnelSettingsActions,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val enrolling = state.enrollmentState is ArcTunnelEnrollmentState.Enrolling
    var showRotateDialog by rememberSaveable { mutableStateOf(false) }

    Column {
        AppTextField(
            value = state.baseAddress,
            onValueChange = actions.onBaseAddressChange,
            label = stringResource(R.string.remote_access_arc_tunnel_base_address_label),
            enabled = state.enabled,
            error =
                if (state.baseAddressValid) {
                    null
                } else {
                    stringResource(R.string.remote_access_arc_tunnel_base_address_error)
                },
            modifier = Modifier.fillMaxWidth(),
            fieldModifier =
                Modifier
                    .onFocusChanged { focusState ->
                        // Gaining focus hides the soft keyboard instead of showing it (see
                        // hideSoftKeyboardOnFocus); losing it commits the address.
                        if (focusState.isFocused) {
                            keyboardController?.hide()
                        } else {
                            actions.onBaseAddressCommit()
                        }
                    },
        )
        Spacer(modifier = Modifier.height(8.dp))
        ArcTunnelSwitchRow(
            label = R.string.remote_access_arc_tunnel_ct_check_label,
            description = R.string.remote_access_arc_tunnel_ct_check_description,
            checked = state.ctCheckEnabled,
            enabled = state.enabled,
            onChange = actions.onCtCheckChange,
        )
        if (arcTunnelIsPublicService(state.baseAddress)) {
            Spacer(modifier = Modifier.height(8.dp))
            ArcTunnelSwitchRow(
                label = R.string.remote_access_arc_tunnel_status_check_label,
                description = R.string.remote_access_arc_tunnel_status_check_description,
                checked = state.statusCheckEnabled,
                enabled = state.enabled,
                onChange = actions.onStatusCheckChange,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        UrlRow(state, enrolling, onRotate = { showRotateDialog = true }, onEnroll = actions.onEnroll)
        EnrollmentProgress(state.enrollmentState, enabled = state.enabled, onRetry = actions.onEnroll)
        if (state.renewal != ArcTunnelRenewalState.Idle) {
            ArcTunnelRenewalBlock(
                failure = (state.renewal as? ArcTunnelRenewalState.Failed)?.message,
                onCancel = actions.onCancelRenewal,
                onRetry = actions.onRetryRenewal,
                cancelEnabled = state.renewal != ArcTunnelRenewalState.Installing,
            )
        }
        if (arcTunnelIsPublicService(state.baseAddress)) {
            val context = LocalContext.current
            TextButton(
                text = stringResource(R.string.remote_access_arc_tunnel_status_link),
                onClick = {
                    runCatching {
                        val page = Uri.parse(ArcTunnelStatusPage.PAGE_URL)
                        context.startActivity(Intent(Intent.ACTION_VIEW, page))
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (arcTunnelEnrollDialogVisible(state)) {
        ConfirmDialog(
            title = R.string.remote_access_arc_tunnel_enroll_dialog_title,
            body = R.string.remote_access_arc_tunnel_enroll_dialog_body,
            onConfirm = actions.onEnroll,
            onDismiss = actions.onCancelEnrollment,
        )
    }
    if (showRotateDialog) {
        ConfirmDialog(
            title = R.string.remote_access_arc_tunnel_rotate_dialog_title,
            body = R.string.remote_access_arc_tunnel_rotate_dialog_body,
            onConfirm = {
                showRotateDialog = false
                actions.onRotateName()
            },
            onDismiss = { showRotateDialog = false },
        )
    }
    LaunchedEffect(state.enabled) { if (!state.enabled) showRotateDialog = false }
}

@Composable
private fun UrlRow(
    state: ArcTunnelSettingsState,
    enrolling: Boolean,
    onRotate: () -> Unit,
    onEnroll: () -> Unit,
) {
    when (val url = state.url) {
        is ArcTunnelUrlState.Loading -> {
            Text(
                text = stringResource(R.string.remote_access_arc_tunnel_url_loading),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        is ArcTunnelUrlState.Enrolled -> {
            AppTextField(
                value = url.url,
                onValueChange = {},
                readOnly = true,
                label = stringResource(R.string.remote_access_arc_tunnel_url_label),
                trailingIcon = {
                    IconButton(onClick = onRotate, enabled = state.enabled && !enrolling) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.remote_access_arc_tunnel_url_refresh),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
            )
        }

        is ArcTunnelUrlState.NotEnrolled -> {
            if (arcTunnelSetUpVisible(state)) {
                TextButton(
                    text = stringResource(R.string.remote_access_arc_tunnel_set_up),
                    onClick = onEnroll,
                    enabled = state.enabled,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun EnrollmentProgress(
    state: ArcTunnelEnrollmentState,
    enabled: Boolean,
    onRetry: () -> Unit,
) {
    when (state) {
        is ArcTunnelEnrollmentState.Idle -> {
            // nothing to show
        }

        is ArcTunnelEnrollmentState.Enrolling -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfiniteProgressIndicator(
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    size = PROGRESS_SIZE_DP.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.remote_access_arc_tunnel_enrolling),
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }

        is ArcTunnelEnrollmentState.Failed -> {
            Column {
                Text(
                    text = stringResource(R.string.remote_access_arc_tunnel_enroll_failed, state.message),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.error,
                )
                TextButton(
                    text = stringResource(R.string.remote_access_arc_tunnel_retry),
                    onClick = onRetry,
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: Int,
    body: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = stringResource(title),
        message = stringResource(body),
        confirmText = stringResource(R.string.remote_access_arc_tunnel_enroll_dialog_continue),
        onConfirm = onConfirm,
        dismissText = stringResource(R.string.remote_access_arc_tunnel_enroll_dialog_cancel),
        onDismissRequest = onDismiss,
    )
}
