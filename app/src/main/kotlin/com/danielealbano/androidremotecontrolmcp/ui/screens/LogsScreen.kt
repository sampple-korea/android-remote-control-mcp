@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber")

package com.danielealbano.androidremotecontrolmcp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.ServerLogEntry
import com.danielealbano.androidremotecontrolmcp.data.repository.ServerLogIndexEntry
import com.danielealbano.androidremotecontrolmcp.ui.components.AppDialog
import com.danielealbano.androidremotecontrolmcp.ui.components.ChoiceChip
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixLazyPage
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.LogsViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LOGS_TIME_FORMAT_PATTERN = "MMM d, HH:mm:ss"

/** Chip display order as agreed with the user — NOT enum declaration order. */
private val CHIP_DISPLAY_ORDER =
    listOf(
        ServerLogEntry.Type.SERVER,
        ServerLogEntry.Type.TOOL_CALL,
        ServerLogEntry.Type.TUNNEL,
        ServerLogEntry.Type.OAUTH,
        ServerLogEntry.Type.AUTH,
        ServerLogEntry.Type.CHANNEL,
        ServerLogEntry.Type.SETTINGS,
        ServerLogEntry.Type.SHELL,
    )

@Composable
fun LogsScreen(
    onBack: () -> Unit,
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val selectedTypes by viewModel.selectedTypes.collectAsStateWithLifecycle()
    val filteredIndex by viewModel.filteredIndex.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    MiuixLazyPage(
        title = stringResource(R.string.server_logs_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = { showClearDialog = true }, modifier = Modifier.padding(end = 16.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.server_logs_clear),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
    ) {
        item(key = "filters") {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CHIP_DISPLAY_ORDER.forEach { type ->
                    ChoiceChip(
                        selected = type in selectedTypes,
                        onClick = { viewModel.toggleType(type) },
                        label = typeLabel(type),
                    )
                }
            }
        }

        if (filteredIndex.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.server_logs_empty),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            }
        } else {
            items(count = filteredIndex.size, key = { filteredIndex[it].cacheKey }) { i ->
                val ref = filteredIndex[i]
                val entry by produceState<ServerLogEntry?>(initialValue = null, ref) {
                    value = viewModel.entryAt(ref)
                }
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    LogEntryRow(ref = ref, entry = entry)
                }
            }
        }
    }

    if (showClearDialog) {
        AppDialog(
            title = stringResource(R.string.server_logs_clear_dialog_title),
            message = stringResource(R.string.server_logs_clear_dialog_body),
            confirmText = stringResource(R.string.server_logs_clear_dialog_confirm),
            onConfirm = {
                viewModel.clearLogs()
                showClearDialog = false
            },
            dismissText = stringResource(R.string.server_logs_clear_dialog_cancel),
            onDismissRequest = { showClearDialog = false },
        )
    }
}

@Composable
private fun LogEntryRow(
    ref: ServerLogIndexEntry,
    entry: ServerLogEntry?,
) {
    val timeFormat = remember { SimpleDateFormat(LOGS_TIME_FORMAT_PATTERN, Locale.getDefault()) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = timeFormat.format(Date(ref.timestamp)),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = typeLabel(ref.type),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (ref.type == ServerLogEntry.Type.TOOL_CALL) {
                Text(
                    text = stringResource(R.string.server_logs_duration_ms, ref.durationMs ?: 0),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (ref.type == ServerLogEntry.Type.TOOL_CALL) {
            Text(
                text = entry?.toolName ?: "…",
                style = MiuixTheme.textStyles.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!entry?.message.isNullOrEmpty()) {
                Text(
                    text = entry.message,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = entry?.message.orEmpty(),
                style = MiuixTheme.textStyles.body2,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun typeLabel(type: ServerLogEntry.Type): String =
    stringResource(
        when (type) {
            ServerLogEntry.Type.TOOL_CALL -> R.string.server_logs_type_tool_call
            ServerLogEntry.Type.TUNNEL -> R.string.server_logs_type_tunnel
            ServerLogEntry.Type.SERVER -> R.string.server_logs_type_server
            ServerLogEntry.Type.OAUTH -> R.string.server_logs_type_oauth
            ServerLogEntry.Type.AUTH -> R.string.server_logs_type_auth
            ServerLogEntry.Type.CHANNEL -> R.string.server_logs_type_channel
            ServerLogEntry.Type.SETTINGS -> R.string.server_logs_type_settings
            ServerLogEntry.Type.PRIVACY -> R.string.server_logs_type_privacy
            ServerLogEntry.Type.SHELL -> R.string.server_logs_type_shell
        },
    )
