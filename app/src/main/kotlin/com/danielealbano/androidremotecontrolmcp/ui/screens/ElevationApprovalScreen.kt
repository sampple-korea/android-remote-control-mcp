@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.services.shell.approval.PendingElevation
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ElevationApprovalViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ElevationApprovalScreen(
    requestId: Int,
    onDone: () -> Unit,
    viewModel: ElevationApprovalViewModel = hiltViewModel(),
) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val request = pending.firstOrNull { it.id == requestId }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            Text(stringResource(R.string.elevation_approval_title), style = MiuixTheme.textStyles.title2)
            Spacer(Modifier.height(16.dp))
            if (request == null) {
                Text(stringResource(R.string.elevation_approval_gone))
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.deny(requestId)
                        onDone()
                    },
                ) { Text(stringResource(R.string.elevation_approval_close)) }
            } else {
                ElevationRequestDetails(request)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            viewModel.deny(request.id)
                            onDone()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.elevation_deny)) }
                    Button(
                        onClick = {
                            viewModel.approve(request.id)
                            onDone()
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.elevation_approve)) }
                }
            }
        }
    }
}

@Composable
private fun ElevationRequestDetails(request: PendingElevation) {
    Text(stringResource(R.string.elevation_approval_reason_label), style = MiuixTheme.textStyles.subtitle)
    Text(request.justification, style = MiuixTheme.textStyles.body1)
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.elevation_approval_command_label), style = MiuixTheme.textStyles.subtitle)
    SelectionContainer {
        Text(
            request.displayCommand,
            style = MiuixTheme.textStyles.body2,
            fontFamily = FontFamily.Monospace,
        )
    }
}
