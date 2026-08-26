package com.buge.store.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buge.store.data.AppInstallState
import com.buge.store.data.ColorMode
import com.buge.store.data.ContrastMode
import com.buge.store.data.DownloadInfo
import com.buge.store.data.DownloadState
import com.buge.store.data.PreferencesRepository
import com.buge.store.data.StoreAppDto
import com.buge.store.data.StoreRepository
import com.buge.store.data.ThemeMode
import com.buge.store.data.UserPreferences
import com.buge.store.platform.PackageAndDownloadManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class StoreViewModel(
    private val repository: StoreRepository,
    private val preferencesRepository: PreferencesRepository,
    private val platform: PackageAndDownloadManager,
) : ViewModel() {
    private val _state = MutableStateFlow(StoreUiState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<StoreEvent>()
    val events = _events.asSharedFlow()

    private val activeDownloads = MutableStateFlow<Map<Long, DownloadInfo>>(emptyMap())
    private val downloadJobs = ConcurrentHashMap<Long, Job>()
    private val downloadIds = AtomicLong(System.currentTimeMillis())

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
            activeDownloads.collect { downloads ->
                _state.update { it.copy(downloads = downloads.values.sortedByDescending { item -> item.id }) }
            }
        }
        refreshInstallPermission()
        viewModelScope.launch { refresh(force = false) }
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
        val id = downloadIds.incrementAndGet()
        updateDownload(DownloadInfo(id, app.packageName, app.name, app.latestVersion, DownloadState.QUEUED, 0L, 0L))
        val job = viewModelScope.launch {
            try {
                updateDownloadState(id, DownloadState.RUNNING)
                val file = platform.downloadApk(id, app) { downloaded, total ->
                    updateDownloadProgress(id, downloaded, total)
                }
                updateDownloadState(id, DownloadState.SUCCESSFUL, localUri = file.absolutePath)
                requestAutomaticInstall(file)
            } catch (_: CancellationException) {
                updateDownloadState(id, DownloadState.CANCELLED)
            } catch (error: Exception) {
                updateDownloadState(id, DownloadState.FAILED, error = error.message ?: "Unable to download APK")
            } finally {
                downloadJobs.remove(id)
            }
        }
        downloadJobs[id] = job
    }

    fun cancelDownload(download: DownloadInfo) {
        platform.cancel(download.id)
        downloadJobs.remove(download.id)?.cancel()
        updateDownloadState(download.id, DownloadState.CANCELLED)
    }

    fun install(download: DownloadInfo) = viewModelScope.launch {
        val path = download.localUri ?: run {
            _events.emit(StoreEvent.Message("The downloaded APK is not available."))
            return@launch
        }
        if (!platform.requestInstall(File(path))) {
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

    private suspend fun requestAutomaticInstall(file: File) {
        if (!platform.requestInstall(file)) _events.emit(StoreEvent.RequestInstallPermission)
    }

    private fun updateDownload(item: DownloadInfo) = activeDownloads.update { it + (item.id to item) }

    private fun updateDownloadProgress(id: Long, downloaded: Long, total: Long) = activeDownloads.update { downloads ->
        downloads[id]?.let { current -> downloads + (id to current.copy(state = DownloadState.RUNNING, downloadedBytes = downloaded, totalBytes = total)) } ?: downloads
    }

    private fun updateDownloadState(id: Long, state: DownloadState, localUri: String? = null, error: String? = null) = activeDownloads.update { downloads ->
        downloads[id]?.let { current -> downloads + (id to current.copy(state = state, localUri = localUri ?: current.localUri, error = error)) } ?: downloads
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
