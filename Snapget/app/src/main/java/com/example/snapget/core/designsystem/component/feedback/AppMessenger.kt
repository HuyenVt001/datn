package com.example.snapget.core.designsystem.component.feedback

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.snapget.core.designsystem.skin.SkinTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Thong bao ngan dung CHUNG toan app (P5 Playful v2 — thay Toast): Snackbar style
 * app (nen `surface`, chu `textPrimary`, nut action `accent`), noi tren footer, co
 * action (Retry/Undo) khi can. Host dat 1 lan o MainActivity.
 *
 * Dung trong Composable: `val messenger = rememberMessenger()` -> `messenger.show("...")`.
 * Khong co host (preview / ngoai cay MainActivity) -> tu lui ve Toast.
 */
@Stable
class AppMessenger(
    internal val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    fun show(
        message: String,
        actionLabel: String? = null,
        long: Boolean = false,
        onAction: (() -> Unit)? = null,
    ) {
        scope.launch {
            // Thong bao moi thay thong bao cu dang hien (khong xep hang cho lau)
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = false,
                duration = when {
                    actionLabel != null -> SnackbarDuration.Long
                    long -> SnackbarDuration.Long
                    else -> SnackbarDuration.Short
                },
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

val LocalAppMessenger = staticCompositionLocalOf<AppMessenger?> { null }

/** Giao dien thong nhat cho cac man: `show(message)` — co host thi Snackbar, khong thi Toast. */
@Stable
fun interface Messenger {
    fun show(message: String)
}

@Composable
fun rememberMessenger(): Messenger {
    val app = LocalAppMessenger.current
    val context = LocalContext.current
    return remember(app, context) {
        if (app != null) {
            Messenger { app.show(it, long = it.length > 60) }
        } else {
            Messenger { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
    }
}

/** Host Snackbar style app — dat o MainActivity, tren footer. */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            modifier = Modifier.padding(horizontal = 16.dp),
            shape = SkinTheme.shapes.input,
            containerColor = SkinTheme.colors.surfaceVariant,
            contentColor = SkinTheme.colors.textPrimary,
            action = data.visuals.actionLabel?.let { label ->
                {
                    TextButton(onClick = { data.performAction() }) {
                        Text(text = label, color = SkinTheme.colors.accent)
                    }
                }
            },
        ) {
            Text(text = data.visuals.message)
        }
    }
}
