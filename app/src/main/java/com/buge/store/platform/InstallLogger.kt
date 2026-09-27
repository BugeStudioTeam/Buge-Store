package com.buge.store.platform

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object InstallLogger {

    private const val TAG = "BugeInstall"
    private const val FILE_NAME = "buge-store-install.log"
    private const val MAX_ENTRY_COUNT = 2000

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "buge-install-logger").apply { isDaemon = true }
    }
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries = _entries.asStateFlow()

    private var logFile: File? = null

    fun init(context: Context) {
        if (logFile != null) return
        val directory = File(context.filesDir, "logs")
        if (!directory.exists()) directory.mkdirs()
        logFile = File(directory, FILE_NAME)
        append("logger", "Log session started. file=${logFile?.absolutePath}")
    }

    fun step(section: String, message: String) {
        append(section, message)
    }

    fun divider(title: String) {
        append("======", "===== $title =====")
    }

    fun snapshot(): List<String> = _entries.value

    fun readAll(): String {
        val file = logFile
        val fromFile = if (file != null && file.exists()) runCatching { file.readText() }.getOrDefault("") else ""
        val fromMemory = _entries.value.joinToString(separator = "\n")
        return if (fromMemory.length >= fromFile.length) fromMemory else fromFile
    }

    fun clear() {
        _entries.value = emptyList()
        val file = logFile
        executor.execute { runCatching { file?.writeText("") } }
    }

    private fun append(section: String, message: String) {
        val line = "${timestampFormat.format(Date())} [${section}] $message"
        Log.d(TAG, line)
        val updated = (_entries.value + line).takeLast(MAX_ENTRY_COUNT)
        _entries.value = updated
        val file = logFile
        executor.execute {
            runCatching {
                file?.appendText(line + "\n")
            }
        }
    }
}
