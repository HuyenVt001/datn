package com.example.snapget.core.designsystem.preview

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.snapget.core.designsystem.theme.AppTheme
import com.example.snapget.feature.message.ConversationItem
import com.example.snapget.feature.message.ConversationUi

// Du lieu mau cho preview man Messages — KHONG goi MessageScreen truc tiep
// (screen do can hiltViewModel -> preview "Failed to instantiate a ViewModel").
// Dung chung cho AllScreensPreview (cung package).
internal val sampleConversations = listOf(
    ConversationUi(
        counterpartId = "1",
        name = "Jane Smith",
        avatar = "",
        preview = "See you tomorrow!",
        sendTime = "2026-07-16T10:00:00Z",
        unread = true,
    ),
    ConversationUi(
        counterpartId = "2",
        name = "Alex Chen",
        avatar = "",
        preview = "Nice shot!",
        sendTime = "2026-07-15T22:30:00Z",
        unread = false,
    ),
    ConversationUi(
        counterpartId = "3",
        name = "John Doe",
        avatar = "",
        preview = "That frame looks great",
        sendTime = "2026-07-15T08:12:00Z",
        unread = false,
    ),
)

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun MessageScreenPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            sampleConversations.forEach { conversation ->
                ConversationItem(conversation = conversation)
            }
        }
    }
}
