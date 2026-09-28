package com.buge.store.platform

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.File

class ShizukuInstallManager(private val context: Context) {

    fun isAvailable(): Boolean = ShizukuShell.isAvailable()

    fun hasPermission(): Boolean = ShizukuShell.hasPermission()

    fun shouldShowRequestPermissionRationale(): Boolean = ShizukuShell.shouldShowRequestPermissionRationale()

    fun requestPermission() {
        InstallLogger.step("manager", "requestPermission: available=${isAvailable()}")
        if (!isAvailable()) return
        ShizukuShell.requestPermission(PERMISSION_REQUEST_CODE)
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    fun prepare() {
        InstallLogger.step(
            "manager",
            "prepare: available=${isAvailable()} permission=${hasPermission()}",
        )
    }

    private fun isInstalledPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    suspend fun install(file: File, installerPackageName: String?): Boolean {
        InstallLogger.divider("Shizuku install: ${file.name}")
        InstallLogger.step(
            "manager",
            "file=${file.absolutePath} exists=${file.exists()} ext=${file.extension} length=${file.length()}",
        )
        if (!file.exists() || file.extension.lowercase() != "apk") {
            InstallLogger.step("manager", "aborted: file missing or not an apk")
            return false
        }
        if (file.length() <= 0L) {
            InstallLogger.step("manager", "aborted: file size is zero")
            return false
        }
        if (!isAvailable()) {
            InstallLogger.step("manager", "aborted: Shizuku is not running")
            return false
        }
        if (!hasPermission()) {
            InstallLogger.step("manager", "aborted: no Shizuku permission")
            return false
        }

        val sourcePath = file.absolutePath
        val tempPath = "$TEMP_DIR/$TEMP_PREFIX${System.currentTimeMillis()}.apk"

        val copy = ShizukuShell.exec("cat \"$sourcePath\" > \"$tempPath\" && chmod 644 \"$tempPath\"")
        val sizeCheck = ShizukuShell.exec("stat -c %s \"$tempPath\"")
        InstallLogger.step(
            "manager",
            "copied to $tempPath sizeCheck=${sizeCheck.output.trim()} expected=${file.length()}",
        )
        if (!copy.success || sizeCheck.output.trim() != file.length().toString()) {
            InstallLogger.step("manager", "copy failed or size mismatch, staging via staging file")
            if (!stageToReadableLocation(file, tempPath)) {
                InstallLogger.step("manager", "aborted: staging failed")
                return false
            }
        }

        val installer = installerPackageName?.trim()?.ifBlank { null }?.takeIf { isInstalledPackage(it) }
        if (installer == null && !installerPackageName.isNullOrBlank()) {
            InstallLogger.step("manager", "installer '${installerPackageName.trim()}' not installed on device, falling back without -i")
        }

        var result = runInstall(tempPath, installer)
        if (!result.success && installer != null) {
            InstallLogger.step("manager", "install with -i failed, retrying without -i")
            result = runInstall(tempPath, null)
        }

        ShizukuShell.exec("rm -f \"$tempPath\"")

        InstallLogger.step("manager", "install result success=${result.success} error=${result.error}")
        return result.success
    }

    private suspend fun stageToReadableLocation(source: File, tempPath: String): Boolean {
        val stagingDir = context.getExternalFilesDir(null) ?: return false
        val stagingFile = File(stagingDir, "${TEMP_PREFIX}${System.currentTimeMillis()}.apk")
        return try {
            runCatching { source.inputStream().use { input -> stagingFile.outputStream().use { output -> input.copyTo(output) } } }
                .getOrElse {
                    InstallLogger.step("manager", "staging copy failed: ${it.message}")
                    return false
                }
            val stagedCopy = ShizukuShell.exec("cat \"${stagingFile.absolutePath}\" > \"$tempPath\" && chmod 644 \"$tempPath\"")
            val stagedSize = ShizukuShell.exec("stat -c %s \"$tempPath\"")
            InstallLogger.step(
                "manager",
                "staged copy success=${stagedCopy.success} sizeCheck=${stagedSize.output.trim()} expected=${source.length()}",
            )
            stagedCopy.success && stagedSize.output.trim() == source.length().toString()
        } finally {
            runCatching { stagingFile.delete() }
        }
    }

    private suspend fun runInstall(tempPath: String, installer: String?): ShizukuShell.Result {
        val command = buildString {
            append("pm install -r -d -t --user 0")
            if (installer != null) append(" -i \"$installer\"")
            append(" \"$tempPath\" 2>&1")
        }
        val result = ShizukuShell.exec(command)
        InstallLogger.step("manager", "command=$command success=${result.success}")
        if (!result.success) {
            InstallLogger.step("manager", "install raw<<<${result.output}${result.error}>>>")
        }
        return result
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 4210
        private const val TEMP_DIR = "/data/local/tmp"
        private const val TEMP_PREFIX = "buge-install-"
    }
}