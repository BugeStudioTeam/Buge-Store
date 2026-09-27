package com.buge.store.platform

import android.os.ParcelFileDescriptor
import android.os.RemoteException
import java.io.FileInputStream

class ShizukuInstallUserService : IInstallService.Stub() {

    @Throws(RemoteException::class)
    override fun destroy() {
        runCatching { System.exit(0) }
    }

    @Throws(RemoteException::class)
    override fun install(apk: ParcelFileDescriptor?, size: Long, installerPackageName: String?): Boolean {
        val descriptor = apk ?: return false
        if (size <= 0L) return false
        return runCatching {
            FileInputStream(descriptor.fileDescriptor).use { source ->
                val process = ProcessBuilder(buildCommand(size, installerPackageName))
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
                runCatching { process.outputStream.use { sink -> source.copyTo(sink) } }
                val exitCode = process.waitFor()
                reader.join(READER_TIMEOUT_MS)
                exitCode == 0 && !output.contains("Failure")
            }
        }.getOrDefault(false)
    }

    private fun buildCommand(size: Long, installerPackageName: String?): List<String> {
        val command = mutableListOf("pm", "install", "-S", size.toString())
        val installer = installerPackageName?.trim().orEmpty()
        if (installer.isNotEmpty()) {
            command += "-i"
            command += installer
        }
        return command
    }

    private companion object {
        const val READER_TIMEOUT_MS = 30_000L
    }
}