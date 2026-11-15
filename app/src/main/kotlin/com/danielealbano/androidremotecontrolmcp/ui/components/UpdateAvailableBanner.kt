@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Compact, full-width band shown at the top of the screen when a newer release is available. Tapping
 * it invokes [onClick], which opens the release page in the default browser.
 */
@Composable
fun UpdateAvailableBanner(
    versionName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLink = stringResource(R.string.a11y_action_open_link)
    val contentColor = MiuixTheme.colorScheme.onPrimaryContainer
    Surface(
        onClick = onClick,
        color = MiuixTheme.colorScheme.primaryContainer,
        contentColor = contentColor,
        shape = RectangleShape,
        // The click label, which Surface takes no parameter for: on the same node as Surface's own click, a label-only
        // action keeps that click's action
        modifier = modifier.fillMaxWidth().semantics { onClick(label = openLink, action = null) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Filled.SystemUpdate, contentDescription = null, tint = contentColor)
            Spacer(Modifier.width(12.dp))
            // Spoken without the sighted "tap to view": the click label says what a double tap does
            val spoken = stringResource(R.string.update_banner_text_spoken, versionName)
            Text(
                text = stringResource(R.string.update_banner_text, versionName),
                style = MiuixTheme.textStyles.body2,
                color = contentColor,
                modifier = Modifier.weight(1f).semantics { contentDescription = spoken },
            )
            Spacer(Modifier.width(12.dp))
            // Decorative: the click label says it
            Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = contentColor)
        }
    }
}
