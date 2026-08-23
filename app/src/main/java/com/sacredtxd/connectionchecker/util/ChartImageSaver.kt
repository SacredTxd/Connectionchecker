package com.sacredtxd.connectionchecker.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a saved chart ended up, so the UI can say something concrete. */
sealed interface SaveResult {
    data class Saved(val displayPath: String) : SaveResult
    data class Failed(val reason: String) : SaveResult
}

/**
 * Writes the chart into the device's shared Pictures collection, so it shows up in
 * the gallery alongside screenshots.
 */
object ChartImageSaver {

    private const val ALBUM = "Connection Checker"
    private const val MIME_TYPE = "image/png"

    suspend fun save(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
    ): SaveResult = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, bitmap, fileName)
            } else {
                saveToPublicDirectory(bitmap, fileName)
            }
        }.getOrElse { error ->
            SaveResult.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
    ): SaveResult {
        val relativePath = "${Environment.DIRECTORY_PICTURES}/$ALBUM"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            // Marked pending until the bytes are written, so nothing reads a half file.
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return SaveResult.Failed("The gallery would not accept a new image")

        resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        } ?: run {
            resolver.delete(uri, null, null)
            return SaveResult.Failed("Could not open the image for writing")
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return SaveResult.Saved("$relativePath/$fileName")
    }

    private fun saveToPublicDirectory(bitmap: Bitmap, fileName: String): SaveResult {
        val album = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM,
        )
        if (!album.exists() && !album.mkdirs()) {
            return SaveResult.Failed("Could not create the $ALBUM album")
        }
        val target = File(album, fileName)
        FileOutputStream(target).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return SaveResult.Saved(target.absolutePath)
    }
}
