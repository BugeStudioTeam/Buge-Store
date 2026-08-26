package com.buge.store.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "store_apps")
data class StoreAppEntity(
    @PrimaryKey val packageName: String,
    val name: String,
    val icon: String,
    val banner: String?,
    val categories: String,
    val latestVersion: String,
    val downloadUrl: String,
    val sizeMb: Double,
    val signature: String,
    val minSdk: Int,
    val targetSdk: Int,
    val developer: String,
    val changelog: String?,
    val shortDescription: String?,
    val description: String?,
    val featured: Boolean,
    val rating: Double,
    val ratingCount: Int,
    val website: String?,
    val sourceCode: String?,
    val releaseDate: String?,
    val downloads: Int,
    val architectures: String,
)

@Entity(tableName = "store_metadata")
data class MetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "favourites")
data class FavouriteEntity(
    @PrimaryKey val packageName: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey val downloadId: Long,
    val packageName: String,
    val appName: String,
    val version: String,
    val createdAt: Long = System.currentTimeMillis(),
    val cancelled: Boolean = false,
)

@Dao
interface StoreDao {
    @Query("SELECT * FROM store_apps ORDER BY name COLLATE NOCASE")
    fun observeApps(): Flow<List<StoreAppEntity>>

    @Query("SELECT * FROM store_apps ORDER BY name COLLATE NOCASE")
    suspend fun getApps(): List<StoreAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<StoreAppEntity>)

    @Query("DELETE FROM store_apps")
    suspend fun clearApps()

    @Query("SELECT * FROM store_metadata WHERE `key` = :key LIMIT 1")
    suspend fun metadata(key: String): MetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMetadata(value: MetadataEntity)

    @Query("SELECT * FROM favourites ORDER BY createdAt DESC")
    fun observeFavourites(): Flow<List<FavouriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun favourite(entity: FavouriteEntity)

    @Query("DELETE FROM favourites WHERE packageName = :packageName")
    suspend fun unfavourite(packageName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDownload(entity: DownloadHistoryEntity)

    @Query("UPDATE download_history SET cancelled = 1 WHERE downloadId = :id")
    suspend fun markDownloadCancelled(id: Long)

    @Query("SELECT * FROM download_history ORDER BY createdAt DESC")
    fun observeDownloads(): Flow<List<DownloadHistoryEntity>>
}

@Database(
    entities = [StoreAppEntity::class, MetadataEntity::class, FavouriteEntity::class, DownloadHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class StoreDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
}
