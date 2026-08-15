package com.example.snapget.core.designsystem.component.circle

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.snapget.R
import com.example.snapget.core.designsystem.skin.SkinIcon
import com.example.snapget.core.designsystem.skin.SkinTheme

data class IconSetting(
    val icon: ImageVector,
    /**
     * `null` = theo token `textPrimary` cua skin dang dung (mac dinh).
     *
     * Phai la nullable chu khong dat thang `SkinTheme.colors.textPrimary` lam
     * gia tri mac dinh: day la data class thuong, khong phai ham `@Composable`
     * nen doc CompositionLocal o day khong duoc.
     *
     * CHI ap cho [icon] Material; [skinRes] luon ve nguyen mau goc.
     */
    val tint: Color? = null,
    val contentDescription: String? = null,
    /**
     * Icon RIENG cua skin dang dung (`SkinTheme.icons.xxx`) — co thi ve thay
     * [icon]. `null` (skin chua ve icon nay) -> roi ve [icon] Material.
     */
    @DrawableRes val skinRes: Int? = null,
)

sealed class ImageSource {
    data class Url(val value: String? = "https://images.unsplash.com/photo-1710987812255-f8aaa57b96eb?q=80&w=1170&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D") : ImageSource()
    data class Resource(val resId: Int) : ImageSource()
}

data class ImageSetting(
    val imageUrl: String? = "https://images.unsplash.com/photo-1710987812255-f8aaa57b96eb?q=80&w=1170&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
    val contentDescription: String? = null,
)

@Composable
fun Circle(
    outerSize: Dp = 56.dp,
    gap: Dp = 5.dp,
    backgroundColor: Color = Color.Gray,
    borderWidth: Dp = 2.dp,
    borderColor: Color = SkinTheme.colors.accent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSetting: IconSetting? = null,
    imageSetting: ImageSetting? = null,
    innerContent: (@Composable () -> Unit)? = null,
    // GIU nut (long-press) + THA tay — cho nut chup: giu = quay video, tha = dung.
    // De null thi nut hoat dong nhu cu (chi click).
    onLongPress: (() -> Unit)? = null,
    onPressRelease: (() -> Unit)? = null,
) {
    // Assert: only 1 type of content allowed
    val contentCount = listOfNotNull(iconSetting, imageSetting, innerContent).size
    require(contentCount <= 1) {
        "Only one of iconSetting, imageSetting, or innerContent should be provided."
    }

    val innerSize = outerSize - gap * 2

    Box(
        modifier = modifier
            .size(outerSize)
            .background(
                color = backgroundColor,
                shape = CircleShape,
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = CircleShape,
            )
            .then(
                if (onLongPress != null || onPressRelease != null) {
                    // Can phan biet tap / giu / tha -> dung detectTapGestures
                    // (onPressRelease goi o MOI lan tha tay — ben nhan tu kiem tra
                    // co dang quay hay khong)
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onClick() },
                            onLongPress = { onLongPress?.invoke() },
                            onPress = {
                                tryAwaitRelease()
                                onPressRelease?.invoke()
                            },
                        )
                    }
                } else {
                    Modifier.clickable { onClick() }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            innerContent != null -> {
                Box(
                    modifier = Modifier
                        .size(innerSize.coerceAtLeast(0.dp))
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    innerContent()
                }
            }

            iconSetting != null -> {
                SkinIcon(
                    res = iconSetting.skinRes,
                    fallback = iconSetting.icon,
                    contentDescription = iconSetting.contentDescription,
                    modifier = Modifier.size(innerSize.coerceAtLeast(0.dp)),
                    tint = iconSetting.tint ?: SkinTheme.colors.textPrimary,
                )
            }

            imageSetting != null -> {
                AsyncImage(
                    model = imageSetting.imageUrl,
                    contentDescription = imageSetting.contentDescription,
                    modifier = Modifier
                        .size(innerSize.coerceAtLeast(0.dp))
                        .fillMaxSize() // để ảnh lấp toàn bộ vùng chứa
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }

            else -> {
                Box(
                    modifier = Modifier
                        .size(innerSize.coerceAtLeast(0.dp))
                        .background(color = SkinTheme.colors.textPrimary, shape = CircleShape),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF404137)
@Composable
fun CirclePreview() {
    MaterialTheme {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Circle(
                outerSize = 56.dp,
                gap = 5.dp,
                backgroundColor = SkinTheme.colors.pill,
                onClick = {},
            )

            Circle(
                outerSize = 40.dp,
                gap = 4.dp,
                backgroundColor = SkinTheme.colors.pill,
                onClick = {},
            )

            Circle(
                outerSize = 64.dp,
                gap = 6.dp,
                backgroundColor = SkinTheme.colors.pill,
                borderColor = SkinTheme.colors.onSurfaceVariant,
                onClick = {},
            )

            Circle(
                outerSize = 56.dp,
                gap = 10.dp,
                backgroundColor = SkinTheme.colors.pill,
                onClick = {},
                iconSetting = IconSetting(
                    icon = Icons.AutoMirrored.Filled.Send,
                ),
            )

            Circle(
                outerSize = 56.dp,
                gap = 10.dp,
                modifier = Modifier.rotate(-45f),
                backgroundColor = SkinTheme.colors.pill,
                onClick = {},
                iconSetting = IconSetting(
                    icon = Icons.AutoMirrored.Filled.Send,
                ),
            )

            Circle(
                outerSize = 56.dp,
                gap = 5.dp,
                backgroundColor = SkinTheme.colors.pill,
                onClick = {},
                imageSetting = ImageSetting(
                    imageUrl = "https://images.unsplash.com/photo-1710988238169-12c5c2474652?q=80&w=1329&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
                    contentDescription = "Example Image",
                ),
            )

            Circle(
                outerSize = 56.dp,
                gap = 5.dp,
                backgroundColor = SkinTheme.colors.pill,
                onClick = {},
                innerContent = {
                    // Anh mau cua preview — `avatar.jpg` da bi xoa khoi res/,
                    // dung icon co san; preview chi can "co anh trong vong tron".
                    AsyncImage(
                        model = R.mipmap.ic_launcher,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                    )
                },
            )

            Circle(
                outerSize = 56.dp,
                gap = 0.dp,
                borderWidth = 10.dp,
                backgroundColor = SkinTheme.colors.pill,
                onClick = {},
                innerContent = {
                    AsyncImage(
                        model = R.mipmap.ic_launcher,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                    )
                },
            )

            // Custom content: text or anything
            Circle(
                outerSize = 56.dp,
                gap = 8.dp,
                backgroundColor = Color.DarkGray,
                onClick = {},
                innerContent = {
                    androidx.compose.material3.Text("A", color = SkinTheme.colors.textPrimary)
                },
            )

            Circle(
                outerSize = 56.dp,
                gap = 8.dp,
                backgroundColor = Color.DarkGray,
                modifier = Modifier.rotate(45f),
                onClick = {},
                innerContent = {
                    androidx.compose.material3.Text("A", color = SkinTheme.colors.textPrimary)
                },
            )
        }
    }
}
