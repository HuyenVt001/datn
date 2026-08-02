package com.example.snapget.feature.post

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.snapget.core.util.MediaActions
import kotlinx.coroutines.launch

/**
 * Nut Download dung chung (feed pager + man xem post tu profile — tach helper
 * 2026-08-02 de 2 man khong lap code): Android 7-9 (API < 29) can xin
 * WRITE_EXTERNAL_STORAGE runtime truoc khi luu ve thu vien (fix 2026-07-26 —
 * khong xin thi insert fail -> "Download failed").
 */
@Composable
fun rememberGalleryDownloader(): (String, Boolean) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDownload by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val downloadToGallery: (String, Boolean) -> Unit = { url, video ->
        scope.launch {
            val ok = MediaActions.saveToGallery(context, url, video)
            Toast.makeText(
                context,
                if (ok) "Saved to gallery." else "Download failed.",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pendingDownload
        pendingDownload = null
        if (request != null) {
            if (granted) {
                downloadToGallery(request.first, request.second)
            } else {
                Toast.makeText(
                    context,
                    "Storage permission is required to download.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    return { url, video ->
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingDownload = url to video
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            downloadToGallery(url, video)
        }
    }
}
