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
            InstallLogger.step("manager", "onServiceConnected: binder=${service != null}")
            val stub = IInstallService.Stub.asInterface(service)
            binder = stub
            pendingBind?.complete(stub)
            pendingBind = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            InstallLogger.step("manager", "onServiceDisconnected")
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
        InstallLogger.step("manager", "requestPermission: available=${isAvailable()}")
        if (!isAvailable()) return
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
            .onFailure { InstallLogger.step("manager", "requestPermission failed: ${it.message}") }
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    private fun bindIfNeeded(): CompletableDeferred<IInstallService>? {
        binder?.let {
            InstallLogger.step("manager", "bindIfNeeded: reusing existing binder")
            return CompletableDeferred(it)
        }
        if (!hasPermission() || !isAvailable()) {
            InstallLogger.step("manager", "bindIfNeeded: aborted. available=${isAvailable()} permission=${hasPermission()}")
            return null
        }
        val deferred = pendingBind ?: CompletableDeferred<IInstallService>().also { pendingBind = it }
        InstallLogger.step("manager", "bindIfNeeded: calling Shizuku.bindUserService")
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
            .onFailure { InstallLogger.step("manager", "bindUserService threw: ${it.message}") }
        return deferred
    }

    fun prepare() {
        bindIfNeeded()
    }

    suspend fun install(file: File, installerPackageName: String?): Boolean {
        InstallLogger.divider("Shizuku install: ${file.name}")
        InstallLogger.step("manager", "file=${file.absolutePath} exists=${file.exists()} ext=${file.extension} length=${file.length()}")
        if (!file.exists() || file.extension.lowercase() != "apk") {
            InstallLogger.step("manager", "aborted: file missing or not an apk")
            return false
        }
        if (!hasPermission()) {
            InstallLogger.step("manager", "aborted: no Shizuku permission")
            return false
        }
        val deferred = bindIfNeeded() ?: return false
        val service = try {
            withTimeout(BIND_TIMEOUT_MS) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            InstallLogger.step("manager", "aborted: bind timeout after ${BIND_TIMEOUT_MS}ms")
            return false
        } catch (error: Exception) {
            InstallLogger.step("manager", "aborted: bind failed: ${error.message}")
            return false
        }
        val size = file.length()
        if (size <= 0L) {
            InstallLogger.step("manager", "aborted: file size is zero")
            return false
        }
        val descriptor = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.onFailure { InstallLogger.step("manager", "open fd failed: ${it.message}") }.getOrNull() ?: return false
        InstallLogger.step("manager", "opened fd, invoking AIDL install(size=$size, installer=${installerPackageName?.trim()?.ifBlank { null }})")
        val callback = object : IInstallCallback.Stub() {
            override fun onStep(step: String, detail: String) {
                InstallLogger.step("shell.$step", detail)
            }
        }
        return descriptor.use {
            runCatching { service.install(it, size, installerPackageName?.trim()?.ifBlank { null }, callback) }
                .onFailure { error -> InstallLogger.step("manager", "AIDL install threw: ${error.javaClass.simpleName}: ${error.message}") }
                .getOrDefault(false)
                .also { result -> InstallLogger.step("manager", "AIDL install returned $result") }
        }
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 4210
        private const val BIND_TIMEOUT_MS = 10_000L
    }
}