package com.buge.store.platform

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import rikka.shizuku.Shizuku
import java.io.File

class ShizukuInstallManager(private val context: Context) {

    companion object {
        const val PERMISSION_REQUEST_CODE = 4210
        private const val SESSION_NAME = "buge-store"
    }

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

    fun install(file: File, installerPackageName: String?): Boolean {
        if (!file.exists() || file.extension.lowercase() != "apk") return false
        if (!hasPermission()) return false
        return runCatching {
            val installer = context.packageManager.packageInstaller
            val sizeBytes = file.length()
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setSize(sizeBytes)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val normalizedInstaller = installerPackageName?.trim().orEmpty().ifBlank { null }
            if (normalizedInstaller != null) {
                params.setInstallerPackageName(normalizedInstaller)
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { source ->
                    session.openWrite(SESSION_NAME, 0, sizeBytes).use { destination ->
                        source.copyTo(destination)
                        session.fsync(destination)
                    }
                }
                val intent = Intent(context, ShizukuInstallReceiver::class.java).setAction(ShizukuInstallReceiver.ACTION)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pendingIntent.intentSender)
            }
            true
        }.getOrDefault(false)
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