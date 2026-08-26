package com.buge.store.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.buge.store.data.StoreAppDto

@Composable
fun HomeScreen(
    state: StoreUiState,
    contentPadding: PaddingValues,
    onSearchChange: (String) -> Unit,
    onOpenApp: (StoreAppDto) -> Unit,
    onToggleFavourite: (StoreAppDto) -> Unit,
    onAction: (StoreAppDto) -> Unit,
    onOpen: (String) -> Unit,
    onInstall: (com.buge.store.data.DownloadInfo) -> Unit,
    onCancelDownload: (com.buge.store.data.DownloadInfo) -> Unit,
    onShowFeatured: () -> Unit,
    onRefresh: () -> Unit,
) {
    val searched = state.filteredApps()
    val featured = state.apps.filter { it.featured }
    val trending = state.trendingPackages.mapNotNull { id -> state.apps.firstOrNull { it.packageName == id } }.ifEmpty { state.apps.take(5) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 12.dp, bottom = contentPadding.calculateBottomPadding() + 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { StoreSearchHeader(state.searchQuery, state.isRefreshing, onSearchChange, onRefresh) }
        item { CatalogueStatus(state.refreshError, state.isRefreshing && state.apps.isEmpty(), onRefresh) }
        if (state.searchQuery.isBlank()) {
            item { SectionHeader(stringResource(R.string.featured), stringResource(R.string.show_all), onShowFeatured) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(featured.take(3), key = { it.packageName }) { app ->
                        FeaturedHeroCard(app = app, onClick = { onOpenApp(app) })
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.trending)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(trending, key = { it.packageName }) { app ->
                        TrendingCard(app = app, onClick = { onOpenApp(app) })
                    }
                }
            }
        }
        item { SectionHeader(if (state.searchQuery.isBlank()) stringResource(R.string.all_apps) else stringResource(R.string.search_apps)) }
        if (searched.isEmpty() && !state.isRefreshing) {
            item { EmptyApps() }
        } else {
            items(searched, key = { it.packageName }) { app ->
                StoreAppCard(
                    app = app,
                    installState = state.installStates[app.packageName] ?: com.buge.store.data.AppInstallState(),
                    isFavourite = app.packageName in state.favouritePackages,
                    download = state.downloadFor(app.packageName),
                    onOpenApp = { onOpenApp(app) },
                    onToggleFavourite = { onToggleFavourite(app) },
                    onAction = { onAction(app) },
                    onOpen = { onOpen(app.packageName) },
                    onInstall = { state.downloadFor(app.packageName)?.let(onInstall) },
                    onCancelDownload = { state.downloadFor(app.packageName)?.let(onCancelDownload) },
                )
            }
        }
    }
}

@Composable
private fun FeaturedHeroCard(app: StoreAppDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(310.dp).height(230.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = app.banner ?: app.icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.45f,
            )
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AssistChip(onClick = {}, label = { Text(app.developer) }, leadingIcon = { Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp)) })
                Text(app.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(app.shortDescription.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TrendingCard(app: StoreAppDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(180.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AsyncImage(model = app.icon, contentDescription = null, modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.medium))
            Text(app.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.developer, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    state: StoreUiState,
    contentPadding: PaddingValues,
    onOpenCategory: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val categories = state.categories.ifEmpty { state.apps.flatMap { it.categories }.distinct().sorted() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories)) },
                actions = { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) } },
            )
        },
    ) { innerPadding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding() + innerPadding.calculateTopPadding()),
        ) {
            CatalogueStatus(state.refreshError, state.isRefreshing && categories.isEmpty(), onRefresh)
            if (categories.isEmpty() && !state.isRefreshing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyApps() }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(156.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(categories, key = { it }) { category ->
                        CategoryCard(
                            name = category,
                            count = state.apps.count { category in it.categories },
                            onClick = { onOpenCategory(category) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(name: String, count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(148.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.feature_count, count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCollectionScreen(
    title: String,
    apps: List<StoreAppDto>,
    state: StoreUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenApp: (StoreAppDto) -> Unit,
    onToggleFavourite: (StoreAppDto) -> Unit,
    onAction: (StoreAppDto) -> Unit,
    onOpen: (String) -> Unit,
    onInstall: (com.buge.store.data.DownloadInfo) -> Unit,
    onCancelDownload: (com.buge.store.data.DownloadInfo) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) } }) }) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + innerPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                StoreAppCard(app, state.installStates[app.packageName] ?: com.buge.store.data.AppInstallState(), app.packageName in state.favouritePackages, state.downloadFor(app.packageName), { onOpenApp(app) }, { onToggleFavourite(app) }, { onAction(app) }, { onOpen(app.packageName) }, { state.downloadFor(app.packageName)?.let(onInstall) }, { state.downloadFor(app.packageName)?.let(onCancelDownload) })
            }
        }
    }
}

@Composable
private fun EmptyApps() {
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(32.dp)) {
        Text(stringResource(R.string.no_apps), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.try_different_search), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun StoreUiState.filteredApps(): List<StoreAppDto> {
    val query = searchQuery.trim().lowercase()
    return apps.asSequence()
        .filter { selectedCategory == null || selectedCategory in it.categories }
        .filter { query.isBlank() || listOf(it.name, it.packageName, it.developer, it.shortDescription.orEmpty(), it.description.orEmpty()).any { value -> value.lowercase().contains(query) } }
        .sortedWith(
            when (sort) {
                AppSort.RELEVANCE -> compareByDescending<StoreAppDto> { it.featured }.thenBy { it.name }
                AppSort.NAME -> compareBy { it.name.lowercase() }
                AppSort.NEWEST -> compareByDescending { it.releaseDate.orEmpty() }
                AppSort.SIZE -> compareByDescending { it.sizeMb }
            },
        ).toList()
}
