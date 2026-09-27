package com.buge.store.platform

import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ShizukuInstallUserService : IInstallService.Stub() {

    @Throws(RemoteException::class)
    override fun destroy() {
        Log.d(TAG, "destroy() called, exiting user service process")
        runCatching { System.exit(0) }
    }

    @Throws(RemoteException::class)
    override fun install(
        apk: ParcelFileDescriptor?,
        size: Long,
        installerPackageName: String?,
        callback: IInstallCallback?,
    ): Boolean {
        return installFromFd(apk, size, null, installerPackageName, callback)
    }

    @Throws(RemoteException::class)
    override fun installFromFd(
        apk: ParcelFileDescriptor?,
        size: Long,
        displayName: String?,
        installerPackageName: String?,
        callback: IInstallCallback?,
    ): Boolean {
        report(callback, "service", "installFromFd() entered. size=$size name=$displayName installer=$installerPackageName uid=${android.os.Process.myUid()}")
        val descriptor = apk
        if (descriptor == null) {
            report(callback, "service", "FAILED: apk ParcelFileDescriptor is null")
            return false
        }
        if (size <= 0L) {
            report(callback, "service", "FAILED: size <= 0")
            return false
        }
        val tempFile = buildTempFile(displayName)
        return runCatching {
            copyToTemp(descriptor, tempFile, size, callback)
            val copied = tempFile.length()
            report(callback, "service", "copied to ${tempFile.absolutePath} length=$copied expected=$size")
            if (copied != size) {
                report(callback, "service", "FAILED: copied size mismatch copied=$copied expected=$size")
                return@runCatching false
            }
            runPmInstall(tempFile, installerPackageName, callback)
        }.onFailure { report(callback, "service", "exception: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrDefault(false)
            .also {
                val removed = runCatching { tempFile.delete() }.getOrDefault(false)
                report(callback, "service", "temp cleanup removed=$removed path=${tempFile.absolutePath}")
            }
    }

    private fun buildTempFile(displayName: String?): File {
        val safeName = (displayName?.trim().orEmpty())
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "buge-install-${System.currentTimeMillis()}.apk" }
        return File(TEMP_DIR, "buge-${System.currentTimeMillis()}-$safeName")
    }

    private fun copyToTemp(descriptor: ParcelFileDescriptor, target: File, size: Long, callback: IInstallCallback?) {
        val dir = File(TEMP_DIR)
        if (!dir.exists()) runCatching { dir.mkdirs() }
        FileInputStream(descriptor.fileDescriptor).use { source ->
            FileOutputStream(target).use { sink ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var written = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    sink.write(buffer, 0, count)
                    written += count
                }
                sink.flush()
                report(callback, "service", "fd->temp bytesWritten=$written expected=$size")
            }
        }
    }

    private fun runPmInstall(target: File, installerPackageName: String?, callback: IInstallCallback?): Boolean {
        val command = buildPathCommand(target.absolutePath, installerPackageName)
        report(callback, "service", "command=${command.joinToString(" ")}")
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line -> output.appendLine(line) }
            }
        }
        reader.isDaemon = true
        reader.start()
        val exitCode = process.waitFor()
        reader.join(READER_TIMEOUT_MS)
        val text = output.toString()
        report(callback, "service", "exitCode=$exitCode")
        report(callback, "service", "rawOutput<<<\n$text>>>")
        val success = exitCode == 0 && !text.contains("Failure")
        report(callback, "service", "result=success=$success")
        return success
    }

    private fun buildPathCommand(path: String, installerPackageName: String?): List<String> {
        val command = mutableListOf("pm", "install", "-r")
        val installer = installerPackageName?.trim().orEmpty()
        if (installer.isNotEmpty()) {
            command += "-i"
            command += installer
        }
        command += path
        return command
    }

    private fun report(callback: IInstallCallback?, step: String, detail: String) {
        Log.d(TAG, "[$step] $detail")
        if (callback == null) return
        runCatching {
            callback.onStep(step, detail)
        }
    }

    private companion object {
        const val TAG = "BugeInstallSvc"
        const val READER_TIMEOUT_MS = 30_000L
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
        const val TEMP_DIR = "/data/local/tmp"
    }
}