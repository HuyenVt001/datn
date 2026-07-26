package com.example.snapget.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.snapget.feature.widget.data.WidgetSnapshot
import com.example.snapget.feature.widget.data.WidgetStateKind
import com.example.snapget.feature.widget.di.WidgetEntryPoint
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.EntryPointAccessors

/**
 * Worker refresh widget: lay streak + moment PHOTO moi nhat -> tai anh -> luu
 * snapshot -> ve lai widget. CoroutineWorker THUONG (khong @HiltWorker),
 * dependency lay qua [WidgetEntryPoint].
 */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
        val store = entryPoint.widgetSnapshotStore()
        val repository = entryPoint.widgetRepository()

        // Chua dang nhap -> widget hien "Sign in", khong goi mang
        if (FirebaseAuth.getInstance().currentUser == null) {
            store.save(
                WidgetSnapshot(kind = WidgetStateKind.SIGNED_OUT, updatedAt = System.currentTimeMillis()),
            )
            SnapgetWidget().updateAll(applicationContext)
            return Result.success()
        }

        return try {
            val (streak, moment) = repository.fetchLatest()
            if (moment == null) {
                store.save(
                    WidgetSnapshot(
                        kind = WidgetStateKind.EMPTY,
                        streak = streak,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                val imageFile = store.imageFile()
                val previous = store.read()
                // Moment khong doi + anh con -> khoi tai lai
                if (previous.momentId != moment.momentId || !imageFile.exists()) {
                    repository.downloadPhoto(moment.mediaUrl, imageFile)
                }
                store.save(
                    WidgetSnapshot(
                        kind = WidgetStateKind.OK,
                        streak = streak,
                        momentId = moment.momentId,
                        imagePath = imageFile.absolutePath,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
            SnapgetWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            // Loi mang: giu snapshot cu tren widget; thu lai toi da 2 lan
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val MAX_RETRIES = 2
    }
}
