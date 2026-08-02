package com.example.snapget.feature.coop

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.snapget.core.designsystem.component.circle.Circle
import com.example.snapget.core.designsystem.component.circle.IconSetting
import com.example.snapget.core.designsystem.component.circle.ImageSetting
import com.example.snapget.core.designsystem.component.common.CommonTopBar
import com.example.snapget.core.designsystem.preview.CameraPreviewWithZoom
import com.example.snapget.core.model.FriendUi
import com.example.snapget.core.util.avatarOrDefault
import com.example.snapget.core.util.downloadToCacheFile
import com.example.snapget.navigation.Screen
import java.io.File
import kotlinx.coroutines.delay

/**
 * Man CHUP COOP (redesign 2026-08-02) — ca 2 nguoi vao day sau khi loi moi
 * duoc chap nhan. Nua man = camera cua MINH (trai = nguoi moi / phai = nguoi
 * nhan, khop thu tu ghep server), nua kia mau xam + vong xoay cho anh doi
 * phuong. Chup -> hien nut SEND tron (mui ten) + icon AGAIN ben trai de chup
 * lai; send xong an het nut, hien "doi ban be chup". Ca 2 nop du -> server
 * ghep -> tu tai anh ghep ve -> sang EditMedia (luong dang bai thuong).
 * Poll trang thai loi moi ~2.5s/lan (REST thuan, khong websocket).
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CoopCaptureScreen(
    navController: NavController,
    inviteId: String,
    partnerName: String,
    coopViewModel: CoopViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val invite by coopViewModel.invite.collectAsState()
    val busy by coopViewModel.busy.collectAsState()
    val coopError by coopViewModel.coopError.collectAsState()
    val myUid = coopViewModel.myUid

    var captureRequestId by remember { mutableIntStateOf(0) }
    var myPhotoPath by remember { mutableStateOf<String?>(null) }
    // Chan navigate sang EditMedia 2 lan (poll van chay trong luc tai anh ghep)
    var navigatedToEdit by remember { mutableStateOf(false) }

    // Poll trang thai loi moi 2.5s/lan (accept/nua anh doi phuong/anh ghep/het han)
    LaunchedEffect(inviteId) {
        coopViewModel.refreshInvite(inviteId)
        while (true) {
            delay(2500)
            coopViewModel.refreshInvite(inviteId)
        }
    }

    DisposableEffect(Unit) {
        onDispose { coopViewModel.clearInvite() }
    }

    LaunchedEffect(coopError) {
        coopError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            coopViewModel.clearError()
        }
    }

    val current = invite
    val isInviter = current?.inviterId == myUid
    val myUrl = if (isInviter) current?.inviterMediaUrl else current?.inviteeMediaUrl
    val partnerUrl = if (isInviter) current?.inviteeMediaUrl else current?.inviterMediaUrl
    val mySubmitted = !myUrl.isNullOrBlank()

    // Trang thai ket thuc: tu choi/het han -> thoat; du anh ghep -> tai ve + EditMedia
    LaunchedEffect(current?.status, current?.mergedMediaUrl, navigatedToEdit) {
        when {
            current == null -> Unit
            current.status == "DECLINED" -> {
                Toast.makeText(context, "$partnerName declined the invite.", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
            current.status == "EXPIRED" -> {
                Toast.makeText(context, "The invite expired (5 minutes).", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
            current.status == "COMPLETED" && current.mergedMediaUrl != null && !navigatedToEdit -> {
                navigatedToEdit = true
                val file = downloadToCacheFile(context, current.mergedMediaUrl, "coop_merged")
                if (file != null) {
                    // Vao luong dang bai thuong voi anh ghep; don man coop khoi stack
                    navController.navigate(
                        Screen.EditMedia.route + "?mediaPath=" + Uri.encode(file.absolutePath),
                    ) {
                        popUpTo(Screen.Camera.route)
                    }
                } else {
                    // Tai fail (mang cham) -> mo lai co de retry o lan poll sau
                    Toast.makeText(context, "Downloading merged photo…", Toast.LENGTH_SHORT).show()
                    navigatedToEdit = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CommonTopBar(
                navController = navController,
                title = "Co-op Capture",
                titleColor = MaterialTheme.colorScheme.onBackground,
                startIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onStartIconClick = { navController.popBackStack() },
            )
        },
    ) { paddingValues ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
        ) {
            when {
                current == null -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
                }

                current.status == "PENDING" -> {
                    // Nguoi moi doi doi phuong chap nhan (nguoi nhan chi vao day sau accept)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.Yellow)
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Waiting for $partnerName to accept…",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Invites expire after 5 minutes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        TextButton(
                            onClick = {
                                coopViewModel.declineInvite(inviteId)
                                navController.popBackStack()
                            },
                        ) {
                            Text(text = "Cancel invite", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                else -> {
                    // ==== ACCEPTED (dang chup) / COMPLETED (dang tai anh ghep) ====
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp)),
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // TRAI = nua nguoi moi, PHAI = nua nguoi nhan (khop server ghep)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            ) {
                                if (isInviter) {
                                    CoopMyHalf(
                                        lifecycleOwner = lifecycleOwner,
                                        myUrl = myUrl,
                                        myPhotoPath = myPhotoPath,
                                        captureRequestId = captureRequestId,
                                        onPhotoTaken = { myPhotoPath = it },
                                    )
                                } else {
                                    CoopPartnerHalf(partnerUrl = partnerUrl, partnerName = partnerName)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            ) {
                                if (isInviter) {
                                    CoopPartnerHalf(partnerUrl = partnerUrl, partnerName = partnerName)
                                } else {
                                    CoopMyHalf(
                                        lifecycleOwner = lifecycleOwner,
                                        myUrl = myUrl,
                                        myPhotoPath = myPhotoPath,
                                        captureRequestId = captureRequestId,
                                        onPhotoTaken = { myPhotoPath = it },
                                    )
                                }
                            }
                        }

                        // Ca 2 da nop -> server dang ghep (hoac dang tai anh ghep ve)
                        if (mySubmitted && !partnerUrl.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.White)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Merging photos…",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    if (mySubmitted) {
                        // Da gui nua cua minh: AN het nut, hien trang thai doi doi phuong
                        if (partnerUrl.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Photo sent — waiting for $partnerName to take theirs…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        // Hang nut: [AGAIN (chi sau khi chup)] [CHUP hoac SEND] [cho doi xung]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (myPhotoPath != null) {
                                IconButton(
                                    onClick = {
                                        // Chup lai: reset captureRequestId ve 0 — preview MOI
                                        // vao composition se khong tu chup ngay vi id > 0 cu
                                        myPhotoPath = null
                                        captureRequestId = 0
                                    },
                                    enabled = !busy,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Replay,
                                        contentDescription = "Retake",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(28.dp))
                            } else {
                                // Giu nut giua co dinh khi icon again chua hien
                                Spacer(modifier = Modifier.width(48.dp + 28.dp))
                            }

                            if (myPhotoPath == null) {
                                // Nut CHUP 80dp vien vang (nhu nut chup camera chinh)
                                Circle(
                                    outerSize = 80.dp,
                                    gap = 7.dp,
                                    backgroundColor = Color.Transparent,
                                    borderColor = Color.Yellow,
                                    borderWidth = 3.dp,
                                    onClick = { captureRequestId++ },
                                )
                            } else {
                                // Nut SEND tron icon mui ten (cung style nut Send man dang bai)
                                Circle(
                                    outerSize = 80.dp,
                                    gap = 20.dp,
                                    backgroundColor = Color.Transparent,
                                    borderColor = Color.Yellow,
                                    borderWidth = 3.dp,
                                    modifier = Modifier.rotate(-45F),
                                    iconSetting = IconSetting(
                                        icon = Icons.AutoMirrored.Filled.Send,
                                        tint = Color.White,
                                        contentDescription = "Send photo",
                                    ),
                                    onClick = {
                                        if (!busy) {
                                            coopViewModel.submitHalf(inviteId, File(myPhotoPath!!))
                                        }
                                    },
                                )
                            }

                            Spacer(modifier = Modifier.width(48.dp + 28.dp))
                        }
                        if (busy) {
                            Text(
                                text = "Sending your photo…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Nua anh cua MINH: da nop -> anh tren server; vua chup -> file local; chua -> camera. */
