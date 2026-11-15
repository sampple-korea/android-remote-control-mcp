@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.components.AppDialog
import com.danielealbano.androidremotecontrolmcp.ui.components.AppTextField
import com.danielealbano.androidremotecontrolmcp.ui.components.CardContentPadding
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixPage
import com.danielealbano.androidremotecontrolmcp.ui.components.NavigationPreference
import com.danielealbano.androidremotecontrolmcp.ui.components.SwitchOnlyPreference
import com.danielealbano.androidremotecontrolmcp.ui.components.hideSoftKeyboardOnFocus
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.AccessViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AccessSettingsScreen(
    onBack: () -> Unit,
    onNavigateClients: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccessViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val serverConfig by viewModel.serverConfig.collectAsStateWithLifecycle()
    val publicUrlOverride by viewModel.publicUrlOverrideInput.collectAsStateWithLifecycle()
    val publicUrlError by viewModel.publicUrlOverrideError.collectAsStateWithLifecycle()
    val showDisableDialog by viewModel.showDisableAuthDialog.collectAsStateWithLifecycle()

    var showToken by remember { mutableStateOf(false) }

    if (showDisableDialog) {
        AppDialog(
            title = stringResource(R.string.access_disable_auth_dialog_title),
            message = stringResource(R.string.access_disable_auth_dialog_body),
            confirmText = stringResource(R.string.access_disable_auth_confirm),
            onConfirm = viewModel::confirmDisableLastAuth,
            dismissText = stringResource(R.string.access_disable_auth_cancel),
            onDismissRequest = viewModel::dismissDisableAuthDialog,
        )
    }

    MiuixPage(
        title = stringResource(R.string.access_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        if (!serverConfig.oauthEnabled && !serverConfig.bearerTokenEnabled) {
            WarningCard(
                title = stringResource(R.string.access_no_auth_warning_title),
                body = stringResource(R.string.access_no_auth_warning_body),
            )
            if (serverConfig.tunnelEnabled) {
                WarningCard(
                    title = stringResource(R.string.access_internet_exposure_warning_title),
                    body = stringResource(R.string.access_internet_exposure_warning_body),
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            // OAuth toggle
            SwitchOnlyPreference(
                title = stringResource(R.string.access_oauth_label),
                summary = stringResource(R.string.access_oauth_supporting),
                checked = serverConfig.oauthEnabled,
                onCheckedChange = viewModel::requestSetOauthEnabled,
            )
            if (serverConfig.oauthEnabled) {
                NavigationPreference(
                    title = stringResource(R.string.access_connected_clients),
                    onClick = onNavigateClients,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            // Bearer toggle
            SwitchOnlyPreference(
                title = stringResource(R.string.access_bearer_label),
                checked = serverConfig.bearerTokenEnabled,
                onCheckedChange = viewModel::requestSetBearerTokenEnabled,
            )
            AppTextField(
                value = serverConfig.bearerToken,
                onValueChange = {},
                readOnly = true,
                enabled = serverConfig.bearerTokenEnabled,
                label = stringResource(R.string.access_bearer_token_field_label),
                visualTransformation =
                    if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription =
                                    if (showToken) {
                                        stringResource(R.string.access_token_hide)
                                    } else {
                                        stringResource(R.string.access_token_show)
                                    },
                            )
                        }
                        IconButton(
                            onClick = { viewModel.copyBearerToken(context) },
                            enabled = serverConfig.bearerToken.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.access_token_copy),
                            )
                        }
                        IconButton(
                            onClick = viewModel::regenerateBearerToken,
                            enabled = serverConfig.bearerTokenEnabled,
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.access_token_regenerate),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
            )
        }

        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardContentPadding) {
            // Public URL override
            AppTextField(
                value = publicUrlOverride,
                onValueChange = viewModel::setPublicUrlOverride,
                label = stringResource(R.string.access_public_url_label),
                error = publicUrlError,
                supportingText = stringResource(R.string.access_public_url_supporting),
                modifier = Modifier.fillMaxWidth(),
                fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
            )
        }
    }
}

@Composable
private fun WarningCard(
    title: String,
    body: String,
) {
    Card(
        colors =
            CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.errorContainer,
                contentColor = MiuixTheme.colorScheme.onErrorContainer,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onErrorContainer,
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = body,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
