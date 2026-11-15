@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.BindingAddress
import com.danielealbano.androidremotecontrolmcp.data.model.ServerStatus
import com.danielealbano.androidremotecontrolmcp.ui.components.AppDialog
import com.danielealbano.androidremotecontrolmcp.ui.components.AppTextField
import com.danielealbano.androidremotecontrolmcp.ui.components.CardContentPadding
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixPage
import com.danielealbano.androidremotecontrolmcp.ui.components.SegmentedChoice
import com.danielealbano.androidremotecontrolmcp.ui.components.hideSoftKeyboardOnFocus
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.MainViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun GeneralSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val serverConfig by viewModel.serverConfig.collectAsStateWithLifecycle()
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val portInput by viewModel.portInput.collectAsStateWithLifecycle()
    val portError by viewModel.portError.collectAsStateWithLifecycle()
    val specificBindAddressInput by viewModel.specificBindAddressInput.collectAsStateWithLifecycle()
    val listenerCheck by viewModel.listenerCheck.collectAsStateWithLifecycle()
    val deviceSlugInput by viewModel.deviceSlugInput.collectAsStateWithLifecycle()
    val deviceSlugError by viewModel.deviceSlugError.collectAsStateWithLifecycle()

    val isEnabled =
        serverStatus !is ServerStatus.Running &&
            serverStatus !is ServerStatus.Starting

    // All networks and a specific IP expose the server beyond the device: both ask for the same confirmation
    var pendingExposedBinding by remember { mutableStateOf<BindingAddress?>(null) }

    pendingExposedBinding?.let { pending ->
        AppDialog(
            title = stringResource(R.string.network_warning_title),
            message = stringResource(R.string.network_warning_message),
            confirmText = stringResource(R.string.network_warning_confirm),
            onConfirm = {
                pendingExposedBinding = null
                viewModel.updateBindingAddress(pending)
            },
            dismissText = stringResource(R.string.network_warning_cancel),
            onDismissRequest = { pendingExposedBinding = null },
        )
    }

    MiuixPage(
        title = stringResource(R.string.settings_general_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        // Binding Address Selector
        SmallTitle(text = stringResource(R.string.config_binding_address_label))
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardContentPadding) {
            val options = BindingAddress.entries
            SegmentedChoice(
                options =
                    options.map { address ->
                        when (address) {
                            BindingAddress.LOCALHOST -> stringResource(R.string.config_binding_localhost)
                            BindingAddress.NETWORK -> stringResource(R.string.config_binding_network)
                            BindingAddress.SPECIFIC -> stringResource(R.string.config_binding_specific)
                        }
                    },
                selectedIndex = options.indexOf(serverConfig.bindingAddress),
                onSelect = { index ->
                    val address = options[index]
                    if (bindingNeedsExposureWarning(address)) {
                        pendingExposedBinding = address
                    } else {
                        viewModel.updateBindingAddress(address)
                    }
                },
                enabled = isEnabled,
            )

            if (serverConfig.bindingAddress == BindingAddress.SPECIFIC) {
                AppTextField(
                    value = specificBindAddressInput,
                    onValueChange = viewModel::updateSpecificBindAddress,
                    label = stringResource(R.string.config_specific_ip_label),
                    error = listenerCheck.specificAddressError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    enabled = isEnabled,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
                )
            }

            // Port Input
            AppTextField(
                value = portInput,
                onValueChange = viewModel::updatePort,
                label = stringResource(R.string.config_port_label),
                error = portError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = isEnabled,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
            )

            // Device Slug Input
            AppTextField(
                value = deviceSlugInput,
                onValueChange = viewModel::updateDeviceSlug,
                label = stringResource(R.string.config_device_slug_label),
                error = deviceSlugError,
                supportingText = stringResource(R.string.config_device_slug_hint),
                enabled = isEnabled,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            // Tool-call Indicator Toggle
            SwitchPreference(
                title = stringResource(R.string.config_tool_call_indicator_label),
                summary = stringResource(R.string.config_tool_call_indicator_description),
                checked = serverConfig.toolCallIndicatorEnabled,
                onCheckedChange = viewModel::updateToolCallIndicatorEnabled,
                enabled = isEnabled,
            )

            // Auto-Start Toggle
            SwitchPreference(
                title = stringResource(R.string.config_auto_start_label),
                checked = serverConfig.autoStartOnBoot,
                onCheckedChange = viewModel::updateAutoStartOnBoot,
                enabled = isEnabled,
            )

            // Hide from Recents Toggle
            SwitchPreference(
                title = stringResource(R.string.config_hide_from_recents_label),
                summary = stringResource(R.string.config_hide_from_recents_description),
                checked = serverConfig.hideFromRecents,
                onCheckedChange = viewModel::updateHideFromRecents,
                enabled = isEnabled,
            )
        }
    }
}

/** Whether choosing [address] asks for the exposure warning first: it exposes the server beyond the device. */
internal fun bindingNeedsExposureWarning(address: BindingAddress): Boolean = address != BindingAddress.LOCALHOST
