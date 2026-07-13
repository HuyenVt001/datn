package com.example.snapget.feature.coop

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.snapget.core.common.LoadStatus
import com.example.snapget.core.designsystem.component.topbar.SimpleTopBar
import com.example.snapget.core.designsystem.preview.CameraPreviewWithZoom
import com.example.snapget.core.designsystem.theme.SnapYellow
import com.example.snapget.navigation.Screen
import java.io.File

/**
 * Man CHAP NHAN chup chung: trai = nua anh nguoi moi, phai = camera/anh cua minh.
 * Chup xong bam ✓ -> upload + accept -> server ghep 2 anh thanh moment chung cho ca 2.
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CoopAcceptScreen(
    navController: NavController,
    inviteId: String,
    inviterMediaUrl: String,
    inviterName: String,
    coopViewModel: CoopViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val acceptStatus by coopViewModel.acceptStatus.collectAsState()

    // Nua anh cua minh sau khi chup (null = dang o che do camera)
    var myPhotoPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(acceptStatus) {
        when (val status = acceptStatus) {
            is LoadStatus.Success -> {
                Toast.makeText(context, "Da ghep anh — khoanh khac chung da len feed!", Toast.LENGTH_SHORT).show()
                coopViewModel.resetAcceptStatus()
                navController.navigate(Screen.Post.route) {
                    popUpTo(Screen.Post.route) { inclusive = true }
                }
            }
            is LoadStatus.Error -> {
                Toast.makeText(context, status.error, Toast.LENGTH_LONG).show()
                coopViewModel.resetAcceptStatus()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            SimpleTopBar(title = "Co-op with $inviterName", onBackClick = { navController.popBackStack() })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Preview split: trai = nguoi moi, phai = minh (camera hoac anh da chup)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = inviterMediaUrl,
                        contentDescription = "Nua anh cua $inviterName",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        val currentPath = myPhotoPath
                        if (currentPath != null) {
                            AsyncImage(
                                model = File(currentPath),
                                contentDescription = "Nua anh cua ban",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            // Camera chup nua cua minh (chi anh, khong video)
                            CameraPreviewWithZoom(
                                lifecycleOwner = lifecycleOwner,
                                height = 400.dp,
                                onPhotoTaken = { path -> myPhotoPath = path },
                                showControls = false,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                if (acceptStatus is LoadStatus.Loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Dang ghep anh...", color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (myPhotoPath == null) {
                    "Chup nua con lai cua khoanh khac ✨"
                } else {
                    "Ung y chua? Bam ✓ de ghep va dang!"
                },
                color = Color(0xFFB0B0B0),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Hang nut: Tu choi ✕ · Gui ✓ (80dp vien vang) · Chup lai ↺
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        coopViewModel.declineInvite(inviteId)
                        Toast.makeText(context, "Da tu choi loi moi.", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Tu choi",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (myPhotoPath != null) Color.White.copy(alpha = 0.9f) else Color(0xFF333333),
                    modifier = Modifier
                        .size(80.dp)
                        .padding(0.dp),
                    border = androidx.compose.foundation.BorderStroke(3.dp, SnapYellow),
                ) {
                    IconButton(
                        onClick = {
                            val path = myPhotoPath ?: return@IconButton
                            coopViewModel.acceptInvite(inviteId, File(path))
                        },
                        enabled = myPhotoPath != null && acceptStatus !is LoadStatus.Loading,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Ghep va dang",
                            tint = if (myPhotoPath != null) Color.Black else Color.Gray,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                IconButton(
                    onClick = { myPhotoPath = null },
                    enabled = myPhotoPath != null,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Chup lai",
                        tint = if (myPhotoPath != null) Color.White else Color.Gray,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
