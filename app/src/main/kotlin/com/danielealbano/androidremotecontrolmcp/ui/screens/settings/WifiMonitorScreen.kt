@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
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
import com.danielealbano.androidremotecontrolmcp.ui.components.TopBarBackButton
import com.danielealbano.androidremotecontrolmcp.ui.components.TopBarTitle
import com.danielealbano.androidremotecontrolmcp.ui.components.hideSoftKeyboardOnFocus
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ChannelViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun WifiMonitorScreen(
    viewModel: ChannelViewModel,
    onNavigateBack: () -> Unit,
) {
    val config by viewModel.eventChannelConfig.collectAsStateWithLifecycle()
    var newSsid by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { TopBarTitle(stringResource(R.string.wifi_monitor_title)) },
                navigationIcon = { TopBarBackButton(onClick = onNavigateBack) },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(config.wifi.ssids.toList()) { ssid ->
                ListItem(
                    headlineContent = { Text(ssid) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeWifiSsid(ssid) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.wifi_monitor_remove_ssid, ssid),
                            )
                        }
                    },
                )
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newSsid,
                        onValueChange = { newSsid = it },
                        label = { Text(stringResource(R.string.wifi_monitor_ssid_label)) },
                        modifier = Modifier.weight(1f).hideSoftKeyboardOnFocus(),
                        singleLine = true,
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
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.wifi_monitor_notify_discovered)) },
                    supportingContent = { Text(stringResource(R.string.wifi_monitor_scan_based)) },
                    trailingContent = {
                        Switch(
                            checked = config.wifi.notifyOnDiscovered,
                            onCheckedChange = { viewModel.updateWifiNotifyOnDiscovered(it) },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        )
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.wifi_monitor_notify_lost)) },
                    supportingContent = { Text(stringResource(R.string.wifi_monitor_scan_based)) },
                    trailingContent = {
                        Switch(
                            checked = config.wifi.notifyOnLost,
                            onCheckedChange = { viewModel.updateWifiNotifyOnLost(it) },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        )
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.wifi_monitor_notify_connected)) },
                    supportingContent = { Text(stringResource(R.string.wifi_monitor_real_time)) },
                    trailingContent = {
                        Switch(
                            checked = config.wifi.notifyOnConnected,
                            onCheckedChange = { viewModel.updateWifiNotifyOnConnected(it) },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        )
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.wifi_monitor_notify_disconnected)) },
                    supportingContent = { Text(stringResource(R.string.wifi_monitor_real_time)) },
                    trailingContent = {
                        Switch(
                            checked = config.wifi.notifyOnDisconnected,
                            onCheckedChange = { viewModel.updateWifiNotifyOnDisconnected(it) },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        )
                    },
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(
                        stringResource(R.string.wifi_monitor_throttling_note),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
