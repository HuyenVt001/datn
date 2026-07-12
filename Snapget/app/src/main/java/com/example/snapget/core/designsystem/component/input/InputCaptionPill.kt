package com.example.snapget.core.designsystem.component.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pill nhap caption tren anh (asset: nen trang mo #80F0F0F0, CHU DEN 16sp — DESIGN.md muc 2).
 * State hoisted: man hinh cha giu text de gui kem moment khi bam Send.
 */
@Composable
fun InputCaptionPill(
    text: String,
    onTextChange: (String) -> Unit,
    width: Dp = 250.dp,
    maxLength: Int = 30,
    placeholder: String = "Add a caption...",
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(IntrinsicSize.Min), // optional: để chiều cao theo content
    ) {
        // ✅ Layer 1: Background blur
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    color = Color(0x80F0F0F0),
                    shape = RoundedCornerShape(50),
                )
                .blur(10.dp), // chỉ làm mờ nền
        )

        // ✅ Layer 2: Text input on top
        BasicTextField(
            value = text,
            onValueChange = {
                if (it.length <= maxLength) onTextChange(it)
            },
            singleLine = true,
            textStyle = TextStyle(
                color = Color.Black,
                fontSize = 16.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Color.White,
                            fontSize = 16.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1611)
@Composable
fun InputCaptionPillWithPreview() {
    var text by remember { mutableStateOf("") }
    InputCaptionPill(text = text, onTextChange = { text = it }, width = 250.dp)
}
