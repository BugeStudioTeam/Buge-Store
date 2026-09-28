package com.buge.store.platform

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShizukuShell {

    data class Result(val success: Boolean, val output: String, val error: String)

    @Volatile
    private var newProcessMethod: Method? = null

    fun isAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission(requestCode: Int) {
        runCatching {
            if (!Shizuku.isPreV11()) Shizuku.requestPermission(requestCode)
        }.onFailure { InstallLogger.step("shell", "requestPermission failed: ${it.message}") }
    }

    fun shouldShowRequestPermissionRationale(): Boolean = runCatching {
        !Shizuku.isPreV11() && Shizuku.shouldShowRequestPermissionRationale()
    }.getOrDefault(false)

    private fun resolveNewProcess(): Method? {
        newProcessMethod?.let { return it }
        return runCatching {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
        }.onFailure {
            InstallLogger.step("shell", "newProcess reflection failed: ${it.javaClass.simpleName}: ${it.message}")
        }.getOrNull()?.also { newProcessMethod = it }
    }

    @SuppressLint("PrivateApi")
    suspend fun exec(command: String): Result = withContext(Dispatchers.IO) {
        execArgv(arrayOf("sh", "-c", command))
    }

    suspend fun execArgv(argv: Array<String>): Result = withContext(Dispatchers.IO) {
        InstallLogger.step("shell", "exec argv=${argv.joinToString(" ")}")
        if (!isAvailable()) {
            InstallLogger.step("shell", "aborted: Shizuku is not running")
            return@withContext Result(false, "", "Shizuku is not running")
        }
        if (!hasPermission()) {
            InstallLogger.step("shell", "aborted: Shizuku permission not granted")
            return@withContext Result(false, "", "Shizuku permission not granted")
        }
        val method = resolveNewProcess()
            ?: return@withContext Result(false, "", "Shizuku.newProcess unavailable")
        try {
            val process = method.invoke(null, argv, null, null) as Process
            val stdout = readStream(process.inputStream)
            val stderr = readStream(process.errorStream)
            val exitCode = process.waitFor()
            InstallLogger.step("shell", "exitCode=$exitCode rawOutput<<<${(stdout + stderr).trim()}>>>")
            if (exitCode == 0) {
                Result(true, stdout.trim(), stderr.trim())
            } else {
                Result(false, stdout.trim(), stderr.trim().ifEmpty { "Exit code: $exitCode" })
            }
        } catch (error: Exception) {
            InstallLogger.step("shell", "exec threw: ${error.javaClass.simpleName}: ${error.message}")
            Result(false, "", "Exception: ${error.message ?: "unknown"}")
        }
    }

    private fun readStream(stream: java.io.InputStream): String =
        runCatching { BufferedReader(InputStreamReader(stream)).use { it.readText() } }.getOrDefault("")
}
