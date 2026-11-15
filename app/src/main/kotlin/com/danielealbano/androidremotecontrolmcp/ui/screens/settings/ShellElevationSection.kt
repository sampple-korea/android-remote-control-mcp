@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.ShellConfig
import com.danielealbano.androidremotecontrolmcp.services.shell.ShizukuState
import com.danielealbano.androidremotecontrolmcp.ui.components.AppTextField
import com.danielealbano.androidremotecontrolmcp.ui.components.hideSoftKeyboardOnFocus
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ShellSwitch
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.SuStatus
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ShellElevationSection(
    config: ShellConfig,
    shizukuState: ShizukuState,
    suStatus: SuStatus,
    onSwitch: (ShellSwitch, Boolean) -> Unit,
    onGrantShizuku: () -> Unit,
    onRestartApp: () -> Unit,
    onTestSu: () -> Unit,
    onAddAllowlistPattern: (String) -> Unit,
    onRemoveAllowlistPattern: (String) -> Unit,
) {
    Column {
        SmallTitle(text = stringResource(R.string.shell_elevation_title))
        Card(modifier = Modifier.fillMaxWidth()) {
            ShellSwitchRow(
                titleRes = R.string.shell_elevation_switch,
                descriptionRes = R.string.shell_elevation_switch_description,
                checked = config.elevationEnabled,
            ) { onSwitch(ShellSwitch.ELEVATION, it) }
            AnimatedVisibility(visible = config.elevationEnabled) {
                Column {
                    ShizukuStatusRow(shizukuState, onGrantShizuku, onRestartApp)
                    SuStatusRow(suStatus, onTestSu)
                    ShellSwitchRow(
                        titleRes = R.string.shell_auto_allow_switch,
                        descriptionRes = R.string.shell_auto_allow_description,
                        checked = config.autoAllowElevation,
                    ) { onSwitch(ShellSwitch.AUTO_ALLOW, it) }
                    if (!config.autoAllowElevation) {
                        ShellPatternListEditor(
                            titleRes = R.string.shell_allowlist_title,
                            descriptionRes = R.string.shell_allowlist_description,
                            patterns = config.allowlist,
                            onAdd = onAddAllowlistPattern,
                            onRemove = onRemoveAllowlistPattern,
                        )
                    }
                }
            }
        }
    }
}

/** A pattern list with its add field, laid out to sit inside a card. */
@Composable
internal fun ShellPatternListEditor(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    patterns: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(titleRes), style = MiuixTheme.textStyles.headline2)
        Text(
            stringResource(descriptionRes),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(8.dp))
        if (patterns.isEmpty()) {
            Text(
                stringResource(R.string.shell_patterns_empty),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        patterns.forEach { pattern ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(pattern, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(pattern) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.shell_pattern_remove, pattern),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            AppTextField(
                value = input,
                onValueChange = { input = it },
                label = stringResource(R.string.shell_pattern_input_label),
                modifier = Modifier.weight(1f),
                fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onAdd(input)
                    input = ""
                },
                enabled = input.isNotBlank(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(stringResource(R.string.shell_pattern_add)) }
        }
    }
}

@Composable
private fun ShellSwitchRow(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    // Only the switch itself toggles: a whole-row toggleable flipped these safety switches when a touch
    // merely grazed the row (e.g. while scrolling the Shell settings), which silently turned elevation off.
    BasicComponent(
        title = stringResource(titleRes),
        summary = stringResource(descriptionRes),
        endActions = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun ShizukuStatusRow(
    state: ShizukuState,
    onGrant: () -> Unit,
    onRestart: () -> Unit,
) {
    BasicComponent(
        title = stringResource(R.string.shell_shizuku_label),
        summary = shizukuStatusText(state),
        endActions = {
            if (state == ShizukuState.NotGranted) {
                TextButton(
                    text = stringResource(R.string.shell_shizuku_grant),
                    onClick = onGrant,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
            if (state == ShizukuState.NeedsRestart) {
                TextButton(
                    text = stringResource(R.string.shell_shizuku_restart),
                    onClick = onRestart,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}

@Composable
private fun SuStatusRow(
    status: SuStatus,
    onTest: () -> Unit,
) {
    BasicComponent(
        title = stringResource(R.string.shell_su_label),
        summary = suStatusText(status),
        endActions = {
            if (status !is SuStatus.NotFound && status !is SuStatus.Testing) {
                TextButton(text = stringResource(R.string.shell_su_test), onClick = onTest)
            }
        },
    )
}

@Composable
private fun shizukuStatusText(state: ShizukuState): String =
    when (state) {
        ShizukuState.NotInstalled -> stringResource(R.string.shell_shizuku_not_installed)
        ShizukuState.NotRunning -> stringResource(R.string.shell_shizuku_not_running)
        ShizukuState.NeedsRestart -> stringResource(R.string.shell_shizuku_needs_restart)
        ShizukuState.Outdated -> stringResource(R.string.shell_shizuku_outdated)
        ShizukuState.NotGranted -> stringResource(R.string.shell_shizuku_not_granted)
        is ShizukuState.Ready -> stringResource(R.string.shell_shizuku_ready, state.uid)
    }

@Composable
private fun suStatusText(status: SuStatus): String =
    when (status) {
        SuStatus.NotFound -> stringResource(R.string.shell_su_not_found)
        is SuStatus.Found -> stringResource(R.string.shell_su_found, status.path)
        is SuStatus.Testing -> stringResource(R.string.shell_su_testing, status.path)
        is SuStatus.Works -> stringResource(R.string.shell_su_works, status.path)
        is SuStatus.Fails -> stringResource(R.string.shell_su_fails, status.path, status.reason)
    }
