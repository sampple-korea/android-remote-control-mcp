@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber")

package com.danielealbano.androidremotecontrolmcp.ui.screens

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.BindingAddress
import com.danielealbano.androidremotecontrolmcp.data.model.ServerStatus
import com.danielealbano.androidremotecontrolmcp.data.model.TunnelProviderType
import com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel.ArcTunnelCtAlert
import com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel.ArcTunnelServiceProblem
import com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel.ArcTunnelStatusPage
import com.danielealbano.androidremotecontrolmcp.ui.ApprovalActivity
import com.danielealbano.androidremotecontrolmcp.ui.components.AppDialog
import com.danielealbano.androidremotecontrolmcp.ui.components.BatteryOptimizationCard
import com.danielealbano.androidremotecontrolmcp.ui.components.CalloutCard
import com.danielealbano.androidremotecontrolmcp.ui.components.ConnectionInfoCard
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixPage
import com.danielealbano.androidremotecontrolmcp.ui.components.PrivacyModeCard
import com.danielealbano.androidremotecontrolmcp.ui.components.ServerLogsSection
import com.danielealbano.androidremotecontrolmcp.ui.components.ServerStatusCard
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ArcTunnelRenewalViewModel
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ArcTunnelViewModel
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ChannelViewModel
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.LogsViewModel
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.MainViewModel
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.PrivacyViewModel
import com.danielealbano.androidremotecontrolmcp.utils.NetworkUtils
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
fun ServerScreen(
    onNavigateToPermissions: () -> Unit,
    onShowAllLogs: () -> Unit,
    onNavigateToNetworkSettings: () -> Unit,
    onNavigateToTunnelSettings: () -> Unit,
    onOpenPrivacySettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
    channelViewModel: ChannelViewModel = hiltViewModel(),
    privacyViewModel: PrivacyViewModel = hiltViewModel(),
    arcTunnelViewModel: ArcTunnelViewModel = hiltViewModel(),
    arcTunnelRenewalViewModel: ArcTunnelRenewalViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val logsViewModel: LogsViewModel = hiltViewModel()

    val privacyConfig by privacyViewModel.privacyConfig.collectAsStateWithLifecycle()
    val privacyCardDismissed by privacyViewModel.privacyCardDismissed.collectAsStateWithLifecycle()
    val serverConfig by viewModel.serverConfig.collectAsStateWithLifecycle()
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val recentServerLogs by logsViewModel.recentServerLogs.collectAsStateWithLifecycle()
    val tunnelStatus by viewModel.tunnelStatus.collectAsStateWithLifecycle()
    val listenerCheck by viewModel.listenerCheck.collectAsStateWithLifecycle()

    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsStateWithLifecycle()
    val isBatteryOptimizationIgnored by viewModel.isBatteryOptimizationIgnored.collectAsStateWithLifecycle()
    val pendingApprovalCount by viewModel.pendingApprovalCount.collectAsStateWithLifecycle()

    val channelConfig by channelViewModel.eventChannelConfig.collectAsStateWithLifecycle()
    val channelStatus by channelViewModel.channelConnectionStatus.collectAsStateWithLifecycle()
    val channelEnabled by channelViewModel.channelEnabled.collectAsStateWithLifecycle()
    val ctAlert by arcTunnelViewModel.ctAlert.collectAsStateWithLifecycle()
    val tunnelServiceProblem by arcTunnelViewModel.serviceProblem.collectAsStateWithLifecycle()
    val renewal by arcTunnelRenewalViewModel.renewal.collectAsStateWithLifecycle()

    val deviceIp = remember(context) { NetworkUtils.getDeviceIpAddress(context) }
    val copiedToClipboardMessage = stringResource(R.string.copied_to_clipboard)
    var showChannelNotConfiguredDialog by remember { mutableStateOf(false) }

    MiuixPage(title = stringResource(R.string.tab_server), modifier = modifier) {
        if (!isAccessibilityEnabled) {
            PermissionWarningCard(onClick = onNavigateToPermissions)
        }

        if (!serverConfig.oauthEnabled && !serverConfig.bearerTokenEnabled) {
            NoAuthWarningCard()
        }

        if (!isBatteryOptimizationIgnored) {
            BatteryOptimizationCard(
                onRequestExemption = { viewModel.requestBatteryOptimizationExemption() },
            )
        }

        if (pendingApprovalCount > 0) {
            PendingApprovalsCard(
                count = pendingApprovalCount,
                onClick = { context.startActivity(Intent(context, ApprovalActivity::class.java)) },
            )
        }

        ArcTunnelServiceProblemCard(tunnelServiceProblem)

        ArcTunnelCtAlertCard(
            alert = ctAlert,
            onChangeName = onNavigateToTunnelSettings,
            onDismiss = { arcTunnelViewModel.dismissCtAlert() },
        )

        if (serverConfig.bindingAddress == BindingAddress.LOCALHOST && !serverConfig.tunnelEnabled) {
            NetworkAccessSuggestionCard(
                enabled = serverStatus is ServerStatus.Stopped,
                onEnableWifi = {
                    viewModel.updateBindingAddress(BindingAddress.NETWORK)
                    onNavigateToNetworkSettings()
                },
                onOtherTunnels = {
                    viewModel.updateTunnelProvider(TunnelProviderType.CLOUDFLARE)
                    onNavigateToTunnelSettings()
                },
            )
        }

        if (!privacyConfig.enabled && !privacyCardDismissed) {
            PrivacyModeCard(
                onSetupClick = onOpenPrivacySettings,
                onDismissClick = { privacyViewModel.dismissPrivacyCard() },
            )
        }

        ServerStatusCard(
            serverStatus = serverStatus,
            channelStatus = channelStatus,
            // Off until the saved configuration is read, and only from then on a state worth announcing
            channelEnabled = channelEnabled ?: false,
            channelStatusKnown = channelEnabled != null,
            onMcpStartClick = { viewModel.startServer(context) },
            onMcpStopClick = { viewModel.stopServer(context) },
            onChannelStartClick = {
                if (channelConfig.endpointUrl.isBlank()) {
                    showChannelNotConfiguredDialog = true
                } else {
                    channelViewModel.startChannel()
                }
            },
            onChannelStopClick = { channelViewModel.stopChannel() },
            startEnabled = isAccessibilityEnabled,
            mcpStartBlockedMessage = listenerCheck.startError,
            mcpBusy = renewal.inProgress,
        )

        ConnectionInfoCard(
            bindingAddress = serverConfig.bindingAddress,
            bindHost = serverConfig.bindHost,
            ipAddress = deviceIp,
            port = serverConfig.port,
            httpsEnabled = serverConfig.httpsEnabled,
            bearerToken = serverConfig.bearerToken,
            tunnelEnabled = serverConfig.tunnelEnabled,
            serverStatus = serverStatus,
            tunnelStatus = tunnelStatus,
            renewal = renewal,
            onCancelRenewal = { viewModel.stopServer(context) },
            onRetryRenewal = arcTunnelRenewalViewModel::retry,
            specificAddressRefused = listenerCheck.specificAddressError != null,
            onCopyAll = { text ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("서버 정보", text)))
                    Toast.makeText(context, copiedToClipboardMessage, Toast.LENGTH_SHORT).show()
                }
            },
            onShare = { text ->
                val intent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                context.startActivity(Intent.createChooser(intent, null))
            },
        )

        ServerLogsSection(
            logs = recentServerLogs,
            onShowMore = onShowAllLogs,
        )
    }

    if (showChannelNotConfiguredDialog) {
        AppDialog(
            title = stringResource(R.string.channel_not_configured_dialog_title),
            message = stringResource(R.string.channel_not_configured_dialog_body),
            confirmText = stringResource(R.string.channel_not_configured_dialog_ok),
            onConfirm = { showChannelNotConfiguredDialog = false },
            onDismissRequest = { showChannelNotConfiguredDialog = false },
        )
    }
}

