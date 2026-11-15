@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A top bar back button, with its spoken label. */
@Composable
fun TopBarBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.padding(start = 16.dp)) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.navigate_back),
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}
