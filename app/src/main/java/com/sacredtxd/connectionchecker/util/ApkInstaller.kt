package com.sacredtxd.connectionchecker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface DownloadResult {
    data class Downloaded(val file: File) : DownloadResult
    data class Failed(val reason: String) : DownloadResult
}

/**
 * Downloads the published APK and hands it to the system installer. The app never
 * installs anything itself — it can only ask the package installer to, which then
 * shows the user its own confirmation screen.
 */
object ApkInstaller {

    private const val FILE_NAME = "update.apk"
    private const val BUFFER_BYTES = 64 * 1024

    /** Where downloads land: app-private, so no storage permission is involved. */
    private fun targetFile(context: Context): File =
        File(context.cacheDir, FILE_NAME)

    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                return@withContext DownloadResult.Failed("HTTP $status")
            }

            val total = connection.contentLength.toLong()
            val target = targetFile(context)
            // A partial file from an interrupted download must never be installed.
            val partial = File(target.parentFile, "$FILE_NAME.part")
            partial.delete()

            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var written = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }

            target.delete()
            if (!partial.renameTo(target)) {
                partial.delete()
                return@withContext DownloadResult.Failed("Could not finalise the download")
            }
            DownloadResult.Downloaded(target)
        } catch (e: IOException) {
            DownloadResult.Failed(e.message ?: "Download failed")
        } finally {
            connection?.disconnect()
        }
    }

    /** True when the system will let this app ask to install packages. */
    fun canRequestInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** Sends the user to the settings screen that grants the install permission. */
    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
