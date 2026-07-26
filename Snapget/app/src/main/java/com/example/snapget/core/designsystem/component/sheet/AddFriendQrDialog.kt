package com.example.snapget.core.designsystem.component.sheet

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.snapget.core.designsystem.theme.GrayOnSurfaceVariant
import com.example.snapget.core.designsystem.theme.GraySurface
import com.example.snapget.core.util.generateQrBitmap

/**
 * Dialog ket ban bang QR (mo tu pill "Add new friend" trong sheet ban be):
 * hien QR chua ma moi cua MINH de ban quet, hoac bam nut de di quet QR cua ban.
 * Style theo DESIGN.md: surface toi bo 20dp, nut chinh pill trang chu den.
 */
@Composable
fun AddFriendQrDialog(
    inviteCode: String?,
    inviteLink: String?,
    expiresAt: String? = null,
    onScanClick: () -> Unit,
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
                Text(
                    text = "Add new friend",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Let your friend scan this code to connect",
                    color = GrayOnSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // QR den tren the trang bo goc — giu tuong phan de quet chac an
                if (inviteLink != null) {
                    val qrBitmap = remember(inviteLink) { generateQrBitmap(inviteLink) }
                    if (qrBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Friend invite QR code",
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(200.dp),
                            )
                        }
                    } else {
                        Text(
                            text = "Couldn't generate QR code",
                            color = GrayOnSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }

                    inviteCode?.let { code ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = code,
                            color = GrayOnSurfaceVariant,
                            fontSize = 14.sp,
                            letterSpacing = 2.sp,
                        )
                    }

                    // Link/ma co han 30 ngay — het han server tu sinh ma moi
                    formatInviteExpiry(expiresAt)?.let { expiry ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Valid until $expiry",
                            color = GrayOnSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Loading invite code...",
                        color = GrayOnSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Divider "hoặc" — cùng kiểu divider "OR" của màn login
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Color.White.copy(alpha = 0.2f),
                    )
                    Text(
                        text = "or",
                        color = GrayOnSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Color.White.copy(alpha = 0.2f),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onScanClick,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scan a friend's QR",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
