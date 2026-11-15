@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PROGRESS_SIZE_DP = 20

/**
 * A free E2E tunnel certificate renewal at tunnel start, while it runs and whoever started the server, over the whole
 * app. Cancel aborts the renewal and stops the MCP server, until [cancelEnabled] says the certificate is received and
 * must be installed; Back or a tap outside only hides this ([onHide]), because a renewal can wait a long time and the
 * app stays usable meanwhile. It closes when the renewal ends: a missing or expired certificate that failed goes on in
 * [ArcTunnelRenewalBlock], since resolving it can need another screen, and a saved chain that failed is only logged,
 * because the tunnel starts anyway.
 */
@Composable
fun ArcTunnelRenewalDialog(
    visible: Boolean,
    cancelEnabled: Boolean,
    onHide: () -> Unit,
    onCancel: () -> Unit,
) {
    AppDialog(
        show = visible,
        title = null,
        confirmText = stringResource(R.string.remote_access_arc_tunnel_renewal_cancel),
        onConfirm = onCancel,
        confirmEnabled = cancelEnabled,
        onDismissRequest = onHide,
    ) {
        ArcTunnelRenewalProgress()
    }
}

/**
 * The free E2E tunnel's tunnel-start certificate renewal: in progress (spinner + Cancel) or failed ([failure],
 * with Retry + Cancel). Cancel aborts the renewal and stops the MCP server, until [cancelEnabled] says the certificate
 * is received and must be installed.
 */
@Composable
fun ArcTunnelRenewalBlock(
    failure: String?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    cancelEnabled: Boolean = true,
) {
    Column(modifier = modifier) {
        if (failure == null) {
            ArcTunnelRenewalProgress()
        } else {
            Text(
                text = stringResource(R.string.remote_access_arc_tunnel_renewal_failed, failure),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (failure != null) {
                TextButton(
                    text = stringResource(R.string.remote_access_arc_tunnel_retry),
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
            TextButton(
                text = stringResource(R.string.remote_access_arc_tunnel_renewal_cancel),
                onClick = onCancel,
                enabled = cancelEnabled,
            )
        }
    }
}

@Composable
private fun ArcTunnelRenewalProgress() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InfiniteProgressIndicator(
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            size = PROGRESS_SIZE_DP.dp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.remote_access_arc_tunnel_renewing),
            style = MiuixTheme.textStyles.body2,
        )
    }
}
