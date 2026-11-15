@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The uniform compact callout card used on the server screen: icon + title on the first row, an
 * optional [body] paragraph, and (optionally) right-aligned action buttons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalloutCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actions: (@Composable FlowRowScope.() -> Unit)? = null,
) {
    Card(modifier = modifier.fillMaxWidth(), insideMargin = CardContentPadding) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = MiuixTheme.textStyles.headline2,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (actions != null) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions()
            }
        }
    }
}

/** Inner padding of free-form (non-preference) card content. */
val CardContentPadding = PaddingValues(16.dp)
