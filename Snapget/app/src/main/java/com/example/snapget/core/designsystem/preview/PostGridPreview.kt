package com.example.snapget.core.designsystem.preview

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.snapget.core.data.SampleData
import com.example.snapget.core.designsystem.component.grid.PostGrid
import com.example.snapget.core.designsystem.theme.AppTheme

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, heightDp = 400)
@Composable
fun PostGridWithDataPreview() {
    AppTheme {
        PostGrid(
            posts = SampleData.samplePosts,
            onPostClick = {},
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, heightDp = 200)
@Composable
fun PostGridNoCameraPreview() {
    AppTheme {
        PostGrid(
            posts = SampleData.samplePosts,
            onPostClick = {},
        )
    }
}
