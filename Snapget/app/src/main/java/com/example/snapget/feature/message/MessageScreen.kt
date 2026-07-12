package com.example.snapget.feature.message

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.data.SampleData
import com.example.snapget.core.designsystem.component.circle.Circle
import com.example.snapget.core.designsystem.component.circle.ImageSetting
import com.example.snapget.core.designsystem.component.common.CommonTopBar
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Danh sach hoi thoai 1-1 — doc tu API /messages/conversations (da migrate
 * khoi Firestore 2026-07-12). Avatar VIEN VANG khi co tin chua doc (DESIGN 7.8).
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MessageScreen(
    navController: NavHostController,
    messageViewModel: MessageViewModel = hiltViewModel(),
) {
    val conversations by messageViewModel.conversations.collectAsState()
    val status by messageViewModel.conversationsStatus.collectAsState()

    // Refresh moi lan mo man (khong realtime — REST thuan)
    LaunchedEffect(Unit) {
        messageViewModel.loadConversations()
    }

    Scaffold(
        topBar = {
            CommonTopBar(
                navController = navController,
                title = "Messages",
                titleColor = Color.White,
                startIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onStartIconClick = { navController.popBackStack() },
                endIcon = Icons.Default.Search,
                onEndIconClick = {
                    Log.d("MessageScreen", "Search icon clicked")
                },
            )
        },
    ) { paddingValues ->
        when {
            status is LoadStatus.Loading && conversations.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            conversations.isEmpty() -> {
                // Empty state when no conversations are available
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "No messages",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No messages yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (status is LoadStatus.Error) {
                            (status as LoadStatus.Error).error
                        } else {
                            "Your conversations will appear here"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                // Show list of conversations
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(conversations) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            onClick = {
                                navController.navigate("chat/${conversation.counterpartId}")
                            },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationItem(
    conversation: ConversationUi,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar — vien VANG khi co tin chua doc (DESIGN 7.8)
            Circle(
                imageSetting = ImageSetting(
                    imageUrl = conversation.avatar.ifEmpty { SampleData.IMAGE_NOT_AVAILABLE },
                    contentDescription = "Profile picture",
                ),
                gap = if (conversation.unread) 3.dp else 0.dp,
                outerSize = 50.dp,
                backgroundColor = Color(0xFF404137),
                borderColor = if (conversation.unread) Color.Yellow else Color(0xFF404137),
                onClick = onClick,
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Message content
            Column(
                modifier = Modifier.weight(1f),
            ) {
                // Sender name and time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = conversation.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Text(
                        text = formatTime(conversation.sendTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Message preview
                Text(
                    text = conversation.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (conversation.unread) Color.White else Color.Gray,
                    fontWeight = if (conversation.unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Arrow indicator
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Open conversation",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/**
 * Parse thoi gian server (ISO UTC dang ...Z) ve gio may; fallback
 * LocalDateTime thuan cho du lieu cu.
 */
@RequiresApi(Build.VERSION_CODES.O)
internal fun parseServerTime(iso: String): LocalDateTime? = try {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDateTime()
} catch (_: Exception) {
    try {
        LocalDateTime.parse(iso)
    } catch (_: Exception) {
        null
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun formatTime(timeString: String): String {
    val time = parseServerTime(timeString) ?: return "Now"
    val now = LocalDateTime.now()

    return when {
        time.toLocalDate() == now.toLocalDate() -> {
            time.format(DateTimeFormatter.ofPattern("HH:mm"))
        }

        time.toLocalDate() == now.toLocalDate().minusDays(1) -> {
            "Yesterday"
        }

        time.year == now.year -> {
            time.format(DateTimeFormatter.ofPattern("MMM d"))
        }

        else -> {
            time.format(DateTimeFormatter.ofPattern("yyyy MMM d"))
        }
    }
}