@Composable
private fun NoAuthWarningCard() {
    CalloutCard(
        icon = Icons.Default.Warning,
        title = stringResource(R.string.access_no_auth_warning_title),
    )
}

@Composable
private fun NetworkAccessSuggestionCard(
    enabled: Boolean,
    onEnableWifi: () -> Unit,
    onOtherTunnels: () -> Unit,
) {
    CalloutCard(
        icon = Icons.Default.Info,
        title = stringResource(R.string.server_network_access_suggestion_title),
    ) {
        TextButton(
            text = stringResource(R.string.server_network_access_suggestion_wifi),
            onClick = onEnableWifi,
            enabled = enabled,
        )
        TextButton(
            text = stringResource(R.string.server_network_access_suggestion_other_tunnels),
            onClick = onOtherTunnels,
            enabled = enabled,
        )
    }
}

/** What the tunnel service's status page reports; nothing unless it reports a problem. */
@Composable
private fun ArcTunnelServiceProblemCard(problem: ArcTunnelServiceProblem?) {
    if (problem == null) return
    val context = LocalContext.current
    CalloutCard(
        icon = Icons.Default.Warning,
        title = stringResource(R.string.server_arc_tunnel_status_title),
        body =
            if (problem.affected.isEmpty()) {
                stringResource(R.string.server_arc_tunnel_status_body, problem.state)
            } else {
                stringResource(
                    R.string.server_arc_tunnel_status_body_affected,
                    problem.state,
                    problem.affected.joinToString(", "),
                )
            },
    ) {
        TextButton(
            text = stringResource(R.string.server_arc_tunnel_status_action),
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ArcTunnelStatusPage.PAGE_URL)))
                }
            },
        )
    }
}

/** The Certificate Transparency banner; nothing when there is no alert. */
@Composable
private fun ArcTunnelCtAlertCard(
    alert: ArcTunnelCtAlert?,
    onChangeName: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (alert == null) return
    CalloutCard(
        icon = Icons.Default.Warning,
        title = stringResource(R.string.server_arc_tunnel_ct_alert_title),
        body = stringResource(R.string.server_arc_tunnel_ct_alert_body, alert.fqdn),
    ) {
        TextButton(
            text = stringResource(R.string.server_arc_tunnel_ct_alert_change_name),
            onClick = onChangeName,
        )
        TextButton(text = stringResource(R.string.server_arc_tunnel_ct_alert_dismiss), onClick = onDismiss)
    }
}

@Composable
private fun PendingApprovalsCard(
    count: Int,
    onClick: () -> Unit,
) {
    CalloutCard(
        icon = Icons.Default.Notifications,
        title = stringResource(R.string.server_pending_approvals_title, count),
    ) {
        TextButton(text = stringResource(R.string.server_pending_approvals_action), onClick = onClick)
    }
}

@Composable
private fun PermissionWarningCard(onClick: () -> Unit) {
    CalloutCard(
        icon = Icons.Default.Warning,
        title = stringResource(R.string.permission_warning_title),
    ) {
        TextButton(text = stringResource(R.string.permission_warning_action), onClick = onClick)
    }
}
