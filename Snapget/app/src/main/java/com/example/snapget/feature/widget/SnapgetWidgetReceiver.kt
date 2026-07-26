package com.example.snapget.feature.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.snapget.feature.widget.di.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Receiver cua widget. onEnabled = user dat widget DAU TIEN -> len lich refresh
 * dinh ky + refresh ngay; onDisabled = go het widget -> huy lich.
 * (Khong dung @AndroidEntryPoint — GlanceAppWidgetReceiver tu xu ly onReceive.)
 */
class SnapgetWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = SnapgetWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val refresher = EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
            .widgetRefresher()
        refresher.schedulePeriodic()
        refresher.refreshNow()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        EntryPointAccessors
            .fromApplication(context, WidgetEntryPoint::class.java)
            .widgetRefresher()
            .cancelPeriodic()
    }
}
