package com.example.snapget.core.designsystem.preview

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.snapget.core.data.SampleData
import com.example.snapget.core.designsystem.component.grid.CameraButton
import com.example.snapget.core.designsystem.component.grid.PostGrid
import com.example.snapget.core.designsystem.component.grid.PostGridItem
import com.example.snapget.core.designsystem.theme.AppTheme

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "Post Grid", showBackground = true)
@Composable
fun PostGridPreview() {
    AppTheme {
        Surface {
            PostGrid(
                posts = SampleData.samplePosts,
                onPostClick = {},
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "Post Grid Item", showBackground = true)
@Composable
fun PostGridItemPreview() {
    AppTheme {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                PostGridItem(
                    post = SampleData.samplePosts.first { true },
                    onClick = {},
                )
            }
        }
    }
}

@Preview(name = "Camera Button", showBackground = true)
@Composable
fun CameraButtonPreview() {
    AppTheme {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                CameraButton(onClick = {})
            }
        }
    }
}

// LUU Y: da bo PostDetailScreenPreview + CameraScreenPreview (2026-07-16) —
// ca 2 screen deu tu tao hiltViewModel (PostDetailScreen) / can CameraX runtime
// (CameraScreen) nen preview luon fail "Failed to instantiate a ViewModel".
// Muon preview lai -> tach bien the Content stateless truoc (CLAUDE.md muc 8).

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "Post Grid Without Camera", showBackground = true)
@Composable
fun PostGridWithoutCameraPreview() {
    AppTheme {
        Surface {
            PostGrid(
                posts = SampleData.samplePosts,
                onPostClick = {},
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(name = "Post Grid Dark Theme", showBackground = true)
@Composable
fun PostGridDarkPreview() {
    AppTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            PostGrid(
                posts = SampleData.samplePosts,
                onPostClick = {},
            )
        }
    }
}
