package com.buge.store

import android.app.Activity
import android.app.Application
import android.os.Bundle
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
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var resumedCount = 0
            override fun onActivityResumed(activity: Activity) {
                resumedCount += 1
            }

            override fun onActivityPaused(activity: Activity) {
                resumedCount -= 1
                if (resumedCount <= 0) InstallLogger.purge()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        runCatching { ShizukuProvider.enableMultiProcessSupport(false) }
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
