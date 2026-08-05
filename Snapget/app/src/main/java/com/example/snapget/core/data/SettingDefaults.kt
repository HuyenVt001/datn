package com.example.snapget.core.data

import com.example.snapget.core.model.Setting
import com.example.snapget.core.model.SettingType

/**
 * Id ON DINH cua tung setting — dung de dispatch click / map icon trong
 * SettingScreen thay vi match theo title (de vo khi doi chu).
 */
object SettingIds {
    const val WIDGET_SETTINGS = "widget_settings"
    const val APP_ICON = "app_icon"

    /** Thay cho "theme" cu (go giao dien Light 2026-08-05) — mo man Appearance. */
    const val APPEARANCE = "appearance"
    const val STREAK_ON_WIDGET = "streak_on_widget"
    const val EDIT_NAME = "edit_name"
    const val EDIT_BIRTHDAY = "edit_birthday"
    const val CHANGE_PHONE_NUMBER = "change_phone_number"
    const val HOW_TO_ADD_WIDGET = "how_to_add_widget"
    const val BLOCKED_ACCOUNTS = "blocked_accounts"
    const val ACCOUNT_VISIBILITY = "account_visibility"
    const val PRIVACY_CHOICES = "privacy_choices"
    const val REPORT_A_PROBLEM = "report_a_problem"
    const val MAKE_A_SUGGESTION = "make_a_suggestion"
    const val ABOUT_TIKTOK = "about_tiktok"
    const val ABOUT_INSTAGRAM = "about_instagram"
    const val ABOUT_TWITTER = "about_twitter"
    const val SHARE_SNAPGET = "share_snapget"
    const val RATE_SNAPGET = "rate_snapget"
    const val TERMS_OF_SERVICE = "terms_of_service"
    const val PRIVACY_POLICY = "privacy_policy"
    const val DELETE_ACCOUNT = "delete_account"
    const val SIGN_OUT = "sign_out"
}

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
            id = SettingIds.WIDGET_SETTINGS,
            type = SettingType.WIDGET,
            title = "Widget Settings",
            description = "Customize your home screen widget",
        ),

        Setting(
            id = SettingIds.APP_ICON,
            type = SettingType.CUSTOMIZE,
            title = "App Icon",
            icon = "ICON_APP",
            description = "Choose from 12 beautiful app icons",
        ),
        Setting(
            id = SettingIds.APPEARANCE,
            type = SettingType.CUSTOMIZE,
            title = "Appearance",
            icon = "ICON_THEME",
            description = "Change your skin, touch effect and see your frames",
        ),
        Setting(
            id = SettingIds.STREAK_ON_WIDGET,
            type = SettingType.CUSTOMIZE,
            title = "Streak on widget",
            icon = "ICON_COLOR",
            description = "Show your streak on the home screen widget",
            isToggleable = true,
            isToggled = true,
        ),

        Setting(
            id = SettingIds.EDIT_NAME,
            type = SettingType.GENERAL,
            title = "Edit Name",
            description = "Change your display name",
        ),
        Setting(
            id = SettingIds.EDIT_BIRTHDAY,
            type = SettingType.GENERAL,
            title = "Edit Birthday",
            description = "Set or update your birth date",
        ),
        Setting(
            id = SettingIds.CHANGE_PHONE_NUMBER,
            type = SettingType.GENERAL,
            title = "Change Phone Number",
            description = "Update your contact number",
        ),
        Setting(
            id = SettingIds.HOW_TO_ADD_WIDGET,
            type = SettingType.GENERAL,
            title = "How to Add Widget",
            description = "Step-by-step widget setup guide",
        ),

        Setting(
            id = SettingIds.BLOCKED_ACCOUNTS,
            type = SettingType.PRIVACY_SAFETY,
            title = "Blocked Accounts",
            description = "View and manage blocked users",
        ),
        Setting(
            id = SettingIds.ACCOUNT_VISIBILITY,
            type = SettingType.PRIVACY_SAFETY,
            title = "Account Visibility",
            description = "Control who can find your profile",
        ),
        Setting(
            id = SettingIds.PRIVACY_CHOICES,
            type = SettingType.PRIVACY_SAFETY,
            title = "Privacy Choices",
            description = "Manage data sharing preferences",
        ),

        Setting(
            id = SettingIds.REPORT_A_PROBLEM,
            type = SettingType.SUPPORT,
            title = "Report a Problem",
            description = "Get help with technical issues",
        ),
        Setting(
            id = SettingIds.MAKE_A_SUGGESTION,
            type = SettingType.SUPPORT,
            title = "Make a Suggestion",
            description = "Share ideas for new features",
        ),

        Setting(
            id = SettingIds.ABOUT_TIKTOK,
            type = SettingType.ABOUT,
            title = "TikTok",
            description = "@snapgetapp - Latest updates & tips",
        ),
        Setting(
            id = SettingIds.ABOUT_INSTAGRAM,
            type = SettingType.ABOUT,
            title = "Instagram",
            description = "@snapgetapp - Behind the scenes",
        ),
        Setting(
            id = SettingIds.ABOUT_TWITTER,
            type = SettingType.ABOUT,
            title = "Twitter",
            description = "@snapgetapp - News & announcements",
        ),
        Setting(
            id = SettingIds.SHARE_SNAPGET,
            type = SettingType.ABOUT,
            title = "Share Snapget",
            description = "Invite friends to join Snapget",
        ),
        Setting(
            id = SettingIds.RATE_SNAPGET,
            type = SettingType.ABOUT,
            title = "Rate Snapget",
            description = "Leave a review on your app store",
        ),
        Setting(
            id = SettingIds.TERMS_OF_SERVICE,
            type = SettingType.ABOUT,
            title = "Terms of Service",
            description = "Read our terms and conditions",
        ),
        Setting(
            id = SettingIds.PRIVACY_POLICY,
            type = SettingType.ABOUT,
            title = "Privacy Policy",
            description = "Understand how we protect your data",
        ),

        Setting(
            id = SettingIds.DELETE_ACCOUNT,
            type = SettingType.DANGER_ZONE,
            title = "Delete Account",
            description = "Permanently delete your account and all data",
        ),
        Setting(
            id = SettingIds.SIGN_OUT,
            type = SettingType.DANGER_ZONE,
            title = "Sign Out",
            description = "Sign out from all devices",
        ),
    )

    /**
     * Cac muc TAM AN khoi man Settings (van giu data de bat lai sau):
     * - report/suggestion: user chot an (2026-07-26)
     * - app_icon + change_phone_number: hoan lai dot sau (2026-07-26)
     */
    private val hiddenIds = setOf(
        SettingIds.REPORT_A_PROBLEM,
        SettingIds.MAKE_A_SUGGESTION,
        SettingIds.APP_ICON,
        SettingIds.CHANGE_PHONE_NUMBER,
    )

    /** Danh sach thuc su hien thi tren man Settings. */
    val visible: List<Setting> = defaults.filterNot { it.id in hiddenIds }
}
