package com.buge.store.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.buge.store.R
import com.buge.store.data.StoreAppDto
import com.buge.store.ui.theme.BugeStoreTheme
import kotlinx.coroutines.flow.collectLatest

private enum class RootDestination(val route: String, val title: Int) {
    HOME("home", R.string.home),
    CATEGORIES("categories", R.string.categories),
    LIBRARY("library", R.string.library),
    SETTINGS("settings", R.string.settings),
}

@Composable
fun BugeStoreApp(
    viewModel: StoreViewModel,
    onApplyLanguage: (String) -> Unit,
    onOpenUnknownSources: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var permissionDialogDismissed by rememberSaveable { mutableStateOf(false) }
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route?.substringBefore("/")
    val useRail = LocalConfiguration.current.screenWidthDp >= 600

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is StoreEvent.Message -> snackbar.showSnackbar(event.value)
                StoreEvent.RequestInstallPermission -> permissionDialogDismissed = false
                is StoreEvent.ApplyLanguage -> onApplyLanguage(event.tag)
            }
        }
    }

    LaunchedEffect(state.requiresInstallPermission) {
        if (!state.requiresInstallPermission) permissionDialogDismissed = false
    }

    BugeStoreTheme(preferences = state.preferences) {
        if (state.requiresInstallPermission && !permissionDialogDismissed) {
            AlertDialog(
                onDismissRequest = { permissionDialogDismissed = true },
                title = { Text(stringResource(R.string.install_permission_title)) },
                text = { Text(stringResource(R.string.install_permission_message)) },
                confirmButton = {
                    TextButton(onClick = { permissionDialogDismissed = true; onOpenUnknownSources() }) {
                        Text(stringResource(R.string.open_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { permissionDialogDismissed = true }) { Text(stringResource(R.string.dismiss)) }
                },
            )
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets = WindowInsets.navigationBars,
            bottomBar = {
                if (!useRail && currentRoute != "featured") {
                    BugeNavigationBar(navController, currentRoute)
                }
            },
        ) { padding ->
            androidx.compose.foundation.layout.Row {
                if (useRail) BugeNavigationRail(navController, currentRoute)
                NavHost(
                    navController = navController,
                    startDestination = RootDestination.HOME.route,
                    modifier = androidx.compose.ui.Modifier.weight(1f),
                ) {
                    composable(RootDestination.HOME.route) {
                        HomeScreen(
                            state = state,
                            contentPadding = padding,
                            onSearchChange = viewModel::setSearch,
                            onOpenApp = { app -> onOpenAppDetails(app.packageName) },
                            onToggleFavourite = viewModel::toggleFavourite,
                            onAction = viewModel::download,
                            onOpen = viewModel::openApp,
                            onInstall = viewModel::install,
                            onCancelDownload = viewModel::cancelDownload,
                            onShowFeatured = { navController.navigate("featured") },
                            onRefresh = { viewModel.refresh(true) },
                        )
                    }
                    composable(RootDestination.CATEGORIES.route) {
                        CategoriesScreen(
                            state = state,
                            contentPadding = padding,
                            onOpenCategory = onOpenCategory,
                            onRefresh = { viewModel.refresh(true) },
                        )
                    }
                    composable(RootDestination.LIBRARY.route) {
                        LibraryScreen(
                            state = state,
                            contentPadding = padding,
                            onFilterChange = viewModel::setLibraryFilter,
                            onOpenApp = { app -> onOpenAppDetails(app.packageName) },
                            onToggleFavourite = viewModel::toggleFavourite,
                            onAction = viewModel::download,
                            onOpen = viewModel::openApp,
                            onInstall = viewModel::install,
                            onCancelDownload = viewModel::cancelDownload,
                        )
                    }
                    composable(RootDestination.SETTINGS.route) {
                        SettingsScreen(
                            state = state,
                            contentPadding = padding,
                            onThemeMode = viewModel::setThemeMode,
                            onColorMode = viewModel::setColorMode,
                            onContrastMode = viewModel::setContrastMode,
                            onReduceMotion = viewModel::setReduceMotion,
                            onLanguage = viewModel::setLanguage,
                        )
                    }
                    composable("featured") {
                        val featured = state.apps.filter { it.featured }
                        AppCollectionScreen(
                            title = stringResource(R.string.featured),
                            apps = featured,
                            contentPadding = padding,
                            onBack = { navController.popBackStack() },
                            onOpenApp = { app -> onOpenAppDetails(app.packageName) },
                            onToggleFavourite = viewModel::toggleFavourite,
                            onAction = viewModel::download,
                            onOpen = viewModel::openApp,
                            onInstall = viewModel::install,
                            onCancelDownload = viewModel::cancelDownload,
                            state = state,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BugeNavigationBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        RootDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { navController.navigateRoot(destination.route) },
                icon = { DestinationIcon(destination) },
                label = { Text(stringResource(destination.title)) },
            )
        }
    }
}

@Composable
private fun BugeNavigationRail(navController: NavHostController, currentRoute: String?) {
    NavigationRail {
        RootDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { navController.navigateRoot(destination.route) },
                icon = { DestinationIcon(destination) },
                label = { Text(stringResource(destination.title)) },
            )
        }
    }
}

@Composable
private fun DestinationIcon(destination: RootDestination) {
    when (destination) {
        RootDestination.HOME -> androidx.compose.material3.Icon(Icons.Default.Home, contentDescription = null)
        RootDestination.CATEGORIES -> androidx.compose.material3.Icon(Icons.Default.Category, contentDescription = null)
        RootDestination.LIBRARY -> androidx.compose.material3.Icon(Icons.Default.Update, contentDescription = null)
        RootDestination.SETTINGS -> androidx.compose.material3.Icon(Icons.Default.Settings, contentDescription = null)
    }
}

private fun NavHostController.navigateRoot(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

