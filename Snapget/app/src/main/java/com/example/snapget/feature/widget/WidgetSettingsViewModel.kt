package com.example.snapget.feature.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapget.core.data.SettingIds
import com.example.snapget.core.data.SettingsPreferences
import com.example.snapget.feature.widget.data.WidgetSnapshot
import com.example.snapget.feature.widget.data.WidgetSnapshotStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** ViewModel man Widget Settings: preview + toggle streak + refresh tay. */
@HiltViewModel
class WidgetSettingsViewModel @Inject constructor(
    private val settingsPreferences: SettingsPreferences,
    private val snapshotStore: WidgetSnapshotStore,
    private val widgetRefresher: WidgetRefresher,
) : ViewModel() {

    private val _streakOnWidget = MutableStateFlow(
        settingsPreferences.isToggled(SettingIds.STREAK_ON_WIDGET, default = true),
    )
    val streakOnWidget: StateFlow<Boolean> = _streakOnWidget.asStateFlow()

    private val _snapshot = MutableStateFlow(snapshotStore.read())
    val snapshot: StateFlow<WidgetSnapshot> = _snapshot.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Bat/tat streak tren widget: dung chung key toggle voi man Settings. */
    fun setStreakOnWidget(enabled: Boolean) {
        settingsPreferences.setToggle(SettingIds.STREAK_ON_WIDGET, enabled)
        _streakOnWidget.value = enabled
        viewModelScope.launch { widgetRefresher.updateWidgets() }
    }

    /** Refresh tay: enqueue worker roi doc lai snapshot sau vai giay de cap nhat preview. */
    fun refreshNow() {
        if (_isRefreshing.value) return
        widgetRefresher.refreshNow()
        viewModelScope.launch {
            _isRefreshing.value = true
            // Worker chay async — doi ngan roi doc lai snapshot (best-effort cho preview)
            delay(RELOAD_DELAY_MS)
            _snapshot.value = snapshotStore.read()
            _isRefreshing.value = false
        }
    }

    /** Doc lai snapshot (goi khi man hinh mo lai). */
    fun reloadSnapshot() {
        _snapshot.value = snapshotStore.read()
    }

    companion object {
        private const val RELOAD_DELAY_MS = 3_000L
    }
}
