@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.SHELL_OUTPUT_MEMORY_CAP_MIB
import com.danielealbano.androidremotecontrolmcp.data.model.ShellLimit
import com.danielealbano.androidremotecontrolmcp.data.model.ShellPatternKind
import com.danielealbano.androidremotecontrolmcp.ui.components.AppTextField
import com.danielealbano.androidremotecontrolmcp.ui.components.CardContentPadding
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixPage
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ShellLimitField
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ShellLimitInputError
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ShellSettingsViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ShellSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShellSettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.shellConfig.collectAsStateWithLifecycle()
    val fields by viewModel.limitFields.collectAsStateWithLifecycle()
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    val suStatus by viewModel.suStatus.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    MiuixPage(
        title = stringResource(R.string.settings_shell_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        ShellElevationSection(
            config = config,
            shizukuState = shizukuState,
            suStatus = suStatus,
            onSwitch = viewModel::setSwitch,
            onGrantShizuku = viewModel::requestShizukuPermission,
            onRestartApp = viewModel::restartApp,
            onTestSu = viewModel::testSu,
            onAddAllowlistPattern = { viewModel.addPattern(ShellPatternKind.ALLOWLIST, it) },
            onRemoveAllowlistPattern = { viewModel.removePattern(ShellPatternKind.ALLOWLIST, it) },
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            ShellPatternListEditor(
                titleRes = R.string.shell_blocklist_title,
                descriptionRes = R.string.shell_blocklist_description,
                patterns = config.blocklist,
                onAdd = { viewModel.addPattern(ShellPatternKind.BLOCKLIST, it) },
                onRemove = { viewModel.removePattern(ShellPatternKind.BLOCKLIST, it) },
            )
        }
        ShellLimitsSection(
            fields = fields,
            onChange = viewModel::updateLimit,
            onCommit = viewModel::commitLimit,
        )
    }
}

@Composable
private fun ShellLimitsSection(
    fields: Map<ShellLimit, ShellLimitField>,
    onChange: (ShellLimit, String) -> Unit,
    onCommit: (ShellLimit) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Column {
        SmallTitle(text = stringResource(R.string.shell_limits_title))
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardContentPadding) {
            Text(
                stringResource(R.string.shell_limits_memory_note),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            ShellLimit.entries.forEach { limit ->
                val field = fields[limit] ?: return@forEach
                AppTextField(
                    value = field.text,
                    onValueChange = { onChange(limit, it) },
                    label = stringResource(limitLabel(limit)),
                    error = field.error?.let { error -> limitErrorText(limit, error) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    // A limit is stored only when its field loses focus, so what gets saved is the
                    // final value the user settled on, not every intermediate keystroke state. Gaining
                    // focus hides the soft keyboard instead of showing it (see hideSoftKeyboardOnFocus).
                    fieldModifier =
                        Modifier.onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                keyboardController?.hide()
                            } else {
                                onCommit(limit)
                            }
                        },
                )
            }
        }
    }
}

@StringRes
private fun limitLabel(limit: ShellLimit): Int =
    when (limit) {
        ShellLimit.MAX_RUNNING_PROCESSES -> R.string.shell_limit_max_running_processes
        ShellLimit.MAX_FINISHED_PROCESSES -> R.string.shell_limit_max_finished_processes
        ShellLimit.BACKGROUND_TIMEOUT_DEFAULT_MINUTES -> R.string.shell_limit_background_timeout_default_minutes
        ShellLimit.BACKGROUND_TIMEOUT_MAX_MINUTES -> R.string.shell_limit_background_timeout_max_minutes
        ShellLimit.APPROVAL_TIMEOUT_SECONDS -> R.string.shell_limit_approval_timeout_seconds
        ShellLimit.FOREGROUND_WAIT_SECONDS -> R.string.shell_limit_foreground_wait_seconds
        ShellLimit.OUTPUT_PER_CALL_KIB -> R.string.shell_limit_output_per_call_kib
        ShellLimit.OUTPUT_BUFFER_KIB -> R.string.shell_limit_output_buffer_kib
    }

@Composable
private fun limitErrorText(
    limit: ShellLimit,
    error: ShellLimitInputError,
): String {
    val outOfRange = R.string.shell_limit_error_out_of_range
    val overMemory = R.string.shell_limit_error_over_memory_cap
    return when (error) {
        ShellLimitInputError.REQUIRED -> stringResource(R.string.shell_limit_error_required)
        ShellLimitInputError.NOT_A_NUMBER -> stringResource(R.string.shell_limit_error_not_a_number)
        ShellLimitInputError.OUT_OF_RANGE -> stringResource(outOfRange, limit.min, limit.max)
        ShellLimitInputError.ABOVE_MAX -> stringResource(R.string.shell_limit_error_above_max)
        ShellLimitInputError.BELOW_DEFAULT -> stringResource(R.string.shell_limit_error_below_default)
        ShellLimitInputError.OVER_MEMORY_CAP -> stringResource(overMemory, SHELL_OUTPUT_MEMORY_CAP_MIB)
    }
}
