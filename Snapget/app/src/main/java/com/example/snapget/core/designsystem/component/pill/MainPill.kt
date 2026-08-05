package com.example.snapget.core.designsystem.component.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.snapget.core.designsystem.component.topbar.avatarWidth
import com.example.snapget.core.designsystem.skin.SkinTheme
import com.example.snapget.core.util.trimUsername

// ⚠️ `Color.White` trong file nay la CO Y, KHONG doi sang token skin:
// chu/icon o day nam de len ANH hoac CAMERA cua nguoi dung nen phai trang
// that o MOI skin. Doi theo `SkinTheme.colors.textPrimary` thi skin nen sang
// se lam chung chim vao anh. Mau cua NEN app trong file nay van dung token.

@Composable
fun PillIcon(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(avatarWidth)
            .clip(SkinTheme.shapes.pill)
            .background(SkinTheme.colors.pill),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
fun Pill(
    data: Any,
    startIcon: @Composable (() -> Unit)? = null,
    endIcon: @Composable (() -> Unit)? = null,
    gap: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    var width: Int = 100

    Box(
        modifier = modifier
            .height(avatarWidth)
            .wrapContentWidth()
            .background(
                color = SkinTheme.colors.pillTranslucent,
                shape = SkinTheme.shapes.pill,
            )
            .clip(SkinTheme.shapes.pill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = modifier
                .padding(horizontal = 3.dp)
                .onGloballyPositioned { coordinates ->
                    // Get the width of the title in pixels
                    width = coordinates.size.width
                },
        ) {
            if (startIcon != null) {
                startIcon()
            }

            Text(
                text = trimUsername(data.toString()) ?: "Everyone",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleMedium,
            )

            if (endIcon != null) {
                endIcon()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PillPreview() {
    Scaffold(
        modifier = Modifier.padding(16.dp),

    ) { paddingValues ->
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .padding(paddingValues)
                .background(SkinTheme.colors.surface) // Dark background for the column
                .padding(16.dp),
        ) {
            Pill(data = "Sample Data", endIcon = {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = "Dropdown",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            })

            Pill(startIcon = {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = "Dropdown",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }, data = "Sample Data")

            Pill(startIcon = {
                Icon(
                    imageVector = Icons.Filled.Group,
                    contentDescription = "Dropdown",
                    tint = Color.White,
                )
            }, data = "45 Friends")
        }
    }
}
