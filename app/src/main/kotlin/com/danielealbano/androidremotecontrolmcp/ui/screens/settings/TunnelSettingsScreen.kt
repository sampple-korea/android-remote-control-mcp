@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.CloudflareTunnelMode
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ServerStatus
import com.danielealbano.androidremotecontrolmcp.data.model.TunnelProviderType
import com.danielealbano.androidremotecontrolmcp.data.model.TunnelStatus
import com.danielealbano.androidremotecontrolmcp.mcp.ListenerSettingsCheck
import com.danielealbano.androidremotecontrolmcp.ui.components.AppTextField
import com.danielealbano.androidremotecontrolmcp.ui.components.CardContentPadding
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixPage
import com.danielealbano.androidremotecontrolmcp.ui.components.SwitchOnlyPreference
import com.danielealbano.androidremotecontrolmcp.ui.components.hideSoftKeyboardOnFocus
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ArcTunnelRenewalViewModel
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ArcTunnelViewModel
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.MainViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val STATUS_INDICATOR_SIZE_DP = 16

@Composable
fun TunnelSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
    arcViewModel: ArcTunnelViewModel = hiltViewModel(),
    arcRenewalViewModel: ArcTunnelRenewalViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val serverConfig by viewModel.serverConfig.collectAsStateWithLifecycle()
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val tunnelStatus by viewModel.tunnelStatus.collectAsStateWithLifecycle()
    val ngrokAuthtokenInput by viewModel.ngrokAuthtokenInput.collectAsStateWithLifecycle()
    val ngrokDomainInput by viewModel.ngrokDomainInput.collectAsStateWithLifecycle()
    val cloudflareTokenInput by viewModel.cloudflareTokenInput.collectAsStateWithLifecycle()
    val cloudflareExtraArgsInput by viewModel.cloudflareExtraArgsInput.collectAsStateWithLifecycle()
    val tunnelLocalPortInput by viewModel.tunnelLocalPortInput.collectAsStateWithLifecycle()
    val tunnelLocalPortError by viewModel.tunnelLocalPortError.collectAsStateWithLifecycle()
    val listenerCheck by viewModel.listenerCheck.collectAsStateWithLifecycle()

    val isEnabled =
        serverStatus !is ServerStatus.Running &&
            serverStatus !is ServerStatus.Starting
    val arcSelected = serverConfig.tunnelProvider == TunnelProviderType.ARC_TUNNEL

    MiuixPage(
        title = stringResource(R.string.settings_tunnel_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        if (serverConfig.httpsEnabled && arcSelected) {
            Text(
                text = stringResource(R.string.remote_access_https_note_arc_tunnel),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            // Tunnel Enable/Disable Toggle
            SwitchOnlyPreference(
                title = stringResource(R.string.remote_access_enabled_label),
                checked = serverConfig.tunnelEnabled,
                onCheckedChange = { on ->
                    viewModel.updateTunnelEnabled(on)
                    if (on && arcSelected) arcViewModel.requestEnrollment()
                },
                enabled = isEnabled,
            )
        }

        // Tunnel Provider Selector
        Column {
            SmallTitle(text = stringResource(R.string.remote_access_provider_label))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.selectableGroup()) {
                    TunnelProviderType.entries.forEach { provider ->
                        RadioButtonPreference(
                            title = providerTitle(provider),
                            summary = providerDescription(provider),
                            selected = provider == serverConfig.tunnelProvider,
                            onClick = { viewModel.updateTunnelProvider(provider) },
                            enabled = isEnabled,
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardContentPadding) {
            // Cloudflare mode selector (Free vs Token)
            AnimatedVisibility(
                visible = serverConfig.tunnelProvider == TunnelProviderType.CLOUDFLARE,
            ) {
                Column {
                    CloudflareModeSelector(
                        selectedMode = serverConfig.cloudflareTunnelMode,
                        enabled = isEnabled,
                        onModeSelected = viewModel::updateCloudflareTunnelMode,
                    )
                }
            }

            // Cloudflare token-mode fields
            AnimatedVisibility(
                visible =
                    serverConfig.tunnelProvider == TunnelProviderType.CLOUDFLARE &&
                        serverConfig.cloudflareTunnelMode == CloudflareTunnelMode.TOKEN,
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    CloudflareTokenFields(
                        token = cloudflareTokenInput,
                        serviceUrl = tunnelForwardUrl(serverConfig),
                        enabled = isEnabled,
                        onTokenChange = viewModel::updateCloudflareTunnelToken,
                    )
                }
            }

            // Cloudflare extra arguments
            AnimatedVisibility(
                visible = serverConfig.tunnelProvider == TunnelProviderType.CLOUDFLARE,
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    CloudflareExtraArgsField(
                        extraArgs = cloudflareExtraArgsInput,
                        enabled = isEnabled,
                        onExtraArgsChange = viewModel::updateCloudflareTunnelExtraArgs,
                    )
                }
            }

            // ngrok-specific fields
            AnimatedVisibility(visible = serverConfig.tunnelProvider == TunnelProviderType.NGROK) {
                Column {
                    NgrokConfigFields(
                        authtoken = ngrokAuthtokenInput,
                        domain = ngrokDomainInput,
                        enabled = isEnabled,
                        onAuthtokenChange = viewModel::updateNgrokAuthtoken,
                        onDomainChange = viewModel::updateNgrokDomain,
                    )
                }
            }

            // Cloudflare and ngrok forward to http://localhost:<tunnel local port>
            AnimatedVisibility(visible = !arcSelected) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    val portError = tunnelLocalPortFieldError(tunnelLocalPortError, listenerCheck)
                    val forwardUrl = tunnelForwardUrl(serverConfig)
                    AppTextField(
                        value = tunnelLocalPortInput,
                        onValueChange = viewModel::updateTunnelLocalPort,
                        label = stringResource(R.string.remote_access_tunnel_local_port_label),
                        error = portError,
                        supportingText = stringResource(R.string.remote_access_tunnel_local_port_hint, forwardUrl),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = isEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
                    )
                }
            }

            AnimatedVisibility(visible = arcSelected) {
                Column {
                    ArcTunnelSettingsSection(
                        tunnelEnabled = serverConfig.tunnelEnabled,
                        enabled = isEnabled,
                        ctCheckEnabled = serverConfig.arcTunnelCtCheckEnabled,
                        statusCheckEnabled = serverConfig.arcTunnelStatusCheckEnabled,
                        onStopServer = { viewModel.stopServer(context) },
                        viewModel = arcViewModel,
                        renewalViewModel = arcRenewalViewModel,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardContentPadding) {
            TunnelStatusIndicator(status = tunnelStatus)
        }
    }
}

/**
 * Where Cloudflare and ngrok forward, also the service URL to enter in the Cloudflare dashboard for a token-mode
 * tunnel. Digits as ASCII whatever the locale: it is a URL to type.
 */
internal fun tunnelForwardUrl(config: ServerConfig): String = "http://localhost:${config.tunnelLocalPort}"

/** The tunnel local port field's error: the input's own first, then the listener check's. */
internal fun tunnelLocalPortFieldError(
    inputError: String?,
    check: ListenerSettingsCheck,
): String? = inputError ?: check.tunnelPortError

@Composable
private fun providerTitle(provider: TunnelProviderType): String =
    when (provider) {
        TunnelProviderType.ARC_TUNNEL -> stringResource(R.string.remote_access_provider_arc_tunnel)
        TunnelProviderType.CLOUDFLARE -> stringResource(R.string.remote_access_provider_cloudflare)
        TunnelProviderType.NGROK -> stringResource(R.string.remote_access_provider_ngrok)
    }

@Composable
private fun providerDescription(provider: TunnelProviderType): String =
    when (provider) {
        TunnelProviderType.ARC_TUNNEL -> stringResource(R.string.remote_access_provider_arc_tunnel_desc)
        TunnelProviderType.CLOUDFLARE -> stringResource(R.string.remote_access_provider_cloudflare_desc)
        TunnelProviderType.NGROK -> stringResource(R.string.remote_access_provider_ngrok_desc)
    }

@Composable
private fun NgrokConfigFields(
    authtoken: String,
    domain: String,
    enabled: Boolean,
    onAuthtokenChange: (String) -> Unit,
    onDomainChange: (String) -> Unit,
) {
    var showAuthtoken by remember { mutableStateOf(false) }

    Column {
        AppTextField(
            value = authtoken,
            onValueChange = onAuthtokenChange,
            label = stringResource(R.string.remote_access_ngrok_authtoken_label),
            enabled = enabled,
            visualTransformation =
                if (showAuthtoken) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            trailingIcon = {
                IconButton(
                    onClick = { showAuthtoken = !showAuthtoken },
                ) {
                    Icon(
                        imageVector =
                            if (showAuthtoken) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                        contentDescription =
                            if (showAuthtoken) {
                                stringResource(R.string.remote_access_ngrok_authtoken_hide)
                            } else {
                                stringResource(R.string.remote_access_ngrok_authtoken_show)
                            },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        AppTextField(
            value = domain,
            onValueChange = onDomainChange,
            label = stringResource(R.string.remote_access_ngrok_domain_label),
            supportingText = stringResource(R.string.remote_access_ngrok_domain_hint),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
        )
    }
}

@Composable
private fun CloudflareModeSelector(
    selectedMode: CloudflareTunnelMode,
    enabled: Boolean,
    onModeSelected: (CloudflareTunnelMode) -> Unit,
) {
    Text(
        text = stringResource(R.string.remote_access_cloudflare_mode_label),
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
    )
    Column(modifier = Modifier.selectableGroup()) {
        CloudflareTunnelMode.entries.forEach { mode ->
            RadioButtonPreference(
                title =
                    when (mode) {
                        CloudflareTunnelMode.FREE -> stringResource(R.string.remote_access_cloudflare_mode_free)
                        CloudflareTunnelMode.TOKEN -> stringResource(R.string.remote_access_cloudflare_mode_token)
                    },
                summary =
                    when (mode) {
                        CloudflareTunnelMode.FREE -> stringResource(R.string.remote_access_cloudflare_mode_free_desc)
                        CloudflareTunnelMode.TOKEN -> stringResource(R.string.remote_access_cloudflare_mode_token_desc)
                    },
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
                enabled = enabled,
                insideMargin = PaddingValues(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun CloudflareTokenFields(
    token: String,
    serviceUrl: String,
    enabled: Boolean,
    onTokenChange: (String) -> Unit,
) {
    var showToken by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column {
        AppTextField(
            value = token,
            onValueChange = onTokenChange,
            label = stringResource(R.string.remote_access_cloudflare_token_label),
            enabled = enabled,
            visualTransformation =
                if (showToken) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(
                        imageVector =
                            if (showToken) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                        contentDescription =
                            if (showToken) {
                                stringResource(R.string.config_token_hide)
                            } else {
                                stringResource(R.string.config_token_show)
                            },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.remote_access_cloudflare_service_url_help),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = serviceUrl,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("서비스 URL", serviceUrl)))
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.remote_access_cloudflare_service_url_copy),
                )
            }
        }
    }
}

@Composable
private fun CloudflareExtraArgsField(
    extraArgs: String,
    enabled: Boolean,
    onExtraArgsChange: (String) -> Unit,
) {
    Column {
        AppTextField(
            value = extraArgs,
            onValueChange = onExtraArgsChange,
            label = stringResource(R.string.remote_access_cloudflare_extra_args_label),
            supportingText = stringResource(R.string.remote_access_cloudflare_extra_args_hint),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.remote_access_cloudflare_extra_args_help),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun TunnelStatusIndicator(
    status: TunnelStatus,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (status) {
            is TunnelStatus.Disconnected -> {
                Text(
                    text = stringResource(R.string.remote_access_status_disconnected),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            is TunnelStatus.Connecting -> {
                InfiniteProgressIndicator(
                    color = MiuixTheme.colorScheme.primary,
                    size = STATUS_INDICATOR_SIZE_DP.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.remote_access_status_connecting),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }

            is TunnelStatus.Connected -> {
                Text(
                    text = stringResource(R.string.remote_access_status_connected),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }

            is TunnelStatus.Error -> {
                Text(
                    text = stringResource(R.string.remote_access_status_error, status.message),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.error,
                )
            }
        }
    }
}
