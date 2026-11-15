@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.components.AppTextField
import com.danielealbano.androidremotecontrolmcp.ui.components.CardContentPadding
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixPage
import com.danielealbano.androidremotecontrolmcp.ui.components.SwitchOnlyPreference
import com.danielealbano.androidremotecontrolmcp.ui.components.hideSoftKeyboardOnFocus
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ChannelViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun WifiMonitorScreen(
    viewModel: ChannelViewModel,
    onNavigateBack: () -> Unit,
) {
    val config by viewModel.eventChannelConfig.collectAsStateWithLifecycle()
    var newSsid by rememberSaveable { mutableStateOf("") }

    MiuixPage(
        title = stringResource(R.string.wifi_monitor_title),
        onBack = onNavigateBack,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            config.wifi.ssids.toList().forEach { ssid ->
                BasicComponent(
                    title = ssid,
                    endActions = {
                        IconButton(onClick = { viewModel.removeWifiSsid(ssid) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.wifi_monitor_remove_ssid, ssid),
                            )
                        }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextField(
                    value = newSsid,
                    onValueChange = { newSsid = it },
                    label = stringResource(R.string.wifi_monitor_ssid_label),
                    modifier = Modifier.weight(1f),
                    fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
                )
                IconButton(
                    onClick = {
                        if (newSsid.isNotBlank()) {
                            viewModel.addWifiSsid(newSsid.trim())
                            newSsid = ""
                        }
                    },
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.wifi_monitor_add_ssid))
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchOnlyPreference(
                title = stringResource(R.string.wifi_monitor_notify_discovered),
                summary = stringResource(R.string.wifi_monitor_scan_based),
                checked = config.wifi.notifyOnDiscovered,
                onCheckedChange = { viewModel.updateWifiNotifyOnDiscovered(it) },
            )
            SwitchOnlyPreference(
                title = stringResource(R.string.wifi_monitor_notify_lost),
                summary = stringResource(R.string.wifi_monitor_scan_based),
                checked = config.wifi.notifyOnLost,
                onCheckedChange = { viewModel.updateWifiNotifyOnLost(it) },
            )
            SwitchOnlyPreference(
                title = stringResource(R.string.wifi_monitor_notify_connected),
                summary = stringResource(R.string.wifi_monitor_real_time),
                checked = config.wifi.notifyOnConnected,
                onCheckedChange = { viewModel.updateWifiNotifyOnConnected(it) },
            )
            SwitchOnlyPreference(
                title = stringResource(R.string.wifi_monitor_notify_disconnected),
                summary = stringResource(R.string.wifi_monitor_real_time),
                checked = config.wifi.notifyOnDisconnected,
                onCheckedChange = { viewModel.updateWifiNotifyOnDisconnected(it) },
            )
        }
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardContentPadding) {
            Text(
                stringResource(R.string.wifi_monitor_throttling_note),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
