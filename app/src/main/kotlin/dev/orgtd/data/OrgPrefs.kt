package dev.orgtd.data

import android.content.Context

/** 应用级设置：同步目录的持久化授权、要读写的文件名。 */
class OrgPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("orgtd.prefs", Context.MODE_PRIVATE)

    var treeUri: String?
        get() = prefs.getString(KEY_TREE_URI, null)
        set(value) = prefs.edit().putString(KEY_TREE_URI, value).apply()

    var fileName: String
        get() = prefs.getString(KEY_FILE_NAME, DEFAULT_FILE) ?: DEFAULT_FILE
        set(value) = prefs.edit().putString(KEY_FILE_NAME, value).apply()

    companion object {
        const val DEFAULT_FILE = "gtd.org"
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_FILE_NAME = "file_name"
    }
}
