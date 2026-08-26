package com.buge.store.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.buge.store.R
import com.buge.store.data.AppInstallState
import com.buge.store.data.DownloadInfo
import com.buge.store.data.DownloadState
import com.buge.store.data.StoreAppDto
import kotlin.math.roundToInt

@Composable
fun StoreSearchHeader(
    query: String,
    isRefreshing: Boolean,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotBlank()) {
                { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, stringResource(R.string.clear_search)) } }
            } else null,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            label = { Text(stringResource(R.string.search_apps)) },
            shape = RoundedCornerShape(28.dp),
        )
        FilledIconButton(onClick = onRefresh, enabled = !isRefreshing, modifier = Modifier.size(48.dp)) {
            if (isRefreshing) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            else Icon(Icons.Default.Refresh, stringResource(R.string.refresh))
        }
    }
}

@Composable
fun StoreAppCard(
    app: StoreAppDto,
    installState: AppInstallState,
    isFavourite: Boolean,
    download: DownloadInfo?,
    onOpenApp: () -> Unit,
    onToggleFavourite: () -> Unit,
    onAction: () -> Unit,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpenApp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = app.icon,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp).clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.developer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(app.packageName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onToggleFavourite) {
                    Icon(
                        if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(if (isFavourite) R.string.remove_favourite else R.string.add_favourite),
                        tint = if (isFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            app.shortDescription?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                app.categories.take(2).forEach { category ->
                    AssistChip(onClick = {}, label = { Text(category) })
                }
                if (app.rating > 0.0) {
                    AssistChip(onClick = {}, label = { Text("${app.rating}") }, leadingIcon = { Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp)) })
                }
            }
            Text(stringResource(R.string.app_meta, app.latestVersion, app.sizeMb, app.minSdk), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppActionButton(
                app = app,
                installState = installState,
                download = download,
                onAction = onAction,
                onOpen = onOpen,
                onInstall = onInstall,
                onCancel = onCancelDownload,
            )
        }
    }
}

@Composable
fun AppActionButton(
    app: StoreAppDto,
    installState: AppInstallState,
    download: DownloadInfo?,
    onAction: () -> Unit,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(targetState = download?.state to installState, label = "appAction") { (state, installed) ->
        when {
            !installed.isCompatible -> OutlinedButton(onClick = {}, enabled = false, modifier = modifier.fillMaxWidth()) { Text(stringResource(R.string.incompatible)) }
            state in setOf(DownloadState.QUEUED, DownloadState.RUNNING, DownloadState.PAUSED) && download != null -> {
                Column(modifier.fillMaxWidth()) {
                    val progress = if (download.totalBytes > 0) download.downloadedBytes.toFloat() / download.totalBytes else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.downloading))
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
            state == DownloadState.SUCCESSFUL && download != null -> Button(onClick = onInstall, modifier = modifier.fillMaxWidth()) { Text(stringResource(R.string.install)) }
            installed.isUpdateAvailable -> Button(onClick = onAction, modifier = modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.update)) }
            installed.canOpen -> FilledTonalButton(onClick = onOpen, modifier = modifier.fillMaxWidth()) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.open)) }
            else -> Button(onClick = onAction, modifier = modifier.fillMaxWidth()) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.download)) }
        }
    }
}

@Composable
fun SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun CatalogueStatus(error: String?, isRefreshing: Boolean, onRetry: () -> Unit) {
    when {
        isRefreshing -> Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        error != null -> Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.catalogue_error), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

fun StoreUiState.downloadFor(packageName: String): DownloadInfo? = downloads
    .filter { it.packageName == packageName }
    .maxByOrNull { it.id }

fun DownloadInfo.progressPercent(): Int = if (totalBytes > 0) ((downloadedBytes.toDouble() / totalBytes) * 100).roundToInt() else 0
