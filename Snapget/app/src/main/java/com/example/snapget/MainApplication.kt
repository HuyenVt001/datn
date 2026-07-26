package com.example.snapget

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.example.snapget.core.fcm.SnapgetMessagingService
import com.example.snapget.feature.widget.WidgetRefresher
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath

@HiltAndroidApp
class MainApplication : Application() {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var widgetRefresher: WidgetRefresher

    @OptIn(ExperimentalCoilApi::class)
    override fun onCreate() {
        super.onCreate()

        // Notification channel for FCM pushes (required on Android O+)
        SnapgetMessagingService.createDefaultChannel(this)

        // Widget dang duoc dat -> dam bao lich refresh dinh ky con song
        // (onEnabled cua receiver KHONG chay lai sau khi app update/reinstall)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (widgetRefresher.hasWidgets()) {
                    widgetRefresher.schedulePeriodic()
                }
            } catch (_: Exception) {
                // Best-effort — khong duoc lam crash app khi khoi dong
            }
        }

        // Method 1: Using setSafe (recommended)
        SingletonImageLoader.setSafe {
            ImageLoader.Builder(this@MainApplication)
                .components {
                    add(OkHttpNetworkFetcherFactory(okHttpClient))
                }
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(this@MainApplication, 0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(this@MainApplication.cacheDir.resolve("image_cache").absolutePath.toPath())
                        .maxSizeBytes(512L * 1024 * 1024) // 512MB
                        .build()
                }
                .crossfade(true)
                .build()
        }
    }
}