@Composable
private fun CoopMyHalf(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    myUrl: String?,
    myPhotoPath: String?,
    captureRequestId: Int,
    onPhotoTaken: (String) -> Unit,
) {
    when {
        !myUrl.isNullOrBlank() -> AsyncImage(
            model = myUrl,
            contentDescription = "Your half",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        myPhotoPath != null -> AsyncImage(
            model = File(myPhotoPath),
            contentDescription = "Your half",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        else -> CameraPreviewWithZoom(
            lifecycleOwner = lifecycleOwner,
            height = 400.dp,
            onPhotoTaken = onPhotoTaken,
            showControls = false,
            captureRequestId = captureRequestId,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Nua cua DOI PHUONG: chua co anh -> nen xam + vong xoay cho (theo spec user). */
@Composable
private fun CoopPartnerHalf(partnerUrl: String?, partnerName: String) {
    if (!partnerUrl.isNullOrBlank()) {
        AsyncImage(
            model = partnerUrl,
            contentDescription = "$partnerName's half",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF2C2C2C)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFB0B0B0),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Waiting for\n$partnerName…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0B0B0),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Popup chon ban gui loi moi chup chung (mo tu nut Co-op tren man camera).
 * Chon 1 nguoi -> bam Send invite (loi moi hieu luc 5 phut, khong kem anh).
 */
@Composable
fun CoopFriendPickerDialog(
    friends: List<FriendUi>,
    busy: Boolean,
    onSend: (FriendUi) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2C2C2C),
        title = {
            Text(text = "Co-op capture", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Pick a friend to shoot with — invites expire after 5 minutes.",
                    color = Color(0xFFB0B0B0),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (friends.isEmpty()) {
                    Text(
                        text = "No friends yet — add a friend first!",
                        color = Color(0xFFB0B0B0),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(friends) { friend ->
                        val selected = selectedId == friend.id
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedId = friend.id }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                        ) {
                            Circle(
                                outerSize = 40.dp,
                                gap = 0.dp,
                                backgroundColor = Color(0xFF404137),
                                // Vien VANG khi duoc chon (nhu FriendList man dang bai)
                                borderColor = if (selected) Color.Yellow else Color(0xFF404137),
                                onClick = { selectedId = friend.id },
                                imageSetting = ImageSetting(
                                    imageUrl = avatarOrDefault(friend.avatar, friend.name),
                                    contentDescription = friend.name,
                                ),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = friend.name,
                                color = Color.White,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { friends.find { it.id == selectedId }?.let(onSend) },
                enabled = selectedId != null && !busy,
            ) {
                Text(
                    text = if (busy) "Sending…" else "Send invite",
                    color = Color.Yellow,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = Color.White)
            }
        },
    )
}
