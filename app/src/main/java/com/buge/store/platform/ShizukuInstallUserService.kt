package com.buge.store.platform

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.RemoteException
import java.io.File

class ShizukuInstallUserService : IInstallService.Stub {

    @Throws(RemoteException::class)
    override fun destroy() {
        runCatching { System.exit(0) }
    }

    @SuppressLint("PrivateApi")
    @Throws(RemoteException::class)
    override fun install(path: String, installerPackageName: String?): Boolean {
        val ctx = systemContext() ?: return false
        return runCatching {
            val file = File(path)
            if (!file.exists() || file.extension.lowercase() != "apk") return false
            val installer = ctx.packageManager.packageInstaller
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
                val intent = Intent(ctx, ShizukuInstallReceiver::class.java).setAction(ShizukuInstallReceiver.ACTION)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val pendingIntent = PendingIntent.getBroadcast(ctx, sessionId, intent, flags)
                session.commit(pendingIntent.intentSender)
            }
            true
        }.getOrDefault(false)
    }

    @SuppressLint("PrivateApi")
    private fun systemContext(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val systemMain = activityThread.getMethod("systemMain").invoke(null)
        val getSystemContext = activityThread.getMethod("getSystemContext")
        getSystemContext.invoke(systemMain) as Context
    }.getOrNull()

    companion object {
        private const val SESSION_NAME = "buge-store"
    }
}