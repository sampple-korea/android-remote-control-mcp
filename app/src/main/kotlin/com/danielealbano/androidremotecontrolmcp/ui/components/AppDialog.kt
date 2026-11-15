@file:Suppress("FunctionNaming", "LongParameterList")

package com.danielealbano.androidremotecontrolmcp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * A Miuix dialog: [title], an optional [message], optional extra [content], then a row with the
 * dismiss button (when [dismissText] is set) and the highlighted confirm button.
 */
@Composable
fun AppDialog(
    title: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    show: Boolean = true,
    message: String? = null,
    dismissText: String? = null,
    onDismiss: () -> Unit = onDismissRequest,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    WindowDialog(
        show = show,
        title = title,
        summary = message,
        onDismissRequest = onDismissRequest,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (dismissText != null) {
                    TextButton(
                        text = dismissText,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    text = confirmText,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
