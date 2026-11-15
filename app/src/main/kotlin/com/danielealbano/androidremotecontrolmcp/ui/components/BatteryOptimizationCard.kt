@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.danielealbano.androidremotecontrolmcp.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
fun BatteryOptimizationCard(
    onRequestExemption: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CalloutCard(
        icon = Icons.Default.BatteryAlert,
        title = stringResource(R.string.battery_optimization_card_title),
        modifier = modifier,
    ) {
        TextButton(
            text = stringResource(R.string.battery_optimization_card_action),
            onClick = onRequestExemption,
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}
