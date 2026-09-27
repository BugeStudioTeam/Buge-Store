package com.buge.store.data

import com.buge.store.platform.PackageAndDownloadManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class DownloadRepository(private val platform: PackageAndDownloadManager) {
    private val _downloads = MutableStateFlow<Map<Long, DownloadInfo>>(emptyMap())
    val downloads: StateFlow<Map<Long, DownloadInfo>> = _downloads.asStateFlow()

    private val jobs = ConcurrentHashMap<Long, Job>()
    private val ids = AtomicLong(System.currentTimeMillis())

    fun isActive(packageName: String): Boolean = _downloads.value.values.any { it.packageName == packageName && it.state.isActive() }

    fun enqueue(scope: CoroutineScope, app: StoreAppDto, onEvent: (DownloadEvent) -> Unit): Boolean {
        if (isActive(app.packageName)) return false
        val id = ids.incrementAndGet()
        _downloads.update { it + (id to DownloadInfo(id, app.packageName, app.name, app.latestVersion, DownloadState.QUEUED, 0L, 0L)) }
        val job = scope.launch {
            try {
                updateState(id, DownloadState.RUNNING)
                val file = platform.downloadApk(id, app) { downloaded, total -> updateProgress(id, downloaded, total) }
                updateState(id, DownloadState.SUCCESSFUL, localUri = file.absolutePath)
                onEvent(DownloadEvent.Ready(file))
            } catch (_: CancellationException) {
                updateState(id, DownloadState.CANCELLED)
            } catch (error: Exception) {
                updateState(id, DownloadState.FAILED, error = error.message ?: "Unable to download APK")
            } finally {
                jobs.remove(id)
            }
        }
        jobs[id] = job
        return true
    }

    fun cancel(download: DownloadInfo) {
        platform.cancel(download.id)
        jobs.remove(download.id)?.cancel()
        updateState(download.id, DownloadState.CANCELLED)
    }

    private fun updateState(id: Long, state: DownloadState, localUri: String? = null, error: String? = null) = _downloads.update { downloads ->
        downloads[id]?.let { current -> downloads + (id to current.copy(state = state, localUri = localUri ?: current.localUri, error = error)) } ?: downloads
    }

    private fun updateProgress(id: Long, downloaded: Long, total: Long) = _downloads.update { downloads ->
        downloads[id]?.let { current -> downloads + (id to current.copy(state = DownloadState.RUNNING, downloadedBytes = downloaded, totalBytes = total)) } ?: downloads
    }
}

sealed interface DownloadEvent {
    data class Ready(val file: File) : DownloadEvent
}
