package com.buge.store.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.buge.store.R
import com.buge.store.data.DownloadInfo
import com.buge.store.data.StoreAppDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    app: StoreAppDto,
    state: StoreUiState,
    onBack: () -> Unit,
    onToggleFavourite: (StoreAppDto) -> Unit,
    onAction: (StoreAppDto) -> Unit,
    onOpen: (String) -> Unit,
    onInstall: (DownloadInfo) -> Unit,
    onCancelDownload: (DownloadInfo) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val shareLabel = stringResource(R.string.share_app)
    val installState = state.installStates[app.packageName] ?: com.buge.store.data.AppInstallState()
    val download = state.downloadFor(app.packageName)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_details)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = { onToggleFavourite(app) }) {
                        Icon(if (app.packageName in state.favouritePackages) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(if (app.packageName in state.favouritePackages) R.string.remove_favourite else R.string.add_favourite))
                    }
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${app.name}\n${app.website ?: app.sourceCode ?: app.packageName}")
                        }
                        context.startActivity(Intent.createChooser(intent, shareLabel))
                    }) { Icon(Icons.Default.Share, stringResource(R.string.share_app)) }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = innerPadding.calculateTopPadding() + 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Box(Modifier.fillMaxWidth().height(220.dp)) {
                        AsyncImage(model = app.banner ?: app.icon, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.35f)
                        Row(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalAlignment = Alignment.Bottom) {
                            AsyncImage(model = app.icon, contentDescription = null, modifier = Modifier.size(82.dp).clip(MaterialTheme.shapes.large), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.app_by, app.developer), style = MaterialTheme.typography.bodyMedium)
                                Text(app.packageName, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            item {
                AppActionButton(app, installState, download, { onAction(app) }, { onOpen(app.packageName) }, { download?.let(onInstall) }, { download?.let(onCancelDownload) })
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.categories.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    if (installState.installedVersion != null) AssistChip(onClick = {}, label = { Text(stringResource(R.string.installed)) })
                }
            }
            item { DetailFacts(app, onCopy = { clipboard.setText(AnnotatedString(it)) }) }
            item {
                DetailSection(stringResource(R.string.about_this_app)) {
                    Text(app.description ?: app.shortDescription.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                }
            }
            app.changelog?.takeIf { it.isNotBlank() }?.let { changelog ->
                item { DetailSection(stringResource(R.string.whats_new)) { Text(changelog, style = MaterialTheme.typography.bodyMedium) } }
            }
            item {
                DetailSection(stringResource(R.string.about)) {
                    app.website?.let { url -> TextButton(onClick = { uriHandler.openUri(url) }) { Icon(Icons.Default.Language, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.website)) } }
                    app.sourceCode?.let { url -> TextButton(onClick = { uriHandler.openUri(url) }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.source_code)) } }
                }
            }
        }
    }
}

@Composable
private fun DetailFacts(app: StoreAppDto, onCopy: (String) -> Unit) {
    DetailSection(stringResource(R.string.app_details)) {
        Fact(stringResource(R.string.version), app.latestVersion)
        Fact(stringResource(R.string.size), "${app.sizeMb} MB")
        Fact(stringResource(R.string.minimum_android), "Android ${app.minSdk}+")
        Fact(stringResource(R.string.architectures), app.architectures.joinToString())
        Fact(stringResource(R.string.release_date), app.releaseDate.orEmpty())
        Fact(stringResource(R.string.package_name), app.packageName, onCopy)
        Fact(stringResource(R.string.signature), app.signature, onCopy)
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun Fact(label: String, value: String, onCopy: ((String) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        if (onCopy != null) IconButton(onClick = { onCopy(value) }) { Icon(Icons.Default.ContentCopy, stringResource(if (label == stringResource(R.string.package_name)) R.string.copy_package else R.string.copy_fingerprint)) }
    }
}
