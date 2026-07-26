package com.example.snapget.feature.widget.di

import com.example.snapget.core.data.SettingsPreferences
import com.example.snapget.feature.widget.WidgetRefresher
import com.example.snapget.feature.widget.data.WidgetRepository
import com.example.snapget.feature.widget.data.WidgetSnapshotStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Cua vao Hilt cho cac class KHONG constructor-inject duoc:
 * GlanceAppWidget, GlanceAppWidgetReceiver va CoroutineWorker thuong.
 * (Khong dung @HiltWorker de tranh phai them hilt-work + Configuration.Provider.)
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetRepository(): WidgetRepository
    fun widgetSnapshotStore(): WidgetSnapshotStore
    fun settingsPreferences(): SettingsPreferences
    fun widgetRefresher(): WidgetRefresher
}
