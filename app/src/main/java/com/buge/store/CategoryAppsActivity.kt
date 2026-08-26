package com.buge.store

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.buge.store.ui.AppCollectionScreen
import com.buge.store.ui.StoreEvent
import com.buge.store.ui.StoreViewModel
import com.buge.store.ui.theme.BugeStoreTheme
import kotlinx.coroutines.flow.collectLatest

class CategoryAppsActivity : ComponentActivity() {
    private val viewModel: StoreViewModel by viewModels {
        val container = (application as BugeStoreApplication).container
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StoreViewModel(container.repository, container.preferences, container.platform) as T
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshInstallPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val category = intent.getStringExtra(EXTRA_CATEGORY_NAME).orEmpty()
        if (category.isBlank()) {
            finish()
            return
        }
        val platform = (application as BugeStoreApplication).container.platform
        setContent {
            val state by viewModel.state.collectAsState()
            val snackbar = remember { SnackbarHostState() }
            var permissionDialogVisible by remember { mutableStateOf(false) }
            val apps = state.apps.filter { category in it.categories }

            LaunchedEffect(viewModel) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        is StoreEvent.Message -> snackbar.showSnackbar(event.value)
                        StoreEvent.RequestInstallPermission -> permissionDialogVisible = true
                        is StoreEvent.ApplyLanguage -> Unit
                    }
                }
            }
            LaunchedEffect(state.requiresInstallPermission) {
                if (state.requiresInstallPermission) permissionDialogVisible = true
            }

            BugeStoreTheme(preferences = state.preferences) {
                if (permissionDialogVisible && state.requiresInstallPermission) {
                    AlertDialog(
                        onDismissRequest = { permissionDialogVisible = false },
                        title = { Text(stringResource(R.string.install_permission_title)) },
                        text = { Text(stringResource(R.string.install_permission_message)) },
                        confirmButton = {
                            TextButton(onClick = { permissionDialogVisible = false; platform.openUnknownSourcesSettings() }) {
                                Text(stringResource(R.string.open_settings))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { permissionDialogVisible = false }) { Text(stringResource(R.string.dismiss)) }
                        },
                    )
                }
                Box(Modifier.fillMaxSize()) {
                    if (state.apps.isEmpty() && state.isRefreshing) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    } else {
                        AppCollectionScreen(
                            title = category,
                            apps = apps,
                            state = state,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                            onBack = ::finish,
                            onOpenApp = { app ->
                                startActivity(Intent(this@CategoryAppsActivity, AppDetailActivity::class.java).putExtra(AppDetailActivity.EXTRA_PACKAGE_NAME, app.packageName))
                            },
                            onToggleFavourite = viewModel::toggleFavourite,
                            onAction = viewModel::download,
                            onOpen = viewModel::openApp,
                            onInstall = viewModel::install,
                            onCancelDownload = viewModel::cancelDownload,
                        )
                    }
                    SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }

    companion object {
        const val EXTRA_CATEGORY_NAME = "com.buge.store.extra.CATEGORY_NAME"
    }
}
