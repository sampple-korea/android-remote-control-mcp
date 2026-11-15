@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel.ArcTunnelRenewalState
import com.danielealbano.androidremotecontrolmcp.ui.components.ArcTunnelRenewalDialog
import com.danielealbano.androidremotecontrolmcp.ui.components.LocalBottomBarInset
import com.danielealbano.androidremotecontrolmcp.ui.components.UpdateAvailableBanner
import com.danielealbano.androidremotecontrolmcp.ui.components.glass.FloatingBottomBar
import com.danielealbano.androidremotecontrolmcp.ui.components.glass.FloatingBottomBarItem
import com.danielealbano.androidremotecontrolmcp.ui.navigation.SettingsRoute
import com.danielealbano.androidremotecontrolmcp.ui.navigation.TopLevelRoute
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ArcTunnelRenewalViewModel
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ArcTunnelViewModel
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.MainViewModel
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.UpdateViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val FLOATING_BAR_HEIGHT_DP = 64

@Composable
fun MainScreen(
    onRequestNotificationPermission: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onRequestMicrophonePermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
    arcTunnelViewModel: ArcTunnelViewModel = hiltViewModel(),
    arcTunnelRenewalViewModel: ArcTunnelRenewalViewModel = hiltViewModel(),
) {
    var selectedTabRoute by rememberSaveable { mutableStateOf(TopLevelRoute.Server.route) }
    var pendingSettingsRoute by rememberSaveable { mutableStateOf<String?>(null) }
    val availableUpdate by updateViewModel.availableUpdate.collectAsStateWithLifecycle()
    val renewal by arcTunnelRenewalViewModel.renewal.collectAsStateWithLifecycle()
    val renewalModalVisible by arcTunnelRenewalViewModel.modalVisible.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Back on the Settings/About tab returns to the Server tab instead of leaving the app. The
    // settings NavHost registers its own back callback AFTER this one, so it wins while its back
    // stack is non-empty: back inside a settings sub-screen still pops to the settings index first.
    BackHandler(enabled = selectedTabRoute != TopLevelRoute.Server.route) {
        selectedTabRoute = TopLevelRoute.Server.route
    }

    val tabs =
        listOf(
            Triple(TopLevelRoute.Server, Icons.Default.Dns, stringResource(R.string.tab_server)),
            Triple(TopLevelRoute.Settings, Icons.Default.Settings, stringResource(R.string.tab_settings)),
            Triple(TopLevelRoute.About, Icons.Default.Info, stringResource(R.string.tab_about)),
        )
    val selectedIndex = tabs.indexOfFirst { it.first.route == selectedTabRoute }.coerceAtLeast(0)
    val navigateToSettings: (String) -> Unit = { route ->
        pendingSettingsRoute = route
        selectedTabRoute = TopLevelRoute.Settings.route
    }

    // What scrolls beneath the floating bar, recorded so the bar can blur and refract it (liquid glass)
    val surfaceColor = MiuixTheme.colorScheme.surface
    val barBackdrop =
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barBottomPadding = if (navigationBarInset != 0.dp) navigationBarInset + 8.dp else 28.dp

    Column(modifier = Modifier.fillMaxSize().background(surfaceColor)) {
        availableUpdate?.let { update ->
            UpdateAvailableBanner(
                versionName = update.versionName,
                onClick = {
                    // No-op if the device has no browser to handle ACTION_VIEW (never crash).
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl))) }
                },
            )
        }
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // The banner already sits under the status bar: the pages below must not pad for it again
                    .then(if (availableUpdate != null) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
        ) {
            CompositionLocalProvider(LocalBottomBarInset provides barBottomPadding + FLOATING_BAR_HEIGHT_DP.dp) {
                Box(modifier = Modifier.fillMaxSize().layerBackdrop(barBackdrop)) {
                    when (selectedTabRoute) {
                        TopLevelRoute.Settings.route -> {
                            SettingsScreen(
                                onRequestNotificationPermission = onRequestNotificationPermission,
                                onRequestCameraPermission = onRequestCameraPermission,
                                onRequestMicrophonePermission = onRequestMicrophonePermission,
                                onRequestLocationPermission = onRequestLocationPermission,
                                pendingRoute = pendingSettingsRoute,
                                onPendingRouteConsumed = { pendingSettingsRoute = null },
                                viewModel = viewModel,
                                arcTunnelViewModel = arcTunnelViewModel,
                            )
                        }

                        TopLevelRoute.About.route -> {
                            AboutScreen()
                        }

                        else -> {
                            ServerTabScreen(
                                onNavigateToPermissions = { navigateToSettings(SettingsRoute.Permissions.route) },
                                onNavigateToNetworkSettings = { navigateToSettings(SettingsRoute.General.route) },
                                onNavigateToTunnelSettings = { navigateToSettings(SettingsRoute.Tunnel.route) },
                                onOpenPrivacySettings = { navigateToSettings(SettingsRoute.Privacy.route) },
                                viewModel = viewModel,
                                arcTunnelViewModel = arcTunnelViewModel,
                            )
                        }
                    }
                }
            }

            FloatingBottomBar(
                selectedIndex = selectedIndex,
                onSelected = { index -> selectedTabRoute = tabs[index].first.route },
                backdrop = barBackdrop,
                tabsCount = tabs.size,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        // Taps between the tabs must not reach the page beneath the bar
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(start = 28.dp, end = 28.dp, bottom = barBottomPadding),
            ) { activateTab ->
                tabs.forEachIndexed { index, (_, icon, label) ->
                    FloatingBottomBarItem(
                        selected = selectedIndex == index,
                        onClick = { activateTab(index) },
                        modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                    ) {
                        // Icon and label take LocalContentColor, so the bar's accent copy can tint them inside
                        // the glass indicator. Decorative icon: the label says it
                        Icon(imageVector = icon, contentDescription = null)
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
        }
    }

    ArcTunnelRenewalDialog(
        visible = renewalModalVisible,
        cancelEnabled = renewal != ArcTunnelRenewalState.Installing,
        onHide = arcTunnelRenewalViewModel::hideModal,
        onCancel = { viewModel.stopServer(context) },
    )
}
