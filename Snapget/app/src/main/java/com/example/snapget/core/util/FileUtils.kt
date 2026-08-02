package com.example.snapget.core.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copy noi dung tu content Uri (thu vien anh...) vao cacheDir de upload multipart.
 * [prefix] phan biet nguon (chat_/avatar_...) cho de don dep. Loi -> null.
 */
fun copyUriToCacheFile(context: Context, uri: Uri, prefix: String = "upload"): File? = try {
    val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }?.let { file }
} catch (_: Exception) {
    null
}

/**
 * Tai file tu URL (https Cloudinary...) ve cacheDir — vd anh ghep coop de dua vao
 * luong edit -> dang bai (EditMediaScreen chi nhan duong dan file local). Loi -> null.
 */
suspend fun downloadToCacheFile(context: Context, url: String, prefix: String = "download"): File? = withContext(Dispatchers.IO) {
    try {
        val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        URL(url).openStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file
    } catch (_: Exception) {
        null
    }
}
