package com.example.snapget.core.designsystem.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.example.snapget.core.data.SampleData
import com.example.snapget.core.designsystem.theme.AppTheme
import com.example.snapget.feature.settings.SettingScreenContent

@Preview(showBackground = true)
@Composable
fun SettingScreenPreview() {
    AppTheme {
        SettingScreenContent(
            settings = SampleData.settingList,
            navController = rememberNavController(),
        )
    }
}
