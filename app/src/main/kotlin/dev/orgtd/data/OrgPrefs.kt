package dev.orgtd.data

import android.content.Context

enum class StorageMode { LOCAL, WEBDAV }

/** 应用级设置：存储方式、本地目录授权、WebDAV 地址与账号。 */
class OrgPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("orgtd.prefs", Context.MODE_PRIVATE)

    var mode: StorageMode
        get() = StorageMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: StorageMode.LOCAL
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    var treeUri: String?
        get() = prefs.getString(KEY_TREE_URI, null)
        set(value) = prefs.edit().putString(KEY_TREE_URI, value).apply()

    var fileName: String
        get() = prefs.getString(KEY_FILE_NAME, DEFAULT_FILE) ?: DEFAULT_FILE
        set(value) = prefs.edit().putString(KEY_FILE_NAME, value).apply()

    var webDavUrl: String
        get() = prefs.getString(KEY_DAV_URL, DEFAULT_DAV_URL) ?: DEFAULT_DAV_URL
        set(value) = prefs.edit().putString(KEY_DAV_URL, value).apply()

    var webDavUser: String
        get() = prefs.getString(KEY_DAV_USER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DAV_USER, value).apply()

    companion object {
        const val DEFAULT_FILE = "gtd.org"
        const val DEFAULT_DAV_URL = "https://dav.jianguoyun.com/dav/orgtd/gtd.org"

        private const val KEY_MODE = "mode"
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_FILE_NAME = "file_name"
        private const val KEY_DAV_URL = "dav_url"
        private const val KEY_DAV_USER = "dav_user"
    }
}
