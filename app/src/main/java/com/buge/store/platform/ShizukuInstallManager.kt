package com.buge.store.platform

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import rikka.shizuku.Shizuku
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class ShizukuInstallManager(private val context: Context) {

    private var binder: IInstallService? = null
    private var pendingBind: CompletableDeferred<IInstallService>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val stub = IInstallService.Stub.asInterface(service)
            binder = stub
            pendingBind?.complete(stub)
            pendingBind = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            pendingBind?.completeExceptionally(IllegalStateException("Shizuku user service disconnected."))
            pendingBind = null
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

    private fun bindIfNeeded(): CompletableDeferred<IInstallService>? {
        binder?.let { return CompletableDeferred(it) }
        if (!hasPermission() || !isAvailable()) return null
        val deferred = pendingBind ?: CompletableDeferred<IInstallService>().also { pendingBind = it }
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
        return deferred
    }

    fun prepare() {
        bindIfNeeded()
    }

    suspend fun install(file: File, installerPackageName: String?): Boolean {
        if (!file.exists() || file.extension.lowercase() != "apk") return false
        if (!hasPermission()) return false
        val deferred = bindIfNeeded() ?: return false
        val service = try {
            withTimeout(BIND_TIMEOUT_MS) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            return false
        } catch (_: Exception) {
            return false
        }
        val size = file.length()
        if (size <= 0L) return false
        val descriptor = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull() ?: return false
        return descriptor.use {
            runCatching { service.install(it, size, installerPackageName?.trim()?.ifBlank { null }) }.getOrDefault(false)
        }
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 4210
        private const val BIND_TIMEOUT_MS = 10_000L
    }
}