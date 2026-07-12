package com.example.snapget.core.designsystem.preview

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.example.snapget.core.designsystem.theme.AppTheme
import com.example.snapget.feature.message.MessageScreen

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun MessageScreenPreview() {
    AppTheme {
        MessageScreen(rememberNavController())
    }
}
