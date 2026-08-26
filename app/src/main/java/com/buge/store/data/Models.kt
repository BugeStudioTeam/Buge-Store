package com.buge.store.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppsResponse(
    val version: Int = 1,
    @SerialName("last_updated") val lastUpdated: String = "",
    @SerialName("total_apps") val totalApps: Int = 0,
    val apps: List<StoreAppDto> = emptyList(),
)

@Serializable
data class StoreAppDto(
    @SerialName("package") val packageName: String,
    val name: String,
    val icon: String,
    val banner: String? = null,
    val categories: List<String> = emptyList(),
    @SerialName("latest_version") val latestVersion: String,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("size_mb") val sizeMb: Double = 0.0,
    val signature: String = "",
    @SerialName("min_sdk") val minSdk: Int = 1,
    @SerialName("target_sdk") val targetSdk: Int = 1,
    val developer: String = "",
    val changelog: String? = null,
    @SerialName("short_description") val shortDescription: String? = null,
    val description: String? = null,
    val featured: Boolean = false,
    val rating: Double = 0.0,
    @SerialName("rating_count") val ratingCount: Int = 0,
    val website: String? = null,
    @SerialName("source_code") val sourceCode: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val downloads: Int = 0,
    val architectures: List<String> = emptyList(),
)

@Serializable
data class CategoriesResponse(val categories: List<CategoryDto> = emptyList())

@Serializable
data class CategoryDto(
    val name: String,
    val count: Int,
    val apps: List<String> = emptyList(),
)

@Serializable
data class TrendingResponse(
    @SerialName("last_updated") val lastUpdated: String = "",
    val trending: List<TrendingDto> = emptyList(),
)

@Serializable
data class TrendingDto(
    @SerialName("package") val packageName: String,
    val name: String,
    val downloads: Int = 0,
    val rating: Double = 0.0,
    @SerialName("trend_score") val trendScore: Double = 0.0,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ColorMode { DYNAMIC, BUGE_BLUE, TEAL, VIOLET, CORAL, EVERGREEN }
enum class ContrastMode { STANDARD, MEDIUM, HIGH }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorMode: ColorMode = ColorMode.DYNAMIC,
    val contrastMode: ContrastMode = ContrastMode.STANDARD,
    val reduceMotion: Boolean = false,
    val selectedLanguage: String = "",
)

data class AppInstallState(
    val installedVersion: String? = null,
    val canOpen: Boolean = false,
    val isUpdateAvailable: Boolean = false,
    val isCompatible: Boolean = true,
)

enum class DownloadState { QUEUED, RUNNING, PAUSED, SUCCESSFUL, FAILED, CANCELLED, UNKNOWN }

data class DownloadInfo(
    val id: Long,
    val packageName: String,
    val appName: String,
    val version: String,
    val state: DownloadState,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val localUri: String? = null,
    val error: String? = null,
)
