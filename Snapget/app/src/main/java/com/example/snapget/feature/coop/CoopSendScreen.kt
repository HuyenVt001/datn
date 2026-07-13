package com.example.snapget.feature.coop

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.topbar.SimpleTopBar
import com.example.snapget.core.designsystem.theme.SnapYellow
import com.example.snapget.core.model.FriendUi
import com.example.snapget.feature.friends.FriendsViewModel
import com.example.snapget.navigation.Screen
import java.io.File

/**
 * Man gui loi moi CHUP CHUNG: xem nua anh vua chup + chon 1 nguoi ban -> gui.
 * Nguoi ban nhan FCM, chap nhan va chup nua con lai -> server ghep thanh moment chung.
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CoopSendScreen(
    navController: NavController,
    photoPath: String,
    coopViewModel: CoopViewModel = hiltViewModel(),
    friendsViewModel: FriendsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val friends by friendsViewModel.friends.collectAsState()
    val sendStatus by coopViewModel.sendStatus.collectAsState()
    var selectedFriend by remember { mutableStateOf<FriendUi?>(null) }

    LaunchedEffect(Unit) { friendsViewModel.loadFriends() }

    LaunchedEffect(sendStatus) {
        when (val status = sendStatus) {
            is LoadStatus.Success -> {
                Toast.makeText(context, "Da gui loi moi chup chung!", Toast.LENGTH_SHORT).show()
                coopViewModel.resetSendStatus()
                navController.navigate(Screen.Post.route) {
                    popUpTo(Screen.Post.route) { inclusive = true }
                }
            }
            is LoadStatus.Error -> {
                Toast.makeText(context, status.error, Toast.LENGTH_LONG).show()
                coopViewModel.resetSendStatus()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            SimpleTopBar(title = "Co-op capture", onBackClick = { navController.popBackStack() })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // Nua anh cua minh (se nam ben TRAI anh ghep)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                Row(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))) {
                    AsyncImage(
                        model = File(photoPath),
                        contentDescription = "Nua anh cua ban",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                    // Nua con lai: cho ban be chup
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(Color(0xFF2C2C2C)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "?", color = Color(0xFFB0B0B0), fontSize = 64.sp)
                    }
                }
                if (sendStatus is LoadStatus.Loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Choose a friend to finish the other half",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (friends.isEmpty()) {
                Text(
                    text = "Chua co ban be — hay ket ban truoc da!",
                    color = Color(0xFFB0B0B0),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(friends) { friend ->
                    val selected = selectedFriend?.id == friend.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) SnapYellow else Color.Transparent,
                                shape = RoundedCornerShape(16.dp),
                            )
                            .clickable { selectedFriend = friend }
                            .padding(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF404137)),
                        ) {
                            if (friend.avatar.isNotEmpty()) {
                                AsyncImage(
                                    model = friend.avatar,
                                    contentDescription = friend.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = friend.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (friend.streak > 0) {
                            Text(
                                text = "🔥 ${friend.streak}",
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Nut gui 80dp vien vang (giong nut Send cua submit)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (selectedFriend != null) {
                        Color.White.copy(alpha = 0.9f)
                    } else {
                        Color(0xFF333333)
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .border(3.dp, SnapYellow, CircleShape),
                ) {
                    IconButton(
                        onClick = {
                            val friend = selectedFriend ?: return@IconButton
                            coopViewModel.sendInvite(File(photoPath), friend.id)
                        },
                        enabled = selectedFriend != null && sendStatus !is LoadStatus.Loading,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Gui loi moi",
                            tint = if (selectedFriend != null) Color.Black else Color.Gray,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
    }
}
