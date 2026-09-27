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
                val command = buildCommand(size, installerPackageName)
                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                process.outputStream.use { sink -> source.copyTo(sink) }
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
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
}