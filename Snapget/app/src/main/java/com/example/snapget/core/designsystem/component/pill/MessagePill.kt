package com.example.snapget.core.designsystem.component.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 3 emoji nhanh tren thanh (khop anh mau 2026-07-26) + bo mo rong khi bam ⊕. */
val quickReactionEmojis = listOf("💛", "😂", "💕")
val extraReactionEmojis = listOf("😄", "❤️", "🔥", "👍", "🎉", "😮", "😢", "💯")

/**
 * Thanh "Send message…" duoi post detail (asset: post_detail_screen.png).
 * - [onEmojiClick]: emoji tha REACTION (server cong friend streak); ⊕ xoe hang mo rong.
 * - [onSendMessage]: bat o GO TEXT that — go noi dung roi bam Send de nhan tin
 *   1-1 toi tac gia bai (co text thi emoji nhuong cho cho nut Send).
 * Khong truyen gi -> thanh tinh nhu cu.
 */
@Composable
fun MessageInputPill(
    modifier: Modifier = Modifier,
    onEmojiClick: ((String) -> Unit)? = null,
    selectedEmoji: String? = null,
    onSendMessage: ((String) -> Unit)? = null,
) {
    // Hang emoji mo rong (chi bat duoc khi co onEmojiClick)
    var expanded by remember { mutableStateOf(false) }

    // Noi dung dang go (chi dung khi onSendMessage != null)
    var messageText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(15.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onSendMessage != null) {
                // O nhap that: placeholder xam khi rong, chu trang khi go
                BasicTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box {
                            if (messageText.isEmpty()) {
                                Text(
                                    text = "Send message...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            } else {
                Text(
                    text = "Send message...",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (onSendMessage != null && messageText.isNotBlank()) {
                // Dang go -> emoji nhuong cho cho nut Send (giong ChatInputPill)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                        .padding(4.dp),
                )
            } else {
                quickReactionEmojis.forEach { emoji ->
                    ReactionEmoji(
                        emoji = emoji,
                        isSelected = selectedEmoji == emoji,
                        onClick = onEmojiClick,
                    )
                }

                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .then(
                            if (onEmojiClick != null) {
                                Modifier.clickable { expanded = !expanded }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }

        if (expanded && onEmojiClick != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                extraReactionEmojis.forEach { emoji ->
                    ReactionEmoji(
                        emoji = emoji,
                        isSelected = selectedEmoji == emoji,
                        onClick = { e ->
                            onEmojiClick(e)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** 1 emoji tha duoc: highlight nen vang mo khi la emoji minh vua tha. */
@Composable
private fun ReactionEmoji(
    emoji: String,
    isSelected: Boolean,
    onClick: ((String) -> Unit)?,
) {
    Text(
        text = emoji,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) {
                    Modifier.background(Color.Yellow.copy(alpha = 0.25f), CircleShape)
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick(emoji) }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Preview(showBackground = true)
@Composable
fun MessageInputPillPreview() {
    MaterialTheme {
        MessageInputPill()
    }
}

@Preview(showBackground = true)
@Composable
fun BottomPreview() {
    MaterialTheme {
        Column {
            MessageInputPill(onEmojiClick = {}, selectedEmoji = "❤️")
        }
    }
}
