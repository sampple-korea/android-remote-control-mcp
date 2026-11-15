@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import android.content.ClipData
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.components.AppTextField
import com.danielealbano.androidremotecontrolmcp.ui.components.CardContentPadding
import com.danielealbano.androidremotecontrolmcp.ui.components.EventSourceRow
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixLazyPage
import com.danielealbano.androidremotecontrolmcp.ui.components.SwitchOnlyPreference
import com.danielealbano.androidremotecontrolmcp.ui.components.hideSoftKeyboardOnFocus
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ChannelViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
fun ChannelSettingsScreen(
    viewModel: ChannelViewModel,
    navController: NavHostController,
    onNavigateToNotificationFilter: () -> Unit,
    onNavigateToWifiMonitor: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val config by viewModel.eventChannelConfig.collectAsStateWithLifecycle()
    val endpointUrlInput by viewModel.endpointUrlInput.collectAsStateWithLifecycle()
    val endpointUrlError by viewModel.endpointUrlError.collectAsStateWithLifecycle()
    val authTokenInput by viewModel.authTokenInput.collectAsStateWithLifecycle()
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    MiuixLazyPage(
        title = stringResource(R.string.event_channel_name),
        onBack = onNavigateBack,
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardContentPadding) {
                AppTextField(
                    value = endpointUrlInput,
                    onValueChange = { viewModel.updateEndpointUrl(it) },
                    label = stringResource(R.string.channel_endpoint_url_label),
                    error = endpointUrlError,
                    modifier = Modifier.fillMaxWidth(),
                    fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
                )
                AppTextField(
                    value = authTokenInput,
                    onValueChange = { viewModel.updateAuthToken(it) },
                    label = stringResource(R.string.channel_auth_token_label),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false,
                        ),
                    visualTransformation =
                        if (tokenVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                Icon(
                                    if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription =
                                        if (tokenVisible) {
                                            stringResource(R.string.channel_auth_token_hide)
                                        } else {
                                            stringResource(R.string.channel_auth_token_show)
                                        },
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText("인증 토큰", authTokenInput)),
                                        )
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.channel_auth_token_copy),
                                )
                            }
                            IconButton(
                                onClick = { viewModel.generateNewAuthToken() },
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.channel_auth_token_generate),
                                )
                            }
                        }
                    },
                )
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchOnlyPreference(
                    title = stringResource(R.string.channel_auto_start_label),
                    summary = stringResource(R.string.channel_auto_start_description),
                    checked = config.enabled,
                    onCheckedChange = { viewModel.updateChannelEnabled(it) },
                )
            }
        }
        item {
            SmallTitle(text = stringResource(R.string.channel_event_sources_section))
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                EventSourceRow(
                    title = stringResource(R.string.channel_notification_events),
                    icon = Icons.Default.Notifications,
                    checked = config.notifications.enabled,
                    onCheckedChange = { viewModel.updateNotificationChannelEnabled(it) },
                    onClick = onNavigateToNotificationFilter,
                )
                EventSourceRow(
                    title = stringResource(R.string.channel_wifi_events),
                    icon = Icons.Default.Wifi,
                    checked = config.wifi.enabled,
                    onCheckedChange = { viewModel.updateWifiChannelEnabled(it) },
                    onClick = onNavigateToWifiMonitor,
                )
            }
        }
        geofenceEventSourceItem(navController)
    }
}
