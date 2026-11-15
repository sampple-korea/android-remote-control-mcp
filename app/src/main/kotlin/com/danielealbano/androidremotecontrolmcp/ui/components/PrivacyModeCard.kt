@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.danielealbano.androidremotecontrolmcp.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
fun PrivacyModeCard(
    onSetupClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CalloutCard(
        icon = Icons.Default.Lightbulb,
        title = stringResource(R.string.privacy_card_title),
        modifier = modifier,
    ) {
        TextButton(
            text = stringResource(R.string.privacy_card_action),
            onClick = onSetupClick,
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
        TextButton(text = stringResource(R.string.privacy_card_dismiss), onClick = onDismissClick)
    }
}
