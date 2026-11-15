@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.CertificateSource
import com.danielealbano.androidremotecontrolmcp.data.model.ServerStatus
import com.danielealbano.androidremotecontrolmcp.ui.components.AppTextField
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixPage
import com.danielealbano.androidremotecontrolmcp.ui.components.hideSoftKeyboardOnFocus
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.MainViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val serverConfig by viewModel.serverConfig.collectAsStateWithLifecycle()
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val hostnameInput by viewModel.hostnameInput.collectAsStateWithLifecycle()
    val hostnameError by viewModel.hostnameError.collectAsStateWithLifecycle()

    val isEnabled =
        serverStatus !is ServerStatus.Running &&
            serverStatus !is ServerStatus.Starting

    MiuixPage(
        title = stringResource(R.string.settings_security_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = stringResource(R.string.config_https_enabled_label),
                checked = serverConfig.httpsEnabled,
                onCheckedChange = viewModel::updateHttpsEnabled,
                enabled = isEnabled,
            )
        }

        // HTTPS Certificate Section (visible only when HTTPS is enabled)
        AnimatedVisibility(visible = serverConfig.httpsEnabled) {
            Column {
                SmallTitle(text = stringResource(R.string.config_certificate_title))
                Card(modifier = Modifier.fillMaxWidth()) {
                    // Certificate source selector
                    Column(modifier = Modifier.selectableGroup()) {
                        CertificateSource.entries.forEach { source ->
                            RadioButtonPreference(
                                title =
                                    when (source) {
                                        CertificateSource.AUTO_GENERATED -> {
                                            stringResource(R.string.config_cert_auto_generated)
                                        }

                                        CertificateSource.CUSTOM -> {
                                            stringResource(R.string.config_cert_custom)
                                        }
                                    },
                                selected = source == serverConfig.certificateSource,
                                onClick = { viewModel.updateCertificateSource(source) },
                                enabled = isEnabled,
                            )
                        }
                    }

                    if (serverConfig.certificateSource == CertificateSource.AUTO_GENERATED) {
                        AppTextField(
                            value = hostnameInput,
                            onValueChange = viewModel::updateCertificateHostname,
                            label = stringResource(R.string.config_hostname_label),
                            error = hostnameError,
                            enabled = isEnabled,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
                        )
                    }
                }
            }
        }
    }
}
