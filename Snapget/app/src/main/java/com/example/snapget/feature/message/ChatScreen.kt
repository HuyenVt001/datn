package com.example.snapget.feature.message

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

    // Xem media full-screen (bam anh trong chat) — Pair(url, isVideo)
    var viewerMedia by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    // Tin nhan dang duoc long-press -> menu reaction + Reply
    var actionTarget by remember { mutableStateOf<MessageDto?>(null) }

    // Tin dang CHON sau khi nhan giu -> icon ↩ hien ben canh bubble (cham tin de bo chon)
    var selectedMessageId by remember { mutableStateOf<String?>(null) }

    // Tin nhan dang duoc TRICH DAN de reply (thanh "Replying to" tren o nhap)
    var replyTarget by remember { mutableStateOf<MessageDto?>(null) }

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
        // imePadding: ban phim mo -> thanh nhap noi len tren ban phim
        // (truoc day ban phim CHE ca o go text)
        modifier = Modifier.imePadding(),
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
                    messageViewModel.sendMessage(
                        recipientId,
                        messageText,
                        replyToId = replyTarget?.messageId,
                    )
                    messageText = ""
                    replyTarget = null
                },
                onEmojiSend = { emoji ->
                    messageViewModel.sendMessage(
                        recipientId,
                        emoji,
                        messageType = "EMOJI",
                        replyToId = replyTarget?.messageId,
                    )
                    replyTarget = null
                },
                modifier = Modifier.padding(bottom = 15.dp),
                onPhotoClick = { photoPicker.launch("image/*") },
                onStickerSend = { url ->
                    messageViewModel.sendMessage(recipientId, url, messageType = "STICKER")
                },
                voiceRecorder = voiceRecorder,
                isSendingMedia = sendingMedia,
                replyingTo = replyTarget,
                replyingToName = if (replyTarget?.senderId == myUid) {
                    "yourself"
                } else {
                    takeFirstNameOfUser(recipientName)
                },
                onCancelReply = { replyTarget = null },
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
                        // Theo theme: hardcode trang la vo hinh o Light mode
                        color = MaterialTheme.colorScheme.onBackground,
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
                                    onMediaClick = { url, isVideo -> viewerMedia = url to isVideo },
                                    // Nhan giu -> hien hang icon 😊|↩ ben canh tin
                                    onLongPress = { selectedMessageId = message.messageId },
                                    isSelected = selectedMessageId == message.messageId,
                                    onSelect = { selectedMessageId = null },
                                    onReactClick = { actionTarget = message },
                                    onReplyClick = {
                                        replyTarget = message
                                        selectedMessageId = null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Xem anh/video full-screen (khong bi crop vuong nhu trong bubble)
    viewerMedia?.let { (url, isVideo) ->
        MediaViewerDialog(url = url, isVideo = isVideo, onDismiss = { viewerMedia = null })
    }

    // Bam 😊 tren hang icon -> bang emoji tha reaction (tha lai cung emoji = go)
    actionTarget?.let { message ->
        ReactionPickerDialog(
            onPick = { emoji ->
                messageViewModel.reactToMessage(message.messageId, emoji)
                actionTarget = null
                selectedMessageId = null
            },
            onDismiss = { actionTarget = null },
        )
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
 * [replyingTo] != null -> hien thanh "Replying to X" (preview tin goc + nut ✕ huy)
 * ngay tren o nhap; tin gui di se mang replyToId (man chat tu xu ly).
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
    replyingTo: MessageDto? = null,
    replyingToName: String = "",
    onCancelReply: (() -> Unit)? = null,
) {
    var showStickers by remember { mutableStateOf(false) }
    val hasMediaButtons = onPhotoClick != null || onStickerSend != null || voiceRecorder != null

    Column(modifier = modifier) {
        // Thanh dang-reply (kieu Messenger): ten nguoi + preview tin goc + nut huy
        if (replyingTo != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                if (replyingTo.messageType == "PHOTO" || replyingTo.messageType == "STICKER") {
                    AsyncImage(
                        model = replyingTo.content,
                        contentDescription = "Replied media",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Replying to $replyingToName",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when (replyingTo.messageType) {
                            "PHOTO" -> "Photo"
                            "STICKER" -> "Sticker"
                            "VOICE" -> "Voice message"
                            else -> replyingTo.content
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onCancelReply != null) {
                    IconButton(onClick = onCancelReply, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel reply",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
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
                    // Theo theme: chu trang tren nen surfaceVariant sang = vo hinh
                    color = MaterialTheme.colorScheme.onSurface,
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
                        color = MaterialTheme.colorScheme.onSurface,
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
                                tint = MaterialTheme.colorScheme.onSurface,
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
                                tint = if (showStickers) {
                                    Color.Yellow
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
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
                                tint = if (voiceRecorder.isRecording) {
                                    Color.Red
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
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
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MessageBubble(
    message: MessageDto,
    isFromCurrentUser: Boolean,
    senderName: String,
    senderAvatar: String,
    showAvatar: Boolean = false,
    isFirstInGroup: Boolean = false,
    // Bam anh/video (bubble PHOTO hoac attachment) -> xem full-screen
    onMediaClick: ((url: String, isVideo: Boolean) -> Unit)? = null,
    // Long-press tin nhan -> menu reaction + Reply
    onLongPress: (() -> Unit)? = null,
    // NHAN GIU tin nhan -> isSelected = true -> hang icon 😊|↩ hien BEN CANH bubble
    // (khop anh mau Messenger cua user); cham tin = bo chon
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    // Bam 😊 tren hang icon -> mo bang emoji tha reaction
    onReactClick: (() -> Unit)? = null,
    // Bam ↩ tren hang icon -> bat dau reply tin nay
    onReplyClick: (() -> Unit)? = null,
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
        // Tin cua MINH: hang icon 😊|↩ nam BEN TRAI bubble (phia ngoai)
        if (isFromCurrentUser && isSelected) {
            MessageActionRow(
                onReactClick = onReactClick,
                onReplyClick = onReplyClick,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 8.dp),
            )
        }

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

            // Khoi TRICH DAN khi tin nay reply 1 tin khac (kieu Messenger):
            // tin goc mo/nho phia tren, bubble that duoc keo de nhe len duoi
            if (message.replyToId != null) {
                val quoteType = message.replyToType ?: "TEXT"
                when (quoteType) {
                    // offset y+6: quote truot xuong duoi bubble (ve truoc = nam duoi)
                    // -> bubble that de nhe len, dung kieu overlap cua Messenger
                    "PHOTO", "STICKER" -> AsyncImage(
                        model = message.replyToContent,
                        contentDescription = "Replied media",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .offset(y = 6.dp)
                            .size(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .alpha(0.75f)
                            .combinedClickable(
                                onClick = {
                                    message.replyToContent?.let { onMediaClick?.invoke(it, false) }
                                },
                                onLongClick = onLongPress,
                            ),
                    )

                    else -> Text(
                        text = when (quoteType) {
                            "VOICE" -> "🎤 Voice message"
                            else -> message.replyToContent.orEmpty()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .offset(y = 6.dp)
                            .widthIn(max = 260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .padding(bottom = 6.dp),
                    )
                }
            }

            // Media dinh kem (tin reply bai dang): anh/poster video tren bubble text,
            // bam de xem full-screen, video hien nut ▶ (khop anh mau reply)
            message.attachmentUrl?.let { attachUrl ->
                val attachIsVideo = message.attachmentType == "VIDEO"
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClick = { onMediaClick?.invoke(attachUrl, attachIsVideo) },
                            onLongClick = onLongPress,
                        ),
                ) {
                    AsyncImage(
                        // Video Cloudinary: doi duoi .jpg lay poster frame (nhu feed)
                        model = if (attachIsVideo) {
                            attachUrl.substringBeforeLast('.') + ".jpg"
                        } else {
                            attachUrl
                        },
                        contentDescription = "Post media",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (attachIsVideo) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play video",
                                tint = Color.White,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Kieu hien thi theo messageType (content cua PHOTO/STICKER/VOICE la URL)
            when (message.messageType) {
                // EMOJI: hien to, KHONG bubble (kieu Messenger)
                "EMOJI" -> Text(
                    text = message.content,
                    fontSize = 36.sp,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .combinedClickable(
                            onClick = { onSelect?.invoke() },
                            onLongClick = onLongPress,
                        ),
                )

                // STICKER: anh nho, KHONG bubble
                "STICKER" -> AsyncImage(
                    model = message.content,
                    contentDescription = "Sticker",
                    modifier = Modifier
                        .size(96.dp)
                        .padding(4.dp)
                        .combinedClickable(
                            onClick = { onSelect?.invoke() },
                            onLongClick = onLongPress,
                        ),
                )

                // PHOTO: anh trong bubble bo goc — BAM de xem day du (khong crop)
                "PHOTO" -> AsyncImage(
                    model = message.content,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(200.dp)
                        .clip(bubbleShape)
                        .combinedClickable(
                            onClick = { onMediaClick?.invoke(message.content, false) },
                            onLongClick = onLongPress,
                        ),
                )

                // VOICE: cham de phat/dung
                "VOICE" -> VoiceBubble(
                    url = message.content,
                    bubbleColor = bubbleColor,
                    textColor = textColor,
                    shape = bubbleShape,
                    onLongPress = onLongPress,
                )

                else -> Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(bubbleShape)
                        .background(bubbleColor)
                        .combinedClickable(
                            onClick = { onSelect?.invoke() },
                            onLongClick = onLongPress,
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = message.content,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Reaction da tha (gom theo emoji + so luong) — bam de doi/go cua minh
            MessageReactionsRow(
                reactions = message.reactions,
                onClick = { onLongPress?.invoke() },
                modifier = Modifier.padding(top = 2.dp),
            )

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

        // Tin cua NGUOI KHAC: hang icon 😊|↩ nam BEN PHAI bubble (phia ngoai)
        if (!isFromCurrentUser && isSelected) {
            MessageActionRow(
                onReactClick = onReactClick,
                onReplyClick = onReplyClick,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 8.dp),
            )
        }

        // (Spacer 40dp ben phai tin cua minh DA XOA 2026-07-27 — gay khoang trong
        // vo nghia giua bubble va mep phai man hinh)
    }
}

/**
 * Hang icon hien ben canh bubble sau khi NHAN GIU tin nhan (khop anh mau
 * Messenger cua user): 😊 mo bang emoji tha reaction · ↩ bat dau reply.
 * Pill nen surfaceVariant bo tron nhu Messenger.
 */
@Composable
private fun MessageActionRow(
    onReactClick: (() -> Unit)?,
    onReplyClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            if (onReactClick != null) {
                IconButton(onClick = onReactClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.EmojiEmotions,
                        contentDescription = "React to message",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (onReplyClick != null) {
                IconButton(onClick = onReplyClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Reply to message",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
