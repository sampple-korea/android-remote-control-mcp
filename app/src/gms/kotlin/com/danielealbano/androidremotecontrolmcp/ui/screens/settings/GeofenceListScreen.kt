@file:Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import android.location.Geocoder
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.GeofenceZone
import com.danielealbano.androidremotecontrolmcp.ui.components.AppDialog
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixLazyPage
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.GeofenceSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.IOException

@Composable
fun GeofenceListScreen(
    viewModel: GeofenceSettingsViewModel,
    onNavigateToMap: (zoneId: String?) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val config by viewModel.geofenceConfig.collectAsStateWithLifecycle()
    var zoneToDelete by remember { mutableStateOf<GeofenceZone?>(null) }
    val streetNames = remember { mutableStateMapOf<String, String>() }
    val context = LocalContext.current

    // Reverse geocode all zones
    LaunchedEffect(config.zones) {
        withContext(Dispatchers.IO) {
            for (zone in config.zones) {
                if (zone.id in streetNames) continue
                try {
                    @Suppress("DEPRECATION")
                    val results = Geocoder(context).getFromLocation(zone.latitude, zone.longitude, 1)
                    val address = results?.firstOrNull()
                    val street =
                        if (address != null) {
                            buildString {
                                address.thoroughfare?.let { append(it) }
                                address.subThoroughfare?.let {
                                    if (isNotEmpty()) append(" ")
                                    append(it)
                                }
                                val cityParts =
                                    listOfNotNull(
                                        address.postalCode,
                                        address.locality ?: address.subAdminArea,
                                    )
                                if (cityParts.isNotEmpty()) {
                                    if (isNotEmpty()) append(", ")
                                    append(cityParts.joinToString(" "))
                                }
                                if (isEmpty()) address.getAddressLine(0)?.let { append(it) }
                            }
                        } else {
                            ""
                        }
                    streetNames[zone.id] = street
                } catch (_: IOException) {
                    streetNames[zone.id] = ""
                }
            }
        }
    }

    MiuixLazyPage(
        title = stringResource(R.string.geofence_list_title),
        onBack = onNavigateBack,
        actions = {
            IconButton(onClick = { onNavigateToMap(null) }, modifier = Modifier.padding(end = 16.dp)) {
                Icon(
                    Icons.Default.Add,
                    stringResource(R.string.geofence_add_zone),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
    ) {
        if (config.zones.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.geofence_list_empty),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            }
        } else {
            items(config.zones) { zone ->
                val street = streetNames[zone.id] ?: ""
                val summary =
                    stringResource(
                        R.string.geofence_zone_summary,
                        zone.latitude,
                        zone.longitude,
                        zone.radiusMeters.toInt(),
                    )
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = zone.name,
                        summary =
                            buildString {
                                if (street.isNotBlank()) append("$street\n")
                                append(summary)
                            },
                        endActions = {
                            IconButton(onClick = { zoneToDelete = zone }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.geofence_delete_zone, zone.name))
                            }
                        },
                        onClick = { onNavigateToMap(zone.id) },
                        onClickLabel = stringResource(R.string.geofence_edit_action),
                    )
                }
            }
        }
    }

    zoneToDelete?.let { zone ->
        AppDialog(
            title = stringResource(R.string.geofence_delete_dialog_title),
            message = stringResource(R.string.geofence_delete_dialog_message, zone.name),
            confirmText = stringResource(R.string.geofence_delete_dialog_confirm),
            onConfirm = {
                viewModel.removeGeofenceZone(zone.id)
                zoneToDelete = null
            },
            dismissText = stringResource(R.string.geofence_delete_dialog_cancel),
            onDismissRequest = { zoneToDelete = null },
        )
    }
}
