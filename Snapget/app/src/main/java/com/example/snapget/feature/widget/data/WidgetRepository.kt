package com.example.snapget.feature.widget.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.snapget.core.network.api.MomentApi
import com.example.snapget.core.network.api.UserApi
import com.example.snapget.core.network.dto.MomentDto
import com.example.snapget.core.network.unwrap
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Data layer cua widget — NOI DUY NHAT cham mang cho feature nay:
 * lay streak + moment PHOTO moi nhat tu feed, tai anh ve file local.
 */
@Singleton
class WidgetRepository @Inject constructor(
    private val momentApi: MomentApi,
    private val userApi: UserApi,
    private val okHttpClient: OkHttpClient,
) {

    /**
     * Streak ca nhan + moment PHOTO moi nhat trong trang dau cua feed.
     * Video bo qua (widget chi hien anh; thumbnail video la polish sau).
     */
    suspend fun fetchLatest(): Pair<Int, MomentDto?> {
        val streak = userApi.getMe().unwrap().personalStreak
        val latestPhoto = momentApi.getFeed(page = 1, limit = FEED_SCAN_LIMIT)
            .unwrap()
            .items
            .firstOrNull { it.contentType == "PHOTO" }
        return streak to latestPhoto
    }

    /**
     * Tai anh ve [dest]: downsample canh dai <= [MAX_EDGE_PX] px roi nen JPEG
     * (RemoteViews co tran bitmap ~vai MB), ghi atomic qua file tmp + rename.
     */
    fun downloadPhoto(url: String, dest: File) {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed: HTTP ${response.code}")
            }
            val bytes = response.body?.bytes() ?: throw IOException("Empty body")

            // Doc bounds truoc de tinh inSampleSize (khong load full anh vao RAM)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_EDGE_PX) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: throw IOException("Cannot decode image")

            val tmp = File(dest.parentFile, "${dest.name}.tmp")
            tmp.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            bitmap.recycle()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        }
    }

    companion object {
        /** Quet toi da N bai dau feed de tim anh (phong khi dau feed toan video). */
        private const val FEED_SCAN_LIMIT = 10

        /** Canh dai toi da cua anh widget (RemoteViews co bitmap cap). */
        private const val MAX_EDGE_PX = 800

        private const val JPEG_QUALITY = 85
    }
}
