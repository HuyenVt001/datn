package com.example.snapget.core.util

import android.content.Context
import android.net.Uri
import java.io.File

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
