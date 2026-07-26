package com.example.snapget.feature.widget.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Luu snapshot widget vao SharedPreferences rieng ("snapget_widget") + file anh
 * trong filesDir (KHONG dung cacheDir vi he thong co the xoa cache bat ky luc nao
 * ma widget phai render duoc ca khi app khong chay).
 */
@Singleton
class WidgetSnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): WidgetSnapshot {
        val kind = runCatching {
            WidgetStateKind.valueOf(prefs.getString(KEY_STATE, WidgetStateKind.SIGNED_OUT.name)!!)
        }.getOrDefault(WidgetStateKind.SIGNED_OUT)
        return WidgetSnapshot(
            kind = kind,
            streak = prefs.getInt(KEY_STREAK, 0),
            momentId = prefs.getString(KEY_MOMENT_ID, null),
            imagePath = prefs.getString(KEY_IMAGE_PATH, null),
            updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L),
        )
    }

    fun save(snapshot: WidgetSnapshot) {
        prefs.edit()
            .putString(KEY_STATE, snapshot.kind.name)
            .putInt(KEY_STREAK, snapshot.streak)
            .putString(KEY_MOMENT_ID, snapshot.momentId)
            .putString(KEY_IMAGE_PATH, snapshot.imagePath)
            .putLong(KEY_UPDATED_AT, snapshot.updatedAt)
            .apply()
    }

    /** Xoa snapshot + file anh (goi khi logout). */
    fun clear() {
        prefs.edit().clear().apply()
        imageFile().delete()
    }

    /** File anh widget (dam bao thu muc ton tai). */
    fun imageFile(): File {
        val dir = File(context.filesDir, IMAGE_DIR).apply { mkdirs() }
        return File(dir, IMAGE_NAME)
    }

    companion object {
        private const val PREFS_NAME = "snapget_widget"
        private const val KEY_STATE = "state"
        private const val KEY_STREAK = "streak"
        private const val KEY_MOMENT_ID = "moment_id"
        private const val KEY_IMAGE_PATH = "image_path"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val IMAGE_DIR = "widget"
        private const val IMAGE_NAME = "latest.jpg"
    }
}
