package com.example.snapget.feature.message

import android.os.Build
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.button.ChunkyButton
import com.example.snapget.core.designsystem.component.feedback.rememberMessenger
import com.example.snapget.core.designsystem.component.lottie.SnapAnim
import com.example.snapget.core.designsystem.component.pill.quickReactionEmojis
import com.example.snapget.core.designsystem.component.state.EmptyState
import com.example.snapget.core.designsystem.component.state.ErrorState
import com.example.snapget.core.designsystem.feedback.rememberFeedback
import com.example.snapget.core.designsystem.motion.popOnChange
import com.example.snapget.core.designsystem.skin.SkinIcon
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.designsystem.skin.skinChatWallpaper
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
    val messenger = rememberMessenger()
    val thread by messageViewModel.thread.collectAsState()
    val threadStatus by messageViewModel.threadStatus.collectAsState()
    val friendsById by messageViewModel.friendsById.collectAsState()
    val sendError by messageViewModel.sendError.collectAsState()
    val myUid = messageViewModel.myUid

    val recipient = friendsById[recipientId]
    val recipientName = recipient?.name ?: "Snapget user"
    val recipientAvatar = recipient?.avatar.orEmpty()

    val listState = rememberLazyListState()
    // rememberSaveable: xoay man / app bi kill khong mat chu dang go (2026-11-15)
    var messageText by rememberSaveable { mutableStateOf("") }
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
            messenger.show(it)
            messageViewModel.clearSendError()
        }
    }

    // Scroll to bottom when new messages arrive
    AutoScrollToBottom(listState = listState, itemCount = thread.size)

    Scaffold(
        // Skin v2: nen trong suot -> thay backdrop cua skin (Navigation.kt)
        containerColor = Color.Transparent,
        // imePadding: ban phim mo -> thanh nhap noi len tren ban phim
        // (truoc day ban phim CHE ca o go text)
        // Skin v2: hoa van particle + lop diu nen, ve TRUOC imePadding de phu ca man
        modifier = Modifier.skinChatWallpaper().imePadding(),
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
                    val draft = messageText
                    val draftReply = replyTarget
                    messageViewModel.sendMessage(
                        recipientId,
                        messageText,
                        replyToId = replyTarget?.messageId,
                        // Gui loi -> tra lai chu + reply (neu nguoi dung chua go gi moi)
                        onFailed = {
                            if (messageText.isEmpty()) {
                                messageText = draft
                                replyTarget = draftReply
                            }
                        },
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
                .padding(paddingValues),
        ) {
            when {
                threadStatus is LoadStatus.Loading && thread.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        // Theo theme: hardcode trang la vo hinh o Light mode
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                // Loi tai thread -> Error + Retry (2026-11-15: truoc hien chung voi "trong")
                thread.isEmpty() && threadStatus is LoadStatus.Error -> {
                    ErrorState(
                        message = (threadStatus as LoadStatus.Error).error,
                        onRetry = { messageViewModel.refreshThread(recipientId, showLoading = true) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                // Chat trong -> goi y "Say hi 👋" (bam = gui luon)
                thread.isEmpty() -> {
                    EmptyState(
                        title = "Say hi to ${takeFirstNameOfUser(recipientName)}!",
                        message = "Start the conversation — they'll get a notification.",
                        actionLabel = "Say hi 👋",
                        onAction = { messageViewModel.sendMessage(recipientId, "Hi 👋") },
                        anim = SnapAnim.FRIENDS,
                        modifier = Modifier.align(Alignment.Center),
                    )
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

/**
 * Vung cham nut/emoji trong o nhap chat (2026-11-15). 44dp thay vi 48dp: hang co toi
 * 5 nut (2 emoji + anh + sticker + mic), 48dp se bop o chu con ~60dp tren may 360dp.
 * Thiet ke lai Chat (UI_UPGRADE_PLAN.md) se gom nut media de dat 48dp.
 */
internal val ChatTouchTarget = 44.dp

/**
 * Tu cuon xuong tin moi NHAT — chi khi lan dau tai hoac nguoi dung DANG o gan cuoi.
 * (2026-11-15) Truoc day polling 5s co tin moi la giat ve day du nguoi dung dang keo
 * len doc tin cu.
 */
@Composable
internal fun AutoScrollToBottom(listState: LazyListState, itemCount: Int) {
    var previousCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(itemCount) {
        if (itemCount == 0) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val wasNearBottom = previousCount == 0 || lastVisible >= previousCount - 2
        if (wasNearBottom) listState.animateScrollToItem(itemCount - 1)
        previousCount = itemCount
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
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        // Nut ⋮ "More options" DA XOA (2026-11-15): bam khong lam gi (nut chet)
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
                        shape = SkinTheme.shapes.input,
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
                    IconButton(onClick = onCancelReply, modifier = Modifier.size(ChatTouchTarget)) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
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
                        shape = SkinTheme.shapes.image,
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
                    shape = SkinTheme.shapes.sheet,
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
                                    SkinTheme.colors.error
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
                        // Vung cham 44dp (truoc ~28dp) — xem ChatTouchTarget
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(ChatTouchTarget)
                                .clip(CircleShape)
                                .clickable(onClickLabel = "Send $emoji") { onEmojiSend(emoji) },
                        ) {
                            Text(text = emoji, style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    if (onPhotoClick != null) {
                        IconButton(onClick = onPhotoClick, modifier = Modifier.size(ChatTouchTarget)) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = "Send photo",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (onStickerSend != null) {
                        IconButton(
                            onClick = { showStickers = !showStickers },
                            modifier = Modifier.size(ChatTouchTarget),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EmojiEmotions,
                                contentDescription = "Sticker",
                                tint = if (showStickers) {
                                    SkinTheme.colors.accent
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                    if (voiceRecorder != null) {
                        IconButton(
                            onClick = voiceRecorder.toggle,
                            modifier = Modifier.size(ChatTouchTarget),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = "Record voice",
                                tint = if (voiceRecorder.isRecording) {
                                    SkinTheme.colors.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            } else {
                // Nut gui TRON CHUNKY nen accent (Chat redesign 2026-11-15) — "pop" khi
                // vua xuat hien (bat dau go) + haptic khi bam
                val sendFeedback = rememberFeedback()
                ChunkyButton(
                    onClick = {
                        sendFeedback.tap()
                        onSend()
                    },
                    height = 40.dp,
                    depth = 3.dp,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .width(44.dp)
                        .popOnChange(true)
                        .semantics { contentDescription = "Send message" },
                ) {
                    SkinIcon(
                        res = SkinTheme.icons.send,
                        fallback = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = null,
                        tint = SkinTheme.colors.onAccent,
                        modifier = Modifier.size(20.dp),
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
                            .clip(SkinTheme.shapes.input)
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
                            .clip(SkinTheme.shapes.input)
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
                        .clip(SkinTheme.shapes.input)
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
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = "Play video",
                                tint = SkinTheme.colors.textPrimary,
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
                        // DESIGN.md muc 3: bubble = bodyLarge 16sp (truoc 14sp)
                        style = MaterialTheme.typography.bodyLarge,
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
                    // Bo alpha 0.6 (contrast ~3.8:1 < AA) -> textSecondary ~8.6:1
                    color = SkinTheme.colors.textSecondary,
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
        shape = SkinTheme.shapes.image,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            if (onReactClick != null) {
                IconButton(onClick = onReactClick) {
                    Icon(
                        imageVector = Icons.Outlined.EmojiEmotions,
                        contentDescription = "React to message",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (onReplyClick != null) {
                IconButton(onClick = onReplyClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Reply,
                        contentDescription = "Reply to message",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
