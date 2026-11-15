@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.NotificationFilterMode
import com.danielealbano.androidremotecontrolmcp.ui.components.AppTextField
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixLazyPage
import com.danielealbano.androidremotecontrolmcp.ui.components.SegmentedChoice
import com.danielealbano.androidremotecontrolmcp.ui.components.hideSoftKeyboardOnFocus
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ChannelViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NotificationFilterScreen(
    viewModel: ChannelViewModel,
    onNavigateBack: () -> Unit,
) {
    val config by viewModel.eventChannelConfig.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadInstalledApps() }

    val filterActive = config.notifications.filterMode != NotificationFilterMode.ALL
    val filtered =
        installedApps.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.packageId.contains(searchQuery, ignoreCase = true)
        }
    val appIcons by viewModel.appIcons.collectAsStateWithLifecycle()

    MiuixLazyPage(
        title = stringResource(R.string.notification_filter_title),
        onBack = onNavigateBack,
        itemSpacing = 0.dp,
    ) {
        item {
            val modes = NotificationFilterMode.entries
            SegmentedChoice(
                options = modes.map { stringResource(notificationFilterModeLabel(it)) },
                selectedIndex = modes.indexOf(config.notifications.filterMode),
                onSelect = { index -> viewModel.updateNotificationFilterMode(modes[index]) },
            )
        }

        if (filterActive) {
            item {
                AppTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = stringResource(R.string.notification_filter_search_label),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
                )
            }
            item {
                Text(
                    pluralStringResource(
                        R.plurals.notification_filter_selected_count,
                        config.notifications.filterApps.size,
                        config.notifications.filterApps.size,
                    ),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            items(filtered, key = { it.packageId }) { app ->
                CheckboxPreference(
                    title = app.name,
                    summary = app.packageId,
                    checked = app.packageId in config.notifications.filterApps,
                    onCheckedChange = { checked ->
                        viewModel.toggleNotificationFilterApp(app.packageId, checked)
                    },
                    checkboxLocation = CheckboxLocation.End,
                    startAction = {
                        val bitmap = appIcons[app.packageId]
                        val iconModifier = Modifier.padding(end = 12.dp).size(40.dp)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                // Decorative: the app's name is the row's title
                                contentDescription = null,
                                modifier = iconModifier,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Android,
                                // Decorative: the app's name is the row's title
                                contentDescription = null,
                                modifier = iconModifier,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    },
                )
            }
        }
    }
}

@StringRes
private fun notificationFilterModeLabel(mode: NotificationFilterMode): Int =
    when (mode) {
        NotificationFilterMode.ALL -> R.string.notification_filter_mode_all
        NotificationFilterMode.WHITELIST -> R.string.notification_filter_mode_whitelist
        NotificationFilterMode.BLACKLIST -> R.string.notification_filter_mode_blacklist
    }
