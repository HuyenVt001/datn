package com.example.snapget.feature.message

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.util.copyUriToCacheFile
import kotlinx.coroutines.delay

/**
 * Chat NHOM (<=20 thanh vien) — dung chung MessageBubble/ChatInputPill voi chat 1-1.
 * Ten nguoi gui resolve tu danh sach ban be (khong phai ban -> "Snapget user").
 * Tin moi lay bang POLLING ~5s nhu chat 1-1.
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun GroupChatScreen(
    navController: NavController,
    groupId: String,
    groupName: String,
    messageViewModel: MessageViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val thread by messageViewModel.thread.collectAsState()
    val threadStatus by messageViewModel.threadStatus.collectAsState()
    val friendsById by messageViewModel.friendsById.collectAsState()
    val sendError by messageViewModel.sendError.collectAsState()
    val sendingMedia by messageViewModel.sendingMedia.collectAsState()
    val myUid = messageViewModel.myUid

    val listState = rememberLazyListState()
    var messageText by remember { mutableStateOf("") }

    // Chon anh tu thu vien -> upload -> gui tin PHOTO vao nhom
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { copyUriToCacheFile(context, it, "chat") }?.let { file ->
            messageViewModel.sendMedia(null, groupId, file, "image/jpeg", "PHOTO")
        }
    }

    // Ghi am -> upload -> gui tin VOICE vao nhom
    val voiceRecorder = rememberVoiceRecorder { file ->
        messageViewModel.sendMedia(null, groupId, file, "audio/mp4", "VOICE")
    }

    // Lan dau: tai ban be (resolve ten — chi khi chua co) + thread nhom; polling 5s/lan
    LaunchedEffect(groupId) {
        messageViewModel.loadFriendsIfNeeded()
        messageViewModel.refreshGroupThread(groupId, showLoading = true)
        while (true) {
            delay(5000)
            messageViewModel.refreshGroupThread(groupId)
        }
    }

    DisposableEffect(Unit) {
        onDispose { messageViewModel.clearThread() }
    }

    LaunchedEffect(sendError) {
        sendError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            messageViewModel.clearSendError()
        }
    }

    LaunchedEffect(thread.size) {
        if (thread.isNotEmpty()) {
            listState.animateScrollToItem(thread.size - 1)
        }
    }

    Scaffold(
        topBar = {
            GroupChatTopBar(navController = navController, groupName = groupName)
        },
        bottomBar = {
            ChatInputPill(
                messageText = messageText,
                onTextChange = { messageText = it },
                onSend = {
                    messageViewModel.sendGroupMessage(groupId, messageText)
                    messageText = ""
                },
                onEmojiSend = { emoji ->
                    messageViewModel.sendGroupMessage(groupId, emoji, messageType = "EMOJI")
                },
                modifier = Modifier.padding(bottom = 15.dp),
                onPhotoClick = { photoPicker.launch("image/*") },
                onStickerSend = { url ->
                    messageViewModel.sendGroupMessage(groupId, url, messageType = "STICKER")
                },
                voiceRecorder = voiceRecorder,
                isSendingMedia = sendingMedia,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
        ) {
            when {
                threadStatus is LoadStatus.Loading && thread.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                    )
                }

                thread.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "No messages yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (threadStatus is LoadStatus.Error) {
                                (threadStatus as LoadStatus.Error).error
                            } else {
                                "Send the first message to the group!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(thread) { index, message ->
                            val isFromCurrentUser = message.senderId == myUid
                            val previousMessage = if (index > 0) thread[index - 1] else null
                            val nextMessage = if (index < thread.size - 1) thread[index + 1] else null

                            val sender = friendsById[message.senderId]
                            val showAvatar = shouldShowAvatar(
                                currentMessage = message,
                                nextMessage = nextMessage,
                                isFromCurrentUser = isFromCurrentUser,
                            )
                            val isNewSenderGroup = isNewSenderGroup(message, previousMessage)

                            Column {
                                if (isNewSenderGroup) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                MessageBubble(
                                    message = message,
                                    isFromCurrentUser = isFromCurrentUser,
                                    senderName = if (isFromCurrentUser) {
                                        "You"
                                    } else {
                                        sender?.name ?: "Snapget user"
                                    },
                                    senderAvatar = sender?.avatar.orEmpty(),
                                    showAvatar = showAvatar,
                                    isFirstInGroup = isNewSenderGroup,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupChatTopBar(
    navController: NavController,
    groupName: String,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Group chat",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
