package com.buge.store

import android.app.Application
import androidx.room.Room
import com.buge.store.data.DownloadRepository
import com.buge.store.data.PreferencesRepository
import com.buge.store.data.StoreApiFactory
import com.buge.store.data.StoreDatabase
import com.buge.store.data.StoreRepository
import com.buge.store.platform.InstallLogger
import com.buge.store.platform.PackageAndDownloadManager
import com.buge.store.platform.ShizukuInstallManager
import rikka.shizuku.ShizukuProvider

class BugeStoreApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        InstallLogger.init(this)
        InstallLogger.step("app", "Application.onCreate pid=${android.os.Process.myPid()}")
        runCatching { ShizukuProvider.enableMultiProcessSupport(true) }
    }

    val container: AppContainer by lazy {
        val database = Room.databaseBuilder(this, StoreDatabase::class.java, "buge-store.db")
            .fallbackToDestructiveMigration()
            .build()
        val platform = PackageAndDownloadManager(this)
        AppContainer(
            repository = StoreRepository(StoreApiFactory.create(), database),
            preferences = PreferencesRepository(this),
            platform = platform,
            downloads = DownloadRepository(platform),
            shizuku = ShizukuInstallManager(this),
        )
    }
}

data class AppContainer(
    val repository: StoreRepository,
    val preferences: PreferencesRepository,
    val platform: PackageAndDownloadManager,
    val downloads: DownloadRepository,
    val shizuku: ShizukuInstallManager,
)
