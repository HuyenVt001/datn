package com.example.snapget.core.designsystem.component.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.example.snapget.core.designsystem.theme.GrayOnSurfaceVariant
import com.example.snapget.core.designsystem.theme.GraySurface
import com.example.snapget.core.network.dto.InviteInfoDto
import com.example.snapget.core.util.avatarOrDefault

/**
 * Dialog XAC NHAN GUI LOI MOI ket ban (mo tu deep link hoac quet QR): hien
 * avatar + ten chu link (GET /friendships/invite-info) + han link; bam
 * "Gui loi moi" -> POST /connect tao loi moi PENDING — CHU LINK phai chap nhan
 * (trong sheet ban be cua ho) thi 2 nguoi moi thanh ban (chot 2026-07-19).
 * Style theo DESIGN.md: surface toi bo 20dp, nut chinh pill trang chu den.
 *
 * @param info null (va [error] null) = dang tai thong tin nguoi moi.
 * @param error message loi tieng Viet cua server (ma sai / link het han 30 ngay).
 */
@Composable
fun InviteConfirmDialog(
    info: InviteInfoDto?,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = GraySurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    error != null -> {
                        Text(
                            text = "Couldn't connect",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = error,
                            color = GrayOnSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Text(text = "Close", fontWeight = FontWeight.Bold)
                        }
                    }

                    info == null -> {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Reading invite...",
                            color = GrayOnSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }

                    else -> {
                        AsyncImage(
                            model = avatarOrDefault(info.avatar, info.fullName ?: "Snapget user"),
                            contentDescription = "Inviter's avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Add ${info.fullName ?: "Snapget user"} as a friend?",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Once ${info.fullName ?: "your friend"} confirms, you'll see each other's moments and start a friend streak together.",
                            color = GrayOnSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                        formatInviteExpiry(info.expiresAt)?.let { expiry ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Invite valid until $expiry",
                                color = GrayOnSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onConfirm,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Text(
                                text = "Send friend invite",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = onDismiss) {
                            Text(text = "Cancel", color = GrayOnSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Doi ISO string ("2026-08-18T07:00:00.000Z") -> "18/08/2026" bang cat chuoi
 * thuan tuy — tranh java.time vi minSdk 24 (LocalDate can API 26).
 */
fun formatInviteExpiry(iso: String?): String? {
    val datePart = iso?.take(10)?.split("-") ?: return null
    if (datePart.size != 3) return null
    val (year, month, day) = datePart
    return "$day/$month/$year"
}
