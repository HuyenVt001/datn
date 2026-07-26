package com.example.snapget.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import com.example.snapget.core.designsystem.component.common.CommonTopBar
import com.example.snapget.core.designsystem.theme.GrayBackground
import com.example.snapget.core.designsystem.theme.SnapYellow
import com.example.snapget.feature.widget.data.WidgetStateKind
import com.example.snapget.navigation.Screen
import java.io.File

/**
 * Man Widget Settings (mo tu Settings): preview widget, toggle streak,
 * refresh tay + nut them widget vao man hinh chinh (pin, API 26+).
 */
@Composable
fun WidgetSettingsScreen(
    navController: NavHostController = rememberNavController(),
    viewModel: WidgetSettingsViewModel = hiltViewModel(),
) {
    val streakOnWidget by viewModel.streakOnWidget.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    LaunchedEffect(Unit) { viewModel.reloadSnapshot() }

    Scaffold(
        topBar = {
            CommonTopBar(
                navController = navController,
                title = "Widget Settings",
                startIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onStartIconClick = { navController.popBackStack() },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(0.dp))

            // Preview widget (mock bang Compose thuong — widget that render qua Glance)
            WidgetPreviewCard(
                imagePath = snapshot.imagePath.takeIf { snapshot.kind == WidgetStateKind.OK },
                streak = snapshot.streak,
                showStreak = streakOnWidget,
            )

            // Toggle streak — cung key voi muc "Streak on widget" ben man Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Streak on widget",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Show your streak on the home screen widget",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = streakOnWidget,
                        onCheckedChange = { viewModel.setStreakOnWidget(it) },
                    )
                }
            }

            // Refresh tay + thoi diem cap nhat gan nhat
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.refreshNow() }, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = SnapYellow,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = SnapYellow,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Refresh now", color = SnapYellow, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (snapshot.updatedAt > 0L) {
                        "Last updated: ${DateUtils.getRelativeTimeSpanString(snapshot.updatedAt)}"
                    } else {
                        "Not updated yet"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AddWidgetButton(navController)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Preview mo phong widget: anh (neu co) + badge streak goc duoi-phai. */
@Composable
private fun WidgetPreviewCard(
    imagePath: String?,
    streak: Int,
    showStreak: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GrayBackground),
        contentAlignment = Alignment.BottomEnd,
    ) {
        if (imagePath != null) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = "Widget preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No moments yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showStreak) {
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "🔥 $streak",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
    }
}

/** Nut "Add to Home Screen": pin truc tiep (API 26+, launcher ho tro) hoac mo man huong dan. */
@Composable
private fun AddWidgetButton(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        onClick = {
            if (!requestPinWidget(context)) {
                navController.navigate(Screen.HowToAddWidget.route)
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.AddToHomeScreen,
                contentDescription = null,
                tint = SnapYellow,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Add to Home Screen",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Yeu cau launcher pin widget. Tra ve false neu khong ho tro (API < 26 hoac
 * launcher khong co pin) — caller fallback sang man huong dan.
 */
internal fun requestPinWidget(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val manager = context.getSystemService(AppWidgetManager::class.java) ?: return false
    if (!manager.isRequestPinAppWidgetSupported) return false
    val provider = ComponentName(context, SnapgetWidgetReceiver::class.java)
    return try {
        manager.requestPinAppWidget(provider, null, null)
    } catch (_: Exception) {
        Toast.makeText(context, "Couldn't open the widget picker", Toast.LENGTH_SHORT).show()
        false
    }
}
