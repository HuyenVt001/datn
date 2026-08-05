package com.example.snapget.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.snapget.core.designsystem.skin.SkinTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ThemeDialog da XOA (2026-08-05 — SKIN_PLAN.md muc 4.4): giao dien Light bi go
// han, nguoi dung chon SKIN o man Appearance thay vi chon sang/toi o day.

/**
 * Dialog doi ten hien thi — ban rut gon cua EditProfileDialog (feature/profile),
 * khong co phan avatar. Dung mau colorScheme de doi theo theme.
 */
@Composable
fun EditNameDialog(
    currentName: String,
    isSaving: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(currentName) { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Edit name",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = { Text("Display name") },
                singleLine = true,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = !isSaving && name.isNotBlank() && name.trim() != currentName,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = SkinTheme.colors.accent,
                    )
                } else {
                    Text(text = "Save", color = SkinTheme.colors.accent, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(text = "Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

/**
 * Dialog chon ngay sinh (Material3 DatePicker). Luu dang ISO yyyy-MM-dd.
 * Parse/format bang SimpleDateFormat UTC (DatePickerState dung millis UTC-midnight;
 * tranh java.time de khoi phai @RequiresApi(O) voi minSdk 24).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayPickerDialog(
    currentBirthday: String?,
    isSaving: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = remember(currentBirthday) { parseIsoDateToUtcMillis(currentBirthday) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        yearRange = 1900..Calendar.getInstance().get(Calendar.YEAR),
    )

    DatePickerDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onSave(formatUtcMillisToIso(it)) }
                },
                enabled = !isSaving && state.selectedDateMillis != null,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = SkinTheme.colors.accent,
                    )
                } else {
                    Text(text = "Save", color = SkinTheme.colors.accent, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(text = "Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

private const val MAX_NAME_LENGTH = 30

private fun isoDateFormat(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun parseIsoDateToUtcMillis(iso: String?): Long? = iso
    ?.takeIf { it.isNotBlank() }
    ?.let { runCatching { isoDateFormat().parse(it)?.time }.getOrNull() }

private fun formatUtcMillisToIso(millis: Long): String = isoDateFormat().format(Date(millis))
