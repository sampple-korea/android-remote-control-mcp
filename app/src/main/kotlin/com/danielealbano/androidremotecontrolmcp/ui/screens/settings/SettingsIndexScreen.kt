@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixPage
import com.danielealbano.androidremotecontrolmcp.ui.components.NavigationPreference
import com.danielealbano.androidremotecontrolmcp.ui.navigation.SettingsRoute
import top.yukonga.miuix.kmp.basic.Card

@Composable
fun SettingsIndexScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    MiuixPage(title = stringResource(R.string.tab_settings), modifier = modifier) {
        SETTINGS_INDEX_GROUPS.forEach { group ->
            Card(modifier = Modifier.fillMaxWidth()) {
                group.forEach { entry ->
                    NavigationPreference(
                        icon = entry.icon,
                        title = stringResource(entry.titleRes),
                        summary = stringResource(entry.subtitleRes),
                        onClick = { onNavigate(entry.route) },
                    )
                }
            }
        }
    }
}

private class SettingsIndexEntry(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val route: String,
)

private val SETTINGS_INDEX_ENTRIES =
    listOf(
        SettingsIndexEntry(
            icon = Icons.Default.Tune,
            titleRes = R.string.settings_general_title,
            subtitleRes = R.string.settings_general_subtitle,
            route = SettingsRoute.General.route,
        ),
        SettingsIndexEntry(
            icon = Icons.Default.Key,
            titleRes = R.string.settings_access_title,
            subtitleRes = R.string.settings_access_subtitle,
            route = SettingsRoute.Access.route,
        ),
        SettingsIndexEntry(
            icon = Icons.Default.Lock,
            titleRes = R.string.settings_security_title,
            subtitleRes = R.string.settings_security_subtitle,
            route = SettingsRoute.Security.route,
        ),
        SettingsIndexEntry(
            icon = Icons.Default.Shield,
            titleRes = R.string.settings_privacy_title,
            subtitleRes = R.string.settings_privacy_subtitle,
            route = SettingsRoute.Privacy.route,
        ),
        SettingsIndexEntry(
            icon = Icons.Default.Cloud,
            titleRes = R.string.settings_tunnel_title,
            subtitleRes = R.string.settings_tunnel_subtitle,
            route = SettingsRoute.Tunnel.route,
        ),
        SettingsIndexEntry(
            icon = Icons.Default.Build,
            titleRes = R.string.settings_mcp_tools_title,
            subtitleRes = R.string.settings_mcp_tools_subtitle,
            route = SettingsRoute.McpTools.route,
        ),
        SettingsIndexEntry(
            icon = Icons.Default.Terminal,
            titleRes = R.string.settings_shell_title,
            subtitleRes = R.string.settings_shell_subtitle,
            route = SettingsRoute.Shell.route,
        ),
        SettingsIndexEntry(
            icon = Icons.Default.AdminPanelSettings,
            titleRes = R.string.settings_permissions_title,
            subtitleRes = R.string.settings_permissions_subtitle,
            route = SettingsRoute.Permissions.route,
        ),
        SettingsIndexEntry(
            icon = Icons.Default.Folder,
            titleRes = R.string.settings_storage_title,
            subtitleRes = R.string.settings_storage_subtitle,
            route = SettingsRoute.Storage.route,
        ),
    )

/** The entries split in cards: the server and its exposure first, then what the tools may do. */
private val SETTINGS_INDEX_GROUPS = listOf(SETTINGS_INDEX_ENTRIES.take(5), SETTINGS_INDEX_ENTRIES.drop(5))
