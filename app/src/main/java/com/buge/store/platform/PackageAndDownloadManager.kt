package com.buge.store.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.buge.store.data.AppInstallState
import com.buge.store.data.StoreAppDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class PackageAndDownloadManager(private val context: Context) {
    private val packageManager = context.packageManager
    private val activeCalls = ConcurrentHashMap<Long, Call>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var pendingInstall: File? = null

    fun installState(app: StoreAppDto): AppInstallState {
        val installed = installedVersion(app.packageName)
        return AppInstallState(
            installedVersion = installed,
            canOpen = installed != null && packageManager.getLaunchIntentForPackage(app.packageName) != null,
            isUpdateAvailable = installed != null && isNewer(app.latestVersion, installed),
            isCompatible = isCompatible(app),
        )
    }

    fun isCompatible(app: StoreAppDto): Boolean {
        val sdkCompatible = Build.VERSION.SDK_INT >= app.minSdk
        val abiCompatible = app.architectures.isEmpty() || Build.SUPPORTED_ABIS.any { it in app.architectures }
        return sdkCompatible && abiCompatible
    }

    suspend fun downloadApk(
        id: Long,
        app: StoreAppDto,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val source = Uri.parse(app.downloadUrl)
        require(source.scheme == "https") { "Only HTTPS APK download URLs are permitted." }
        val request = Request.Builder().url(source.toString()).get().build()
        val call = client.newCall(request)
        activeCalls[id] = call
        val destination = File(requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)), safeFileName(app))
        val temporary = File(destination.parentFile, "${destination.name}.part")
        temporary.delete()
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("APK download failed: HTTP ${response.code}")
                val body = response.body ?: throw IOException("APK download response was empty")
                val contentLength = body.contentLength()
                body.byteStream().use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            onProgress(downloaded, contentLength)
                        }
                        output.flush()
                    }
                }
            }
            if (!temporary.renameTo(destination)) throw IOException("Unable to finalize APK download")
            destination
        } finally {
            activeCalls.remove(id)
            if (temporary.exists()) temporary.delete()
        }
    }

    fun cancel(id: Long) {
        activeCalls.remove(id)?.cancel()
    }

    fun open(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /**
     * Starts Android's package installer immediately after a successful OkHttp download.
     * Android still owns the final consent screen; there is no silent installation path.
     */
    fun requestInstall(file: File): Boolean {
        if (!file.exists() || file.extension.lowercase() != "apk") return false
        if (!canRequestPackageInstalls()) {
            pendingInstall = file
            return false
        }
        pendingInstall = null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
        return true
    }

    fun retryPendingInstall(): Boolean {
        val file = pendingInstall ?: return false
        return requestInstall(file)
    }

    fun canRequestPackageInstalls(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        packageManager.canRequestPackageInstalls()
    } else true

    fun openUnknownSourcesSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun installedVersion(packageName: String): String? = try {
        @Suppress("DEPRECATION")
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            packageManager.getPackageInfo(packageName, 0)
        }
        info.versionName
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun safeFileName(app: StoreAppDto): String = "${app.packageName}-${app.latestVersion}.apk"

    private fun isNewer(remote: String, local: String): Boolean {
        val remoteParts = remote.split(Regex("[^0-9]+"))
        val localParts = local.split(Regex("[^0-9]+"))
        val length = maxOf(remoteParts.size, localParts.size)
        for (index in 0 until length) {
            val remotePart = remoteParts.getOrNull(index)?.toIntOrNull() ?: 0
            val localPart = localParts.getOrNull(index)?.toIntOrNull() ?: 0
            if (remotePart != localPart) return remotePart > localPart
        }
        return remote > local
    }
}
