package com.example.snapget.core.model

import com.google.firebase.firestore.DocumentSnapshot
import java.util.UUID

data class Setting(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Setting Title",
    val description: String = "",
    val icon: String = "ICON_DEFAULT",
    val type: SettingType,
    val isToggleable: Boolean = false,
    val isToggled: Boolean = false,
) {
    companion object {
        fun fromDocument(doc: DocumentSnapshot): Setting = Setting(
            id = doc.id,
            title = doc.getString("title") ?: "",
            description = doc.getString("description") ?: "",
            icon = doc.getString("icon") ?: "ICON_DEFAULT",
            type = runCatching { SettingType.valueOf(doc.getString("type") ?: "") }
                .getOrDefault(SettingType.GENERAL),
            isToggleable = doc.getBoolean("isToggleable") ?: false,
            isToggled = doc.getBoolean("isToggled") ?: false,
        )
    }
}

enum class SettingType {
    WIDGET,
    CUSTOMIZE,
    GENERAL,
    PRIVACY_SAFETY,
    SUPPORT,
    ABOUT,
    DANGER_ZONE,
}
