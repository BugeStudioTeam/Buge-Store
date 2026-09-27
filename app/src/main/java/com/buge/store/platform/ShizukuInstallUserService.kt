package com.buge.store.platform

import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.util.Log
import java.io.FileInputStream

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
        report(callback, "service", "install() entered. size=$size installer=$installerPackageName uid=${android.os.Process.myUid()}")
        val descriptor = apk
        if (descriptor == null) {
            report(callback, "service", "FAILED: apk ParcelFileDescriptor is null")
            return false
        }
        if (size <= 0L) {
            report(callback, "service", "FAILED: size <= 0")
            return false
        }
        return runCatching {
            FileInputStream(descriptor.fileDescriptor).use { source ->
                val command = buildCommand(size, installerPackageName)
                report(callback, "service", "command=${command.joinToString(" ")}")
                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
                report(callback, "service", "process started, writing APK bytes to stdin")
                val output = StringBuilder()
                val reader = Thread {
                    runCatching {
                        process.inputStream.bufferedReader().forEachLine { line -> output.appendLine(line) }
                    }
                }
                reader.isDaemon = true
                reader.start()
                var written = 0L
                runCatching {
                    process.outputStream.use { sink ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            sink.write(buffer, 0, count)
                            written += count
                        }
                        sink.flush()
                    }
                }.onFailure { report(callback, "service", "stdin write failed: ${it.message}") }
                report(callback, "service", "stdin closed. bytesWritten=$written expected=$size")
                val exitCode = process.waitFor()
                reader.join(READER_TIMEOUT_MS)
                val text = output.toString()
                report(callback, "service", "exitCode=$exitCode")
                report(callback, "service", "rawOutput<<<\n$text>>>")
                val success = exitCode == 0 && !text.contains("Failure")
                report(callback, "service", "result=success=$success")
                success
            }
        }.onFailure { report(callback, "service", "exception: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrDefault(false)
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
    }
}