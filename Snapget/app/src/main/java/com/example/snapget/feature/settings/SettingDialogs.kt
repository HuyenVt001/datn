package com.example.snapget.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.snapget.core.designsystem.theme.SnapYellow
import com.example.snapget.core.model.ThemeMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Dialog chon che do giao dien (muc Theme trong Settings).
 * Chon radio la apply ngay (persist + toan app recompose) roi dong dialog.
 */
@Composable
fun ThemeDialog(
    currentMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        ThemeMode.DARK to "Dark",
        ThemeMode.LIGHT to "Light",
        ThemeMode.SYSTEM to "Follow system",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Theme",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                options.forEach { (mode, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onSelect(mode) },
                            colors = RadioButtonDefaults.colors(selectedColor = SnapYellow),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Close", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

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
                        color = SnapYellow,
                    )
                } else {
                    Text(text = "Save", color = SnapYellow, fontWeight = FontWeight.Bold)
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
                        color = SnapYellow,
                    )
                } else {
                    Text(text = "Save", color = SnapYellow, fontWeight = FontWeight.Bold)
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
