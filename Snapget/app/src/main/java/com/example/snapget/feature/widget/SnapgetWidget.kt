package com.example.snapget.feature.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.snapget.MainActivity
import com.example.snapget.R
import com.example.snapget.core.data.SettingIds
import com.example.snapget.feature.widget.data.WidgetSnapshot
import com.example.snapget.feature.widget.data.WidgetStateKind
import com.example.snapget.feature.widget.di.WidgetEntryPoint
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.EntryPointAccessors

/**
 * Widget man hinh chinh: anh moment PHOTO moi nhat lam nen + badge streak
 * (an duoc qua toggle "Streak on widget"). CHI doc snapshot local — khong goi mang.
 */
class SnapgetWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val store = entryPoint.widgetSnapshotStore()
        val settingsPreferences = entryPoint.settingsPreferences()

        var snapshot = store.read()
        // Lan dau dat widget (worker chua chay): quyet dinh state theo trang thai dang nhap
        if (snapshot.updatedAt == 0L) {
            snapshot = snapshot.copy(
                kind = if (FirebaseAuth.getInstance().currentUser != null) {
                    WidgetStateKind.EMPTY
                } else {
                    WidgetStateKind.SIGNED_OUT
                },
            )
        }
        val showStreak = settingsPreferences.isToggled(SettingIds.STREAK_ON_WIDGET, default = true)
        // Decode bitmap TRUOC provideContent (khong lam IO trong composition)
        val bitmap = snapshot.imagePath?.let { BitmapFactory.decodeFile(it) }

        provideContent {
            WidgetContent(
                context = context,
                snapshot = snapshot,
                showStreak = showStreak,
                bitmap = bitmap,
            )
        }
    }

    companion object {
        /** Extra dieu huong khi tap widget — MainActivity doc va chuyen route. */
        const val EXTRA_WIDGET_ROUTE = "snapget.widget.route"
        const val ROUTE_FEED = "post"
        const val ROUTE_CAMERA = "camera"
    }
}

@androidx.compose.runtime.Composable
private fun WidgetContent(
    context: Context,
    snapshot: WidgetSnapshot,
    showStreak: Boolean,
    bitmap: Bitmap?,
) {
    when {
        snapshot.kind == WidgetStateKind.OK && bitmap != null ->
            MomentWidgetBody(context, bitmap, snapshot.streak, showStreak)

        snapshot.kind == WidgetStateKind.SIGNED_OUT ->
            MessageWidgetBody(
                context = context,
                message = "Sign in to Snapget",
                route = null,
            )

        // EMPTY (hoac OK nhung mat file anh -> cho lan refresh sau)
        else ->
            MessageWidgetBody(
                context = context,
                message = "No moments yet — take your first snap!",
                route = SnapgetWidget.ROUTE_CAMERA,
            )
    }
}

/** Anh moment full-bleed + badge streak goc duoi-phai. */
@androidx.compose.runtime.Composable
private fun MomentWidgetBody(
    context: Context,
    bitmap: Bitmap,
    streak: Int,
    showStreak: Boolean,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(openAppIntent(context, SnapgetWidget.ROUTE_FEED))),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "Latest moment",
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.fillMaxSize(),
        )
        if (showStreak) {
            Box(modifier = GlanceModifier.padding(8.dp)) {
                Text(
                    text = "🔥 $streak",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = GlanceModifier
                        .background(ImageProvider(R.drawable.widget_badge_bg))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** Trang thai EMPTY / SIGNED_OUT: nen toi bo goc + thong bao. */
@androidx.compose.runtime.Composable
private fun MessageWidgetBody(
    context: Context,
    message: String,
    route: String?,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_empty_bg))
            .clickable(actionStartActivity(openAppIntent(context, route))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier.padding(12.dp),
        ) {
            Image(
                provider = ImageProvider(R.mipmap.ic_launcher),
                contentDescription = "Snapget",
            )
            Text(
                text = message,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                modifier = GlanceModifier.padding(top = 8.dp),
            )
        }
    }
}

private fun openAppIntent(context: Context, route: String?): Intent = Intent(context, MainActivity::class.java).apply {
    flags = Intent.FLAG_ACTIVITY_NEW_TASK
    route?.let { putExtra(SnapgetWidget.EXTRA_WIDGET_ROUTE, it) }
}
