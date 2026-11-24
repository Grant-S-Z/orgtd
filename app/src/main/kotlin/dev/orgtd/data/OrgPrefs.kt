package dev.orgtd.data

import android.content.Context
import androidx.core.content.edit
import dev.orgtd.Language
import dev.orgtd.UiStyle

enum class StorageMode { LOCAL, WEBDAV }

enum class DarkMode { SYSTEM, LIGHT, DARK }

/** 应用级设置：存储方式、本地目录授权、WebDAV 地址与账号。 */
class OrgPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("orgtd.prefs", Context.MODE_PRIVATE)

    var mode: StorageMode
        get() = StorageMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: StorageMode.LOCAL
        set(value) = prefs.edit { putString(KEY_MODE, value.name) }

    var treeUri: String?
        get() = prefs.getString(KEY_TREE_URI, null)
        set(value) = prefs.edit { putString(KEY_TREE_URI, value) }

    var fileName: String
        get() = prefs.getString(KEY_FILE_NAME, DEFAULT_FILE) ?: DEFAULT_FILE
        set(value) = prefs.edit { putString(KEY_FILE_NAME, value) }

    var webDavUrl: String
        get() = prefs.getString(KEY_DAV_URL, DEFAULT_DAV_URL) ?: DEFAULT_DAV_URL
        set(value) = prefs.edit { putString(KEY_DAV_URL, value) }

    var webDavUser: String
        get() = prefs.getString(KEY_DAV_USER, "") ?: ""
        set(value) = prefs.edit { putString(KEY_DAV_USER, value) }

    var themeId: String
        get() = prefs.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        set(value) = prefs.edit { putString(KEY_THEME, value) }

    var uiStyle: UiStyle
        get() = UiStyle.entries.firstOrNull { it.name == prefs.getString(KEY_UI_STYLE, null) }
            ?: UiStyle.APPLE
        set(value) = prefs.edit { putString(KEY_UI_STYLE, value.name) }

    var remindDateOnly: Boolean
        get() = prefs.getBoolean(KEY_REMIND_DATE_ONLY, false)
        set(value) = prefs.edit { putBoolean(KEY_REMIND_DATE_ONLY, value) }

    var language: Language
        get() = Language.entries.firstOrNull { it.name == prefs.getString(KEY_LANGUAGE, null) }
            ?: Language.SYSTEM
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value.name) }

    var darkMode: DarkMode
        get() = DarkMode.entries.firstOrNull { it.name == prefs.getString(KEY_DARK_MODE, null) }
            ?: DarkMode.SYSTEM
        set(value) = prefs.edit { putString(KEY_DARK_MODE, value.name) }

    companion object {
        const val DEFAULT_FILE = "gtd.org"
        const val DEFAULT_DAV_URL = "https://dav.jianguoyun.com/dav/orgtd/gtd.org"
        const val DEFAULT_THEME = "ocean"

        private const val KEY_MODE = "mode"
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_DAV_URL = "dav_url"
        private const val KEY_DAV_USER = "dav_user"
        private const val KEY_THEME = "theme_id"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_REMIND_DATE_ONLY = "remind_date_only"
        private const val KEY_UI_STYLE = "ui_style"
    }
}
