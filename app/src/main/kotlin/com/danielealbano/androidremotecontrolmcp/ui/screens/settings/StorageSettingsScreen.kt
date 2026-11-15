@file:Suppress("FunctionNaming", "LongMethod", "CyclomaticComplexMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.BuiltinAccessLevel
import com.danielealbano.androidremotecontrolmcp.data.model.BuiltinStorageLocation
import com.danielealbano.androidremotecontrolmcp.data.model.StorageLocation
import com.danielealbano.androidremotecontrolmcp.services.storage.StorageLocationProvider
import com.danielealbano.androidremotecontrolmcp.ui.components.AppDialog
import com.danielealbano.androidremotecontrolmcp.ui.components.AppTextField
import com.danielealbano.androidremotecontrolmcp.ui.components.CardContentPadding
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixPage
import com.danielealbano.androidremotecontrolmcp.ui.components.SwitchOnlyPreference
import com.danielealbano.androidremotecontrolmcp.ui.components.hideSoftKeyboardOnFocus
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.MainViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StorageSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val serverConfig by viewModel.serverConfig.collectAsStateWithLifecycle()
    val storageLocations by viewModel.storageLocations.collectAsStateWithLifecycle()
    val fileSizeLimitInput by viewModel.fileSizeLimitInput.collectAsStateWithLifecycle()
    val fileSizeLimitError by viewModel.fileSizeLimitError.collectAsStateWithLifecycle()
    val downloadTimeoutInput by viewModel.downloadTimeoutInput.collectAsStateWithLifecycle()
    val downloadTimeoutError by viewModel.downloadTimeoutError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.storageError.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogDescription by remember { mutableStateOf("") }
    var addDialogSelectedUri by remember { mutableStateOf<Uri?>(null) }
    var addDialogSelectedName by remember { mutableStateOf<String?>(null) }
    var addDialogDuplicateError by remember { mutableStateOf(false) }
    var addDialogDuplicateChecking by remember { mutableStateOf(false) }

    var showEditDialog by remember { mutableStateOf(false) }
    var editDialogLocation by remember { mutableStateOf<StorageLocation?>(null) }
    var editDialogDescription by remember { mutableStateOf("") }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteDialogLocation by remember { mutableStateOf<StorageLocation?>(null) }

    val scope = rememberCoroutineScope()

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { _ -> viewModel.refreshStorageLocations() }

    val unknownLocationName = stringResource(R.string.storage_location_unknown_name)
    val documentTreeLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                addDialogSelectedUri = uri
                val docFile = DocumentFile.fromTreeUri(context, uri)
                addDialogSelectedName = docFile?.name ?: uri.lastPathSegment ?: unknownLocationName
                addDialogDuplicateChecking = true
                addDialogDuplicateError = false
                scope.launch {
                    addDialogDuplicateError = viewModel.isDuplicateTreeUri(uri)
                    addDialogDuplicateChecking = false
                }
            }
        }

    MiuixPage(
        title = stringResource(R.string.settings_storage_title),
        modifier = modifier,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) {
        val builtinLocations = storageLocations.filter { it.isBuiltin }
        val userLocations = storageLocations.filter { !it.isBuiltin }

        // ── Built-in Locations Section ──────────────────────────────
        StorageSectionHeader(
            title = stringResource(R.string.storage_builtin_locations_title),
            description = stringResource(R.string.storage_builtin_locations_description),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            builtinLocations.forEach { location ->
                val builtin = BuiltinStorageLocation.fromLocationId(location.id)
                val readMediaPermissions =
                    builtin
                        ?.collections
                        ?.mapNotNull { it.readMediaPermission }
                        ?.distinct()
                        .orEmpty()
                val requestPermissions = builtinRequestPermissions(builtin)
                BuiltinStorageLocationRow(
                    location = location,
                    accessLevel = location.accessLevel,
                    readMediaPermissions = readMediaPermissions,
                    requestPermissions = requestPermissions,
                    onAllowWriteChange = { enabled ->
                        viewModel.updateLocationAllowWrite(location.id, enabled)
                    },
                    onAllowDeleteChange = { enabled ->
                        viewModel.updateLocationAllowDelete(location.id, enabled)
                    },
                    onRequestPermission = { permissions ->
                        permissionLauncher.launch(permissions.toTypedArray())
                    },
                )
            }
        }

        // ── User Locations Section ──────────────────────────────────
        StorageSectionHeader(
            title = stringResource(R.string.storage_user_locations_title),
            description = stringResource(R.string.storage_locations_description),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            if (userLocations.isEmpty()) {
                Text(
                    text = stringResource(R.string.storage_location_no_locations),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                userLocations.forEach { location ->
                    StorageLocationRow(
                        location = location,
                        onEdit = {
                            editDialogLocation = location
                            editDialogDescription = location.description
                            showEditDialog = true
                        },
                        onDelete = {
                            deleteDialogLocation = location
                            showDeleteDialog = true
                        },
                        onAllowWriteChange = { enabled ->
                            viewModel.updateLocationAllowWrite(location.id, enabled)
                        },
                        onAllowDeleteChange = { enabled ->
                            viewModel.updateLocationAllowDelete(location.id, enabled)
                        },
                    )
                }
            }
            Button(
                onClick = {
                    addDialogDescription = ""
                    addDialogSelectedUri = null
                    addDialogSelectedName = null
                    addDialogDuplicateError = false
                    addDialogDuplicateChecking = false
                    showAddDialog = true
                },
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.storage_location_add_button))
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), insideMargin = CardContentPadding) {
            AppTextField(
                value = fileSizeLimitInput,
                onValueChange = viewModel::updateFileSizeLimit,
                label = stringResource(R.string.storage_file_size_limit_label),
                error = fileSizeLimitError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppTextField(
                value = downloadTimeoutInput,
                onValueChange = viewModel::updateDownloadTimeout,
                label = stringResource(R.string.storage_download_timeout_label),
                error = downloadTimeoutError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchOnlyPreference(
                title = stringResource(R.string.storage_allow_http_downloads_label),
                summary = stringResource(R.string.storage_allow_http_downloads_description),
                checked = serverConfig.allowHttpDownloads,
                onCheckedChange = viewModel::updateAllowHttpDownloads,
            )
            SwitchOnlyPreference(
                title = stringResource(R.string.storage_allow_unverified_https_label),
                summary = stringResource(R.string.storage_allow_unverified_https_description),
                checked = serverConfig.allowUnverifiedHttpsCerts,
                onCheckedChange = viewModel::updateAllowUnverifiedHttpsCerts,
            )
            SwitchOnlyPreference(
                title = stringResource(R.string.storage_block_local_network_downloads_label),
                summary = stringResource(R.string.storage_block_local_network_downloads_description),
                checked = serverConfig.blockLocalNetworkDownloads,
                onCheckedChange = viewModel::updateBlockLocalNetworkDownloads,
            )
        }
    }

    // Add Storage Location Dialog
    if (showAddDialog) {
        AppDialog(
            title = stringResource(R.string.storage_location_add_dialog_title),
            confirmText = stringResource(R.string.storage_location_add_dialog_add),
            onConfirm = {
                addDialogSelectedUri?.let { uri ->
                    viewModel.addLocation(uri, addDialogDescription)
                }
                showAddDialog = false
            },
            confirmEnabled =
                addDialogSelectedUri != null &&
                    !addDialogDuplicateError &&
                    !addDialogDuplicateChecking,
            dismissText = stringResource(R.string.storage_location_add_dialog_cancel),
            onDismissRequest = { showAddDialog = false },
        ) {
            AppTextField(
                value = addDialogDescription,
                onValueChange = { newValue ->
                    if (newValue.length <= StorageLocationProvider.MAX_DESCRIPTION_LENGTH) {
                        addDialogDescription = newValue
                    }
                },
                label = stringResource(R.string.storage_location_add_dialog_description_label),
                supportingText =
                    stringResource(R.string.storage_location_add_dialog_description_hint) + " · " +
                        stringResource(
                            R.string.storage_location_description_counter,
                            addDialogDescription.length,
                            StorageLocationProvider.MAX_DESCRIPTION_LENGTH,
                        ),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
                fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
            )
            Button(
                onClick = { documentTreeLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.storage_location_add_dialog_browse))
            }
            if (addDialogSelectedName != null) {
                Text(
                    text =
                        stringResource(
                            R.string.storage_location_add_dialog_selected,
                            addDialogSelectedName!!,
                        ),
                    style = MiuixTheme.textStyles.body2,
                )
            } else {
                Text(
                    text = stringResource(R.string.storage_location_add_dialog_no_selection),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (addDialogDuplicateError) {
                Text(
                    text = stringResource(R.string.storage_location_add_dialog_duplicate),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                )
            }
        }
    }

    // Edit Description Dialog
    if (showEditDialog) {
        AppDialog(
            title = stringResource(R.string.storage_location_edit_dialog_title),
            confirmText = stringResource(R.string.storage_location_edit_dialog_save),
            onConfirm = {
                editDialogLocation?.let { location ->
                    viewModel.updateLocationDescription(location.id, editDialogDescription)
                }
                showEditDialog = false
            },
            dismissText = stringResource(R.string.storage_location_add_dialog_cancel),
            onDismissRequest = { showEditDialog = false },
        ) {
            AppTextField(
                value = editDialogDescription,
                onValueChange = { newValue ->
                    if (newValue.length <= StorageLocationProvider.MAX_DESCRIPTION_LENGTH) {
                        editDialogDescription = newValue
                    }
                },
                label = stringResource(R.string.storage_location_add_dialog_description_label),
                supportingText =
                    stringResource(
                        R.string.storage_location_description_counter,
                        editDialogDescription.length,
                        StorageLocationProvider.MAX_DESCRIPTION_LENGTH,
                    ),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
                fieldModifier = Modifier.hideSoftKeyboardOnFocus(),
            )
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AppDialog(
            title = stringResource(R.string.storage_location_delete_dialog_title),
            message =
                deleteDialogLocation?.let { location ->
                    stringResource(R.string.storage_location_delete_dialog_message, location.name)
                },
            confirmText = stringResource(R.string.storage_location_delete_dialog_confirm),
            onConfirm = {
                deleteDialogLocation?.let { location ->
                    viewModel.removeLocation(location.id)
                }
                showDeleteDialog = false
            },
            dismissText = stringResource(R.string.storage_location_delete_dialog_cancel),
            onDismissRequest = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun StorageSectionHeader(
    title: String,
    description: String,
) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Text(
            text = description,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Suppress("LongMethod")
@Composable
private fun StorageLocationRow(
    location: StorageLocation,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAllowWriteChange: (Boolean) -> Unit,
    onAllowDeleteChange: (Boolean) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = location.name,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = location.path,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (location.description.isNotEmpty()) {
                    Text(
                        text = location.description,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.storage_location_edit_description_of, location.name),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.storage_location_remove_named, location.name),
                )
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 0.dp, top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Spoken with the location's name, on the switch: rows of several locations look alike
            val allowWriteOn = stringResource(R.string.storage_location_allow_write_on, location.name)
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.storage_location_allow_write_label),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = location.allowWrite,
                    onCheckedChange = onAllowWriteChange,
                    modifier = Modifier.semantics { contentDescription = allowWriteOn },
                )
            }
            val allowDeleteOn = stringResource(R.string.storage_location_allow_delete_on, location.name)
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.storage_location_allow_delete_label),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = location.allowDelete,
                    onCheckedChange = onAllowDeleteChange,
                    modifier = Modifier.semantics { contentDescription = allowDeleteOn },
                )
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun BuiltinStorageLocationRow(
    location: StorageLocation,
    accessLevel: BuiltinAccessLevel?,
    readMediaPermissions: List<String>,
    requestPermissions: List<String>,
    onAllowWriteChange: (Boolean) -> Unit,
    onAllowDeleteChange: (Boolean) -> Unit,
    onRequestPermission: (List<String>) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = location.name,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = location.path,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Spoken with the location's name, on the switch: rows of several locations look alike
            val allowWriteOn = stringResource(R.string.storage_location_allow_write_on, location.name)
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.storage_location_allow_write_label),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = location.allowWrite,
                    onCheckedChange = onAllowWriteChange,
                    modifier = Modifier.semantics { contentDescription = allowWriteOn },
                )
            }
            val allowDeleteOn = stringResource(R.string.storage_location_allow_delete_on, location.name)
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.storage_location_allow_delete_label),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = location.allowDelete,
                    onCheckedChange = onAllowDeleteChange,
                    modifier = Modifier.semantics { contentDescription = allowDeleteOn },
                )
            }
        }
        if (readMediaPermissions.isNotEmpty()) {
            Button(
                onClick = { onRequestPermission(requestPermissions) },
                enabled = builtinGrantButtonEnabled(accessLevel),
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(builtinGrantButtonLabelRes(accessLevel)))
            }
        }
    }
}

/** Permissions to request for a builtin location's grant button. */
internal fun builtinRequestPermissions(builtin: BuiltinStorageLocation?): List<String> {
    val readMediaPermissions =
        builtin
            ?.collections
            ?.mapNotNull { it.readMediaPermission }
            ?.distinct()
            .orEmpty()
    return if (builtin?.collections?.any { it.isVisual } == true) {
        readMediaPermissions + android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    } else {
        readMediaPermissions
    }
}

/** The grant button is disabled only when full access is already granted. */
internal fun builtinGrantButtonEnabled(level: BuiltinAccessLevel?): Boolean = level != BuiltinAccessLevel.FULL

@StringRes
internal fun builtinGrantButtonLabelRes(accessLevel: BuiltinAccessLevel?): Int =
    when (accessLevel) {
        BuiltinAccessLevel.FULL -> R.string.storage_builtin_all_files_granted
        BuiltinAccessLevel.PARTIAL -> R.string.storage_builtin_manage_access
        else -> R.string.storage_builtin_grant_all_files
    }
