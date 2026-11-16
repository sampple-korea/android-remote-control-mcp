@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber", "LongParameterList")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixPage
import com.danielealbano.androidremotecontrolmcp.ui.theme.isInDarkTheme
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.MainViewModel
import com.danielealbano.androidremotecontrolmcp.utils.PermissionUtils
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun enabledColor(): Color = if (isInDarkTheme()) Color(0xFF81C784) else Color(0xFF4CAF50)

@Composable
private fun disabledColor(): Color = if (isInDarkTheme()) Color(0xFFEF5350) else Color(0xFFF44336)

@Composable
fun PermissionsSettingsScreen(
    onBack: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onRequestMicrophonePermission: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsStateWithLifecycle()
    val isNotificationPermissionGranted by viewModel.isNotificationPermissionGranted.collectAsStateWithLifecycle()
    val isNotificationListenerEnabled by viewModel.isNotificationListenerEnabled.collectAsStateWithLifecycle()
    val isCameraPermissionGranted by viewModel.isCameraPermissionGranted.collectAsStateWithLifecycle()
    val isMicrophonePermissionGranted by viewModel.isMicrophonePermissionGranted.collectAsStateWithLifecycle()
    val isLocationPermissionGranted by viewModel.isLocationPermissionGranted.collectAsStateWithLifecycle()

    // Refresh permissions on ON_RESUME
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshPermissionStatus(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    MiuixPage(
        title = stringResource(R.string.settings_permissions_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        // ---- Required ----
        PermissionSectionHeader(
            title = stringResource(R.string.permission_section_required_title),
            subtitle = stringResource(R.string.permission_section_required_subtitle),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            PermissionRow(
                label = stringResource(R.string.permission_accessibility),
                rationale = stringResource(R.string.permission_accessibility_rationale),
                isEnabled = isAccessibilityEnabled,
                buttonText =
                    if (isAccessibilityEnabled) {
                        stringResource(R.string.permission_enabled)
                    } else {
                        stringResource(R.string.permission_enable)
                    },
                onAction = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                actionEnabled = !isAccessibilityEnabled,
            )
        }

        // ---- Required if you use a feature ----
        PermissionSectionHeader(
            title = stringResource(R.string.permission_section_required_if_title),
            subtitle = stringResource(R.string.permission_section_required_if_subtitle),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            PermissionRow(
                label = stringResource(R.string.permission_notifications),
                rationale = stringResource(R.string.permission_notifications_rationale),
                isEnabled = isNotificationPermissionGranted,
                buttonText =
                    if (isNotificationPermissionGranted) {
                        stringResource(R.string.permission_granted)
                    } else {
                        stringResource(R.string.permission_grant)
                    },
                onAction = onRequestNotificationPermission,
                actionEnabled = !isNotificationPermissionGranted,
            )

            PermissionRow(
                label = stringResource(R.string.permission_notification_listener),
                rationale = stringResource(R.string.permission_notification_listener_rationale),
                isEnabled = isNotificationListenerEnabled,
                buttonText =
                    if (isNotificationListenerEnabled) {
                        stringResource(R.string.permission_enabled)
                    } else {
                        stringResource(R.string.permission_enable)
                    },
                onAction = { PermissionUtils.openNotificationListenerSettings(context) },
                actionEnabled = !isNotificationListenerEnabled,
            )

            PermissionRow(
                label = stringResource(R.string.permission_camera),
                rationale = stringResource(R.string.permission_camera_rationale),
                isEnabled = isCameraPermissionGranted,
                buttonText =
                    if (isCameraPermissionGranted) {
                        stringResource(R.string.permission_granted)
                    } else {
                        stringResource(R.string.permission_grant)
                    },
                onAction = onRequestCameraPermission,
                actionEnabled = !isCameraPermissionGranted,
            )

            PermissionRow(
                label = stringResource(R.string.permission_location),
                rationale = stringResource(R.string.permission_location_rationale),
                isEnabled = isLocationPermissionGranted,
                buttonText =
                    if (isLocationPermissionGranted) {
                        stringResource(R.string.permission_granted)
                    } else {
                        stringResource(R.string.permission_grant)
                    },
                onAction = onRequestLocationPermission,
                actionEnabled = !isLocationPermissionGranted,
            )
        }

        // ---- Optional ----
        PermissionSectionHeader(
            title = stringResource(R.string.permission_section_optional_title),
            subtitle = stringResource(R.string.permission_section_optional_subtitle),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            PermissionRow(
                label = stringResource(R.string.permission_microphone),
                rationale = stringResource(R.string.permission_microphone_rationale),
                isEnabled = isMicrophonePermissionGranted,
                buttonText =
                    if (isMicrophonePermissionGranted) {
                        stringResource(R.string.permission_granted)
                    } else {
                        stringResource(R.string.permission_grant)
                    },
                onAction = onRequestMicrophonePermission,
                actionEnabled = !isMicrophonePermissionGranted,
            )
        }
    }
}

@Composable
internal fun PermissionRow(
    label: String,
    rationale: String,
    isEnabled: Boolean,
    buttonText: String,
    onAction: () -> Unit,
    actionEnabled: Boolean,
    spokenButtonText: String = buttonText,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Decorative: the button says the state ("Granted", "Grant", ...), or a [spokenButtonText] does
        Icon(
            imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (isEnabled) enabledColor() else disabledColor(),
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MiuixTheme.textStyles.body1)
            Text(
                text = rationale,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        val actionDescription = stringResource(R.string.permission_action_description, label, spokenButtonText)
        Button(onClick = onAction, enabled = actionEnabled, colors = ButtonDefaults.buttonColorsPrimary()) {
            // On the text: the button reads its content, and the description replaces the bare "Grant"
            Text(text = buttonText, modifier = Modifier.semantics { contentDescription = actionDescription })
        }
    }
}

@Composable
private fun PermissionSectionHeader(
    title: String,
    subtitle: String,
) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
