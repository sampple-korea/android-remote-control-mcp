package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.components.EventSourceRow
import com.danielealbano.androidremotecontrolmcp.ui.navigation.GeofenceRoutes
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.GeofenceSettingsViewModel
import top.yukonga.miuix.kmp.basic.Card

/** gms flavor: the "Geofence Events" row in the Event Channel settings list. */
fun LazyListScope.geofenceEventSourceItem(navController: NavHostController) {
    item {
        val viewModel: GeofenceSettingsViewModel = hiltViewModel()
        val config by viewModel.geofenceConfig.collectAsStateWithLifecycle()
        Card(modifier = Modifier.fillMaxWidth()) {
            EventSourceRow(
                title = stringResource(R.string.geofence_events),
                icon = Icons.Default.LocationOn,
                checked = config.enabled,
                onCheckedChange = { viewModel.updateGeofenceChannelEnabled(it) },
                onClick = { navController.navigate(GeofenceRoutes.LIST) },
            )
        }
    }
}
