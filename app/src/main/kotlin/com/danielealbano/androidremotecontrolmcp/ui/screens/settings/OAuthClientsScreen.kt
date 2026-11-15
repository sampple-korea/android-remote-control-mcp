@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.mcp.oauth.LogoUrlPolicy
import com.danielealbano.androidremotecontrolmcp.mcp.oauth.OAuthClient
import com.danielealbano.androidremotecontrolmcp.ui.components.AppDialog
import com.danielealbano.androidremotecontrolmcp.ui.components.MiuixLazyPage
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.OAuthClientsViewModel
import okhttp3.OkHttpClient
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.URI
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun OAuthClientsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OAuthClientsViewModel = hiltViewModel(),
) {
    val clients by viewModel.clients.collectAsStateWithLifecycle()

    MiuixLazyPage(
        title = stringResource(R.string.oauth_clients_title),
        modifier = modifier,
        onBack = onBack,
    ) {
        if (clients.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.oauth_clients_empty),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            items(clients, key = { it.clientId }) { client ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    ClientRow(client = client, onRevoke = { viewModel.revoke(client.clientId) })
                }
            }
        }
    }
}

@Composable
private fun ClientRow(
    client: OAuthClient,
    onRevoke: () -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    if (showConfirm) {
        AppDialog(
            title = stringResource(R.string.oauth_clients_revoke_dialog_title),
            message = stringResource(R.string.oauth_clients_revoke_dialog_body),
            confirmText = stringResource(R.string.oauth_clients_revoke_confirm),
            onConfirm = {
                showConfirm = false
                onRevoke()
            },
            dismissText = stringResource(R.string.oauth_clients_revoke_cancel),
            onDismissRequest = { showConfirm = false },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val clientLabel =
            client.clientName
                ?: hostOf(client.redirectUris.firstOrNull())
                ?: stringResource(R.string.oauth_clients_unknown_client)
        ClientAvatar(client)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = clientLabel,
                style = MiuixTheme.textStyles.body1,
            )
            Text(
                text = client.applicationType ?: "—",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.oauth_clients_created, dateFormat.format(Date(client.createdAtMs))),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = stringResource(R.string.oauth_clients_last_used, dateFormat.format(Date(client.lastUsedAtMs))),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        IconButton(onClick = { showConfirm = true }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.oauth_clients_revoke_named, clientLabel),
            )
        }
    }
}

@Composable
private fun ClientAvatar(client: OAuthClient) {
    val context = LocalContext.current
    // Coil loader with redirects disabled and a short timeout (SSRF hardening for the remote fetch).
    val imageLoader =
        remember {
            ImageLoader
                .Builder(context)
                .components {
                    add(
                        OkHttpNetworkFetcherFactory(
                            callFactory = {
                                OkHttpClient
                                    .Builder()
                                    .followRedirects(false)
                                    .followSslRedirects(false)
                                    .callTimeout(LOGO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                                    .build()
                            },
                        ),
                    )
                }.build()
        }
    val badgeColor = Color(BADGE_COLOR_MASK or (client.clientId.hashCode() and BADGE_RGB_MASK))
    val initials =
        (client.clientName ?: hostOf(client.redirectUris.firstOrNull()) ?: "?")
            .trim()
            .take(2)
            .uppercase()

    // Decorative: the client's name is read next to it
    Box(
        modifier =
            Modifier
                .size(AVATAR_SIZE_DP.dp)
                .clip(CircleShape)
                .background(badgeColor)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        // Initials sit behind; a successfully-loaded safe logo covers them.
        Text(text = initials, color = Color.White, textAlign = TextAlign.Center)
        if (LogoUrlPolicy.isSafeLogoUrl(client.logoUri)) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(client.logoUri).build(),
                imageLoader = imageLoader,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private fun hostOf(uri: String?): String? = uri?.let { runCatching { URI(it).host }.getOrNull() }

private const val AVATAR_SIZE_DP = 40
private const val LOGO_TIMEOUT_SECONDS = 5L
private const val BADGE_COLOR_MASK = 0xFF404040.toInt()
private const val BADGE_RGB_MASK = 0x00FFFFFF
