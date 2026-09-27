package com.buge.store.platform

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.io.File

class ShizukuInstallManager(private val context: Context) {

    private var binder: IInstallService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = IInstallService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
        }
    }

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShizukuInstallUserService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("install")
        .debuggable(false)
        .version(1)

    fun isAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun appContext(): Context = context

    fun hasPermission(): Boolean = runCatching {
        isAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun shouldShowRequestPermissionRationale(): Boolean = runCatching {
        isAvailable() && Shizuku.shouldShowRequestPermissionRationale()
    }.getOrDefault(false)

    fun requestPermission() {
        if (!isAvailable()) return
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    private fun ensureServiceBound() {
        if (!hasPermission() || binder != null) return
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
    }

    fun prepare() {
        ensureServiceBound()
    }

    fun install(file: File, installerPackageName: String?): Boolean {
        if (!file.exists() || file.extension.lowercase() != "apk") return false
        if (!hasPermission()) return false
        ensureServiceBound()
        val service = binder ?: return false
        return runCatching { service.install(file.absolutePath, installerPackageName) }.getOrDefault(false)
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 4210
    }
}

object BugeStoreInstallEvents {
    const val INSTALL_FINISHED = "com.buge.store.INSTALL_FINISHED"
    const val EXTRA_MESSAGE = "message"
}

class ShizukuInstallReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "com.buge.store.SHIZUKU_INSTALL_RESULT"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || intent.action != ACTION) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                context?.sendBroadcast(Intent(BugeStoreInstallEvents.INSTALL_FINISHED).setPackage(context.packageName))
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context?.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Shizuku installation failed."
                context?.sendBroadcast(
                    Intent(BugeStoreInstallEvents.INSTALL_FINISHED)
                        .setPackage(context.packageName)
                        .putExtra(BugeStoreInstallEvents.EXTRA_MESSAGE, message),
                )
            }
        }
    }
}