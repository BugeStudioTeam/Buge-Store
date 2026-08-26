package com.buge.store.data

import androidx.room.withTransaction
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.IOException

class StoreRepository(
    private val api: StoreApi,
    private val database: StoreDatabase,
) {
    private val dao = database.storeDao()

    val apps: Flow<List<StoreAppDto>> = dao.observeApps().map { entities -> entities.map { it.toDto() } }
    val favouritePackages: Flow<Set<String>> = dao.observeFavourites().map { values -> values.map { it.packageName }.toSet() }
    val downloadHistory: Flow<List<DownloadHistoryEntity>> = dao.observeDownloads()
    val appsWithFavourites: Flow<Pair<List<StoreAppDto>, Set<String>>> = combine(apps, favouritePackages) { appList, favourites -> appList to favourites }

    suspend fun refresh(force: Boolean = false): Result<RefreshSummary> {
        val cacheTimestamp = dao.metadata("last_refresh")?.value?.toLongOrNull() ?: 0L
        val age = System.currentTimeMillis() - cacheTimestamp
        if (!force && cacheTimestamp > 0L && age < CACHE_VALIDITY_MS) {
            return Result.success(RefreshSummary(fromCache = true, updatedAt = dao.metadata("api_updated_at")?.value.orEmpty()))
        }
        return runCatching {
            val payload = coroutineScope {
                val appsRequest = async { retryRequest { api.apps() } }
                val categoriesRequest = async { retryRequest { api.categories() } }
                val trendingRequest = async { retryRequest { api.trending() } }
                Triple(appsRequest.await(), categoriesRequest.await(), trendingRequest.await())
            }
            database.withTransaction {
                dao.clearApps()
                dao.insertApps(payload.first.apps.map { it.toEntity() })
                dao.putMetadata(MetadataEntity("last_refresh", System.currentTimeMillis().toString()))
                dao.putMetadata(MetadataEntity("api_updated_at", payload.first.lastUpdated))
                dao.putMetadata(MetadataEntity("categories", payload.second.categories.joinToString("|") { it.name }))
                dao.putMetadata(MetadataEntity("trending", payload.third.trending.joinToString("|") { it.packageName }))
            }
            RefreshSummary(fromCache = false, updatedAt = payload.first.lastUpdated)
        }
    }

    suspend fun categories(): List<String> = dao.metadata("categories")?.value
        ?.split("|")
        ?.filter { it.isNotBlank() }
        ?.sorted()
        .orEmpty()

    suspend fun trendingPackages(): List<String> = dao.metadata("trending")?.value
        ?.split("|")
        ?.filter { it.isNotBlank() }
        .orEmpty()

    suspend fun setFavourite(packageName: String, favourite: Boolean) {
        if (favourite) dao.favourite(FavouriteEntity(packageName)) else dao.unfavourite(packageName)
    }

    suspend fun recordDownload(entry: DownloadHistoryEntity) = dao.addDownload(entry)
    suspend fun cancelDownload(id: Long) = dao.markDownloadCancelled(id)
    suspend fun cachedApps(): List<StoreAppDto> = dao.getApps().map { it.toDto() }

    private suspend fun <T> retryRequest(block: suspend () -> T): T {
        var lastError: Throwable? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                return block()
            } catch (error: IOException) {
                lastError = error
            } catch (error: retrofit2.HttpException) {
                if (error.code() !in setOf(403, 429, 500, 502, 503, 504)) throw error
                lastError = error
            }
            delay(700L * (attempt + 1) * (attempt + 1))
        }
        throw lastError ?: IOException("Unable to refresh Buge Store")
    }

    private fun StoreAppDto.toEntity() = StoreAppEntity(
        packageName, name, icon, banner, categories.joinToString(SEPARATOR), latestVersion, downloadUrl,
        sizeMb, signature, minSdk, targetSdk, developer, changelog, shortDescription, description,
        featured, rating, ratingCount, website, sourceCode, releaseDate, downloads, architectures.joinToString(SEPARATOR),
    )

    private fun StoreAppEntity.toDto() = StoreAppDto(
        packageName, name, icon, banner, categories.split(SEPARATOR).filter(String::isNotBlank), latestVersion,
        downloadUrl, sizeMb, signature, minSdk, targetSdk, developer, changelog, shortDescription, description,
        featured, rating, ratingCount, website, sourceCode, releaseDate, downloads,
        architectures.split(SEPARATOR).filter(String::isNotBlank),
    )

    data class RefreshSummary(val fromCache: Boolean, val updatedAt: String)

    private companion object {
        const val CACHE_VALIDITY_MS = 30L * 60L * 1000L
        const val MAX_RETRIES = 3
        const val SEPARATOR = "\u001F"
    }
}
