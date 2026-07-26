package com.example.snapget.feature.message

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.pill.quickReactionEmojis
import com.example.snapget.core.network.dto.MessageDto
import com.example.snapget.core.util.avatarOrDefault
import com.example.snapget.core.util.copyUriToCacheFile
import com.example.snapget.core.util.takeFirstNameOfUser
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * Chat 1-1 — doc/gui qua API /messages (da migrate khoi Firestore 2026-07-12).
 * Tin moi lay bang POLLING ~5s (server REST thuan, khong websocket).
 * Ho tro TEXT + EMOJI (emoji hien to, khong bubble).
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ChatScreen(
    navController: NavController,
    recipientId: String,
    messageViewModel: MessageViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val thread by messageViewModel.thread.collectAsState()
    val threadStatus by messageViewModel.threadStatus.collectAsState()
    val friendsById by messageViewModel.friendsById.collectAsState()
    val sendError by messageViewModel.sendError.collectAsState()
    val myUid = messageViewModel.myUid

    val recipient = friendsById[recipientId]
    val recipientName = recipient?.name ?: "Snapget user"
    val recipientAvatar = recipient?.avatar.orEmpty()

    val listState = rememberLazyListState()
    var messageText by remember { mutableStateOf("") }
    val sendingMedia by messageViewModel.sendingMedia.collectAsState()

    // Chon anh tu thu vien -> upload -> gui tin PHOTO
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { copyUriToCacheFile(context, it, "chat") }?.let { file ->
            messageViewModel.sendMedia(recipientId, null, file, "image/jpeg", "PHOTO")
        }
    }

    // Ghi am -> upload -> gui tin VOICE
    val voiceRecorder = rememberVoiceRecorder { file ->
        messageViewModel.sendMedia(recipientId, null, file, "audio/mp4", "VOICE")
    }

    // Lan dau: tai ban be (ten/avatar — chi khi chua co) + thread; sau do polling 5s/lan
    LaunchedEffect(recipientId) {
        messageViewModel.loadFriendsIfNeeded()
        messageViewModel.refreshThread(recipientId, showLoading = true)
        while (true) {
            delay(5000)
            messageViewModel.refreshThread(recipientId)
        }
    }

    // Roi man -> xoa thread de khong loe tin cu khi mo chat nguoi khac
    DisposableEffect(Unit) {
        onDispose { messageViewModel.clearThread() }
    }

    // Loi gui tin (vd: "Chi nhan tin duoc voi ban be.") -> Toast message cua server
    LaunchedEffect(sendError) {
        sendError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            messageViewModel.clearSendError()
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(thread.size) {
        if (thread.isNotEmpty()) {
            listState.animateScrollToItem(thread.size - 1)
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                navController = navController,
                recipientId = recipientId,
                recipientName = recipientName,
                recipientAvatar = recipientAvatar,
            )
        },
        bottomBar = {
            ChatInputPill(
                messageText = messageText,
                onTextChange = { messageText = it },
                onSend = {
                    messageViewModel.sendMessage(recipientId, messageText)
                    messageText = ""
                },
                onEmojiSend = { emoji ->
                    messageViewModel.sendMessage(recipientId, emoji, messageType = "EMOJI")
                },
                modifier = Modifier.padding(bottom = 15.dp),
                onPhotoClick = { photoPicker.launch("image/*") },
                onStickerSend = { url ->
                    messageViewModel.sendMessage(recipientId, url, messageType = "STICKER")
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
                    // Show empty state
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
                                "Send a message to start a conversation"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                else -> {
                    // Show messages
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

                            // Determine avatar visibility logic
                            val showAvatar = shouldShowAvatar(
                                currentMessage = message,
                                nextMessage = nextMessage,
                                isFromCurrentUser = isFromCurrentUser,
                            )

                            // Determine spacing logic
                            val isNewSenderGroup = isNewSenderGroup(message, previousMessage)
                            val messageSpacing = if (isNewSenderGroup) 16.dp else 2.dp

                            Column {
                                if (isNewSenderGroup) {
                                    Spacer(modifier = Modifier.height(messageSpacing))
                                }

                                MessageBubble(
                                    message = message,
                                    isFromCurrentUser = isFromCurrentUser,
                                    senderName = if (isFromCurrentUser) "You" else recipientName,
                                    senderAvatar = if (isFromCurrentUser) "" else recipientAvatar,
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

// Helper function to determine if avatar should be shown
internal fun shouldShowAvatar(
    currentMessage: MessageDto,
    nextMessage: MessageDto?,
    isFromCurrentUser: Boolean,
): Boolean {
    // Never show avatar for current user messages
    if (isFromCurrentUser) return false

    // Always show avatar if this is the last message
    if (nextMessage == null) return true

    // Show avatar if the next message is from a different sender
    return currentMessage.senderId != nextMessage.senderId
}

// Helper function to determine if this message starts a new sender group
internal fun isNewSenderGroup(
    currentMessage: MessageDto,
    previousMessage: MessageDto?,
): Boolean {
    // First message is always a new group
    if (previousMessage == null) return true

    // New group if sender changed
    return currentMessage.senderId != previousMessage.senderId
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    navController: NavController,
    recipientId: String,
    recipientName: String,
    recipientAvatar: String,
) {
    TopAppBar(
        title = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Recipient avatar with better styling
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    ) {
                        AsyncImage(
                            model = avatarOrDefault(recipientAvatar, recipientName),
                            contentDescription = "User avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        // Recipient name
                        Text(
                            text = takeFirstNameOfUser(recipientName),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        // Online status (optional)
                        Text(
                            text = "Active now",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
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
        actions = {
            IconButton(onClick = { /* Show options */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/**
 * Thanh nhap tin theo style MessagePill (bo 24dp, nen surfaceVariant — DESIGN 7.9):
 * o go text + emoji gui nhanh (tin EMOJI) + nut Send khi co text.
 * Truyen them callback de bat nut anh 📷 / sticker 😊 / mic 🎤 (media qua /upload).
 */
@Composable
fun ChatInputPill(
    messageText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onEmojiSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPhotoClick: (() -> Unit)? = null,
    onStickerSend: ((String) -> Unit)? = null,
    voiceRecorder: VoiceRecorderState? = null,
    isSendingMedia: Boolean = false,
) {
    var showStickers by remember { mutableStateOf(false) }
    val hasMediaButtons = onPhotoClick != null || onStickerSend != null || voiceRecorder != null

    Column(modifier = modifier) {
        // Khay sticker (bam icon 😊 de mo/dong) — bam sticker la gui luon
        if (showStickers && onStickerSend != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                stickerCatalog.forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = "Sticker",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onStickerSend(url)
                                showStickers = false
                            },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(24.dp),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = messageText,
                onValueChange = onTextChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (messageText.isEmpty()) {
                            Text(
                                text = if (voiceRecorder?.isRecording == true) {
                                    "Recording... tap the mic to send"
                                } else {
                                    "Send message..."
                                },
                                color = if (voiceRecorder?.isRecording == true) {
                                    Color.Red
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 16.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            if (messageText.isBlank()) {
                if (isSendingMedia) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    // Emoji gui nhanh — bam la gui 1 tin EMOJI luon
                    // (rut gon con 2 khi co them nut media cho khoi chat hang)
                    val quickEmojis = if (hasMediaButtons) {
                        quickReactionEmojis.take(2)
                    } else {
                        quickReactionEmojis
                    }
                    quickEmojis.forEach { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onEmojiSend(emoji) }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }

                    if (onPhotoClick != null) {
                        IconButton(onClick = onPhotoClick, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Send photo",
                                tint = Color.White,
                            )
                        }
                    }
                    if (onStickerSend != null) {
                        IconButton(
                            onClick = { showStickers = !showStickers },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEmotions,
                                contentDescription = "Sticker",
                                tint = if (showStickers) Color.Yellow else Color.White,
                            )
                        }
                    }
                    if (voiceRecorder != null) {
                        IconButton(
                            onClick = voiceRecorder.toggle,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Record voice",
                                tint = if (voiceRecorder.isRecording) Color.Red else Color.White,
                            )
                        }
                    }
                }
            } else {
                IconButton(
                    onClick = onSend,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MessageBubble(
    message: MessageDto,
    isFromCurrentUser: Boolean,
    senderName: String,
    senderAvatar: String,
    showAvatar: Boolean = false,
    isFirstInGroup: Boolean = false,
) {
    val bubbleColor = if (isFromCurrentUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val textColor = if (isFromCurrentUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    // Dynamic bubble shape based on position in conversation
    val bubbleShape = RoundedCornerShape(
        topStart = if (!isFromCurrentUser && isFirstInGroup) 20.dp else 16.dp,
        topEnd = if (isFromCurrentUser && isFirstInGroup) 20.dp else 16.dp,
        bottomStart = if (isFromCurrentUser) {
            16.dp
        } else if (showAvatar) {
            20.dp
        } else {
            6.dp
        },
        bottomEnd = if (isFromCurrentUser) if (showAvatar) 20.dp else 6.dp else 16.dp,
    )

    val formattedTime = parseServerTime(message.sendTime)
        ?.format(DateTimeFormatter.ofPattern("HH:mm"))
        ?: "Now"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromCurrentUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        // Avatar space for non-user messages
        if (!isFromCurrentUser) {
            if (showAvatar) {
                // Show actual avatar
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                ) {
                    AsyncImage(
                        model = avatarOrDefault(senderAvatar, senderName),
                        contentDescription = "User avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            } else {
                // Empty space to maintain alignment
                Spacer(modifier = Modifier.width(32.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start,
        ) {
            // Sender name for first message in group (only for non-current user)
            if (!isFromCurrentUser && isFirstInGroup) {
                Text(
                    text = takeFirstNameOfUser(senderName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        start = 12.dp,
                        bottom = 4.dp,
                    ),
                )
            }

            // Kieu hien thi theo messageType (content cua PHOTO/STICKER/VOICE la URL)
            when (message.messageType) {
                // EMOJI: hien to, KHONG bubble (kieu Messenger)
                "EMOJI" -> Text(
                    text = message.content,
                    fontSize = 36.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                // STICKER: anh nho, KHONG bubble
                "STICKER" -> AsyncImage(
                    model = message.content,
                    contentDescription = "Sticker",
                    modifier = Modifier
                        .size(96.dp)
                        .padding(4.dp),
                )

                // PHOTO: anh trong bubble bo goc
                "PHOTO" -> AsyncImage(
                    model = message.content,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(200.dp)
                        .clip(bubbleShape),
                )

                // VOICE: cham de phat/dung
                "VOICE" -> VoiceBubble(
                    url = message.content,
                    bubbleColor = bubbleColor,
                    textColor = textColor,
                    shape = bubbleShape,
                )

                else -> Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(bubbleShape)
                        .background(bubbleColor)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = message.content,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Message time (show only for messages with avatars or every few messages)
            if (showAvatar || isFirstInGroup) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(
                        top = 4.dp,
                    ).padding(horizontal = 16.dp),
                )
            }
        }

        // Space for current user messages alignment
        if (isFromCurrentUser) {
            Spacer(modifier = Modifier.width(40.dp))
        }
    }
}
