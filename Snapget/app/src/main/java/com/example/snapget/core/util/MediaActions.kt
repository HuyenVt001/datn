package com.example.snapget.core.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Share / Download media cua post (menu ⋯ tren feed — khop anh mau 2026-07-26).
 * Chi thao tac voi URL Cloudinary do server tra ve, khong goi API server.
 */
object MediaActions {

    /**
     * Tai file ve cacheDir roi mo share sheet he thong — share ANH/VIDEO that
     * (qua FileProvider), khong phai URL text. Nem exception neu tai that bai
     * (caller bat va toast).
     */
    suspend fun share(context: Context, url: String, isVideo: Boolean) {
        val file = withContext(Dispatchers.IO) {
            val ext = if (isVideo) "mp4" else "jpg"
            val target = File(context.cacheDir, "share_${System.currentTimeMillis()}.$ext")
            URL(url).openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/mp4" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share moment"))
    }

    /**
     * Luu media vao thu vien may (MediaStore, thu muc Pictures/Snapget).
     * Android <10 can WRITE_EXTERNAL_STORAGE (da khai bao maxSdk 28) — thiet bi
     * demo Android 10+ khong can quyen. Tra ve true neu luu thanh cong.
     */
    suspend fun saveToGallery(context: Context, url: String, isVideo: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val name = "snapget_${System.currentTimeMillis()}" + if (isVideo) ".mp4" else ".jpg"
            val collection = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Snapget",
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val itemUri = resolver.insert(collection, values) ?: return@withContext false
            resolver.openOutputStream(itemUri)?.use { output ->
                URL(url).openStream().use { input -> input.copyTo(output) }
            } ?: return@withContext false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
