package com.example.snapget.core.data

import com.example.snapget.core.model.Setting
import com.example.snapget.core.model.SettingType

/**
 * Nguon TINH cua danh sach settings hien thi trong app.
 *
 * Settings o Snapget la CAU HINH UI (khong thuoc domain server) nen khong can
 * goi mang. Truoc day [FirestoreRepository] doc tu Firestore collection
 * "settings" — nhung collection do khong bao gio duoc seed nen man hinh Settings
 * hien ra TRONG. Nay dinh nghia cung tai day.
 *
 * Moi setting co `id` ON DINH (khong dung UUID ngau nhien nhu default cua
 * [Setting]) de trang thai toggle luu local qua [SettingsPreferences] map dung
 * lai sau khi restart app.
 */
object SettingDefaults {

    val defaults: List<Setting> = listOf(
        Setting(
            id = "widget_settings",
            type = SettingType.WIDGET,
            title = "Widget Settings",
            description = "Customize your home screen widget",
        ),

        Setting(
            id = "app_icon",
            type = SettingType.CUSTOMIZE,
            title = "App Icon",
            icon = "ICON_APP",
            description = "Choose from 12 beautiful app icons",
        ),
        Setting(
            id = "theme",
            type = SettingType.CUSTOMIZE,
            title = "Theme",
            icon = "ICON_THEME",
            description = "Switch between light, dark, or auto mode",
        ),
        Setting(
            id = "streak_on_widget",
            type = SettingType.CUSTOMIZE,
            title = "Streak on widget",
            icon = "ICON_COLOR",
            description = "Show your streak on the home screen widget",
            isToggleable = true,
            isToggled = true,
        ),

        Setting(
            id = "edit_name",
            type = SettingType.GENERAL,
            title = "Edit Name",
            description = "Change your display name",
        ),
        Setting(
            id = "edit_birthday",
            type = SettingType.GENERAL,
            title = "Edit Birthday",
            description = "Set or update your birth date",
        ),
        Setting(
            id = "change_phone_number",
            type = SettingType.GENERAL,
            title = "Change Phone Number",
            description = "Update your contact number",
        ),
        Setting(
            id = "how_to_add_widget",
            type = SettingType.GENERAL,
            title = "How to Add Widget",
            description = "Step-by-step widget setup guide",
        ),

        Setting(
            id = "blocked_accounts",
            type = SettingType.PRIVACY_SAFETY,
            title = "Blocked Accounts",
            description = "View and manage blocked users",
        ),
        Setting(
            id = "account_visibility",
            type = SettingType.PRIVACY_SAFETY,
            title = "Account Visibility",
            description = "Control who can find your profile",
        ),
        Setting(
            id = "privacy_choices",
            type = SettingType.PRIVACY_SAFETY,
            title = "Privacy Choices",
            description = "Manage data sharing preferences",
        ),

        Setting(
            id = "report_a_problem",
            type = SettingType.SUPPORT,
            title = "Report a Problem",
            description = "Get help with technical issues",
        ),
        Setting(
            id = "make_a_suggestion",
            type = SettingType.SUPPORT,
            title = "Make a Suggestion",
            description = "Share ideas for new features",
        ),

        Setting(
            id = "about_tiktok",
            type = SettingType.ABOUT,
            title = "TikTok",
            description = "@snapgetapp - Latest updates & tips",
        ),
        Setting(
            id = "about_instagram",
            type = SettingType.ABOUT,
            title = "Instagram",
            description = "@snapgetapp - Behind the scenes",
        ),
        Setting(
            id = "about_twitter",
            type = SettingType.ABOUT,
            title = "Twitter",
            description = "@snapgetapp - News & announcements",
        ),
        Setting(
            id = "share_snapget",
            type = SettingType.ABOUT,
            title = "Share Snapget",
            description = "Invite friends to join Snapget",
        ),
        Setting(
            id = "rate_snapget",
            type = SettingType.ABOUT,
            title = "Rate Snapget",
            description = "Leave a review on your app store",
        ),
        Setting(
            id = "terms_of_service",
            type = SettingType.ABOUT,
            title = "Terms of Service",
            description = "Read our terms and conditions",
        ),
        Setting(
            id = "privacy_policy",
            type = SettingType.ABOUT,
            title = "Privacy Policy",
            description = "Understand how we protect your data",
        ),

        Setting(
            id = "delete_account",
            type = SettingType.DANGER_ZONE,
            title = "Delete Account",
            description = "Permanently delete your account and all data",
        ),
        Setting(
            id = "sign_out",
            type = SettingType.DANGER_ZONE,
            title = "Sign Out",
            description = "Sign out from all devices",
        ),
    )
}
