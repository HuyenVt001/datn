package com.example.snapget.core.designsystem.component.collectible

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.snapget.core.designsystem.skin.SkinTheme

/** Trang thai 1 o suu tap — quyet dinh vien, do mo va nhan hien thi. */
enum class CollectibleState {
    /** Da so huu va DANG dung -> vien accent + nhan "In use". */
    IN_USE,

    /** Da so huu, chua dung. */
    OWNED,

    /** Chua so huu -> mo di + 🔒. */
    LOCKED,
}

/**
 * O trong luoi suu tap — dung CHUNG cho ca 3 tab cua man Appearance
 * (SKIN_PLAN.md muc 4.2). Khac nhau giua cac tab chi la **ti le o** va **so cot**:
 * Frames 3 cot o 1:1 · Skins 2 cot o 9:16 · Effects 2 cot o 1:1.
 *
 * Phan hinh anh do caller ve qua [preview] — nho vay tab Effects nhet duoc o
 * demo CHAY THAT vao day thay vi anh tinh, ma khong phai viet component rieng.
 *
 * @param locked o chua so huu. VAN bam duoc (de xem thu) — nguoi goi tu quyet
 *   dinh co ap dung hay khong; xem [CollectibleState].
 */
@Composable
fun CollectibleItem(
    name: String,
    state: CollectibleState,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    preview: @Composable () -> Unit,
) {
    val locked = state == CollectibleState.LOCKED
    val inUse = state == CollectibleState.IN_USE

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(SkinTheme.shapes.input)
                .background(SkinTheme.colors.surfaceVariant)
                .then(
                    if (inUse) {
                        Modifier.border(3.dp, SkinTheme.colors.accent, SkinTheme.shapes.input)
                    } else {
                        Modifier
                    },
                )
                .clickable { onClick() },
        ) {
            Box(modifier = Modifier.fillMaxSize().alpha(if (locked) 0.35f else 1f)) {
                preview()
            }

            if (locked) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "🔒", fontSize = 22.sp)
                }
            }

            if (inUse) {
                Text(
                    text = "In use",
                    color = SkinTheme.colors.onAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(SkinTheme.shapes.pill)
                        .background(SkinTheme.colors.accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Box(modifier = Modifier.height(4.dp))

        Text(
            text = name,
            color = if (locked) SkinTheme.colors.textSecondary else SkinTheme.colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (inUse) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
