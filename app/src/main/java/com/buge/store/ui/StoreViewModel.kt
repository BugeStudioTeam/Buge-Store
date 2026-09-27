package com.buge.store.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.pm.PackageManager
import com.buge.store.data.AppInstallState
import com.buge.store.data.ColorMode
import com.buge.store.data.ContrastMode
import com.buge.store.data.DownloadEvent
import com.buge.store.data.DownloadInfo
import com.buge.store.data.DownloadRepository
import com.buge.store.data.PreferencesRepository
import com.buge.store.data.StoreAppDto
import com.buge.store.data.StoreRepository
import com.buge.store.data.ThemeMode
import com.buge.store.data.UserPreferences
import com.buge.store.platform.PackageAndDownloadManager
import com.buge.store.platform.ShizukuInstallManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.File

class StoreViewModel(
    private val repository: StoreRepository,
    private val preferencesRepository: PreferencesRepository,
    private val platform: PackageAndDownloadManager,
    private val downloadRepository: DownloadRepository,
    private val shizuku: ShizukuInstallManager,
) : ViewModel() {
    private val _state = MutableStateFlow(StoreUiState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<StoreEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.apps,
                repository.favouritePackages,
                preferencesRepository.preferences,
            ) { apps, favourites, preferences -> Snapshot(apps, favourites, preferences) }
                .collect { snapshot ->
                    _state.update {
                        it.copy(
                            apps = snapshot.apps,
                            favouritePackages = snapshot.favourites,
                            preferences = snapshot.preferences,
                            installStates = snapshot.apps.associate { app -> app.packageName to platform.installState(app) },
                        )
                    }
                }
        }
        viewModelScope.launch {
            downloadRepository.downloads.collect { downloads ->
                _state.update { it.copy(downloads = downloads.values.sortedByDescending { item -> item.id }) }
            }
        }
        refreshInstallPermission()
        shizuku.addPermissionListener(shizukuPermissionListener)
        viewModelScope.launch { refresh(force = false) }
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            viewModelScope.launch { _events.emit(StoreEvent.Message("Shizuku install permission granted.")) }
        } else {
            viewModelScope.launch { _events.emit(StoreEvent.Message("Shizuku install permission denied.")) }
        }
    }

    fun requestShizukuPermission() {
        if (!shizuku.isAvailable()) {
            viewModelScope.launch { _events.emit(StoreEvent.Message("Shizuku is not running on this device.")) }
            return
        }
        if (shizuku.hasPermission()) {
            viewModelScope.launch { _events.emit(StoreEvent.Message("Shizuku install permission is already granted.")) }
            return
        }
        shizuku.requestPermission()
    }

    override fun onCleared() {
        shizuku.removePermissionListener(shizukuPermissionListener)
        super.onCleared()
    }

    fun refresh(force: Boolean = true) = viewModelScope.launch {
        _state.update { it.copy(isRefreshing = true, refreshError = null) }
        repository.refresh(force)
            .onSuccess { summary ->
                _state.update {
                    it.copy(
                        lastUpdated = summary.updatedAt,
                        categories = repository.categories(),
                        trendingPackages = repository.trendingPackages(),
                    )
                }
            }
            .onFailure { error -> _state.update { it.copy(refreshError = error.message ?: "Unable to refresh catalogue") } }
        _state.update { it.copy(isRefreshing = false) }
    }

    fun setSearch(query: String) = _state.update { it.copy(searchQuery = query) }
    fun setCategory(category: String?) = _state.update { it.copy(selectedCategory = category) }
    fun setSort(sort: AppSort) = _state.update { it.copy(sort = sort) }
    fun setLibraryFilter(filter: LibraryFilter) = _state.update { it.copy(libraryFilter = filter) }

    fun toggleFavourite(app: StoreAppDto) = viewModelScope.launch {
        repository.setFavourite(app.packageName, app.packageName !in _state.value.favouritePackages)
    }

    fun download(app: StoreAppDto) {
        if (!platform.isCompatible(app)) {
            viewModelScope.launch { _events.emit(StoreEvent.Message("This app is not compatible with this device.")) }
            return
        }
        if (downloadRepository.isActive(app.packageName)) return
        if (!platform.hasNetwork()) {
            viewModelScope.launch { _events.emit(StoreEvent.Message("No network connection available.")) }
            return
        }
        if (_state.value.preferences.wifiOnlyDownloads && !platform.isOnWifi()) {
            viewModelScope.launch { _events.emit(StoreEvent.Message("Wi-Fi only downloads are enabled. Connect to Wi-Fi to download.")) }
            return
        }
        downloadRepository.enqueue(viewModelScope, app) { event ->
            when (event) {
                is DownloadEvent.Ready -> if (_state.value.preferences.autoInstall) {
                    viewModelScope.launch { requestAutomaticInstall(event.file) }
                }
            }
        }
    }

    fun cancelDownload(download: DownloadInfo) {
        downloadRepository.cancel(download)
    }

    fun install(download: DownloadInfo) = viewModelScope.launch {
        val path = download.localUri ?: run {
            _events.emit(StoreEvent.Message("The downloaded APK is not available."))
            return@launch
        }
        if (!requestInstall(File(path))) {
            _events.emit(StoreEvent.RequestInstallPermission)
        }
    }

    fun refreshInstallPermission() {
        val needsPermission = !platform.canRequestPackageInstalls()
        _state.update { it.copy(requiresInstallPermission = needsPermission) }
        if (!needsPermission) platform.retryPendingInstall()
    }

    fun openApp(packageName: String) = viewModelScope.launch {
        if (!platform.open(packageName)) _events.emit(StoreEvent.Message("This app cannot be opened from this device."))
    }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { preferencesRepository.setThemeMode(value) }
    fun setColorMode(value: ColorMode) = viewModelScope.launch { preferencesRepository.setColorMode(value) }
    fun setContrastMode(value: ContrastMode) = viewModelScope.launch { preferencesRepository.setContrastMode(value) }
    fun setReduceMotion(value: Boolean) = viewModelScope.launch { preferencesRepository.setReduceMotion(value) }
    fun setLanguage(value: String) = viewModelScope.launch { preferencesRepository.setLanguage(value); _events.emit(StoreEvent.ApplyLanguage(value)) }
    fun setAutoInstall(value: Boolean) = viewModelScope.launch { preferencesRepository.setAutoInstall(value) }
    fun setWifiOnlyDownloads(value: Boolean) = viewModelScope.launch { preferencesRepository.setWifiOnlyDownloads(value) }
    fun setInstallerPackageName(value: String) = viewModelScope.launch { preferencesRepository.setInstallerPackageName(value) }

    private suspend fun requestInstall(file: File): Boolean {
        if (shizuku.isAvailable() && shizuku.hasPermission()) {
            if (shizuku.install(file, _state.value.preferences.installerPackageName)) return true
        }
        return platform.requestInstall(file)
    }

    private suspend fun requestAutomaticInstall(file: File) {
        if (!requestInstall(file)) _events.emit(StoreEvent.RequestInstallPermission)
    }

    private data class Snapshot(
        val apps: List<StoreAppDto>,
        val favourites: Set<String>,
        val preferences: UserPreferences,
    )
}

data class StoreUiState(
    val apps: List<StoreAppDto> = emptyList(),
    val categories: List<String> = emptyList(),
    val trendingPackages: List<String> = emptyList(),
    val favouritePackages: Set<String> = emptySet(),
    val installStates: Map<String, AppInstallState> = emptyMap(),
    val downloads: List<DownloadInfo> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val sort: AppSort = AppSort.RELEVANCE,
    val libraryFilter: LibraryFilter = LibraryFilter.UPDATES,
    val isRefreshing: Boolean = false,
    val refreshError: String? = null,
    val lastUpdated: String = "",
    val requiresInstallPermission: Boolean = false,
)

enum class AppSort { RELEVANCE, NAME, NEWEST, SIZE }
enum class LibraryFilter { UPDATES, INSTALLED, FAVOURITES, DOWNLOADS }

sealed interface StoreEvent {
    data class Message(val value: String) : StoreEvent
    data object RequestInstallPermission : StoreEvent
    data class ApplyLanguage(val tag: String) : StoreEvent
}
