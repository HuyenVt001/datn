package com.example.snapget.feature.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.snapget.feature.widget.data.WidgetSnapshot
import com.example.snapget.feature.widget.data.WidgetSnapshotStore
import com.example.snapget.feature.widget.data.WidgetStateKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade duy nhat de cac ViewModel/Receiver dieu khien widget:
 * len lich refresh (WorkManager) + ve lai (updateAll) — giu Context ngoai VM.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snapshotStore: WidgetSnapshotStore,
) {

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Refresh 1 lan ngay (mo app / dang bai xong). Worker tu no-op neu chua dang nhap. */
    fun refreshNow() {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONCE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /** Refresh dinh ky ~30 phut (goi khi widget duoc them / app khoi dong ma co widget). */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            WIDGET_REFRESH_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Huy refresh dinh ky (goi khi user go het widget). */
    fun cancelPeriodic() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    /** Ve lai widget tu snapshot hien tai — KHONG goi mang (vd doi toggle streak). */
    suspend fun updateWidgets() {
        SnapgetWidget().updateAll(context)
    }

    /** Logout: xoa snapshot + anh, chuyen widget sang trang thai "Sign in". */
    suspend fun markSignedOut() {
        snapshotStore.clear()
        snapshotStore.save(
            WidgetSnapshot(kind = WidgetStateKind.SIGNED_OUT, updatedAt = System.currentTimeMillis()),
        )
        updateWidgets()
    }

    /** Co widget nao dang duoc dat tren man hinh chinh khong. */
    suspend fun hasWidgets(): Boolean = GlanceAppWidgetManager(context).getGlanceIds(SnapgetWidget::class.java).isNotEmpty()

    companion object {
        /** Chu ky refresh widget (hang so co ten theo CLAUDE.md muc 9). */
        const val WIDGET_REFRESH_INTERVAL_MINUTES = 30L

        private const val ONCE_WORK = "widget_refresh_once"
        private const val PERIODIC_WORK = "widget_refresh_periodic"
    }
}
