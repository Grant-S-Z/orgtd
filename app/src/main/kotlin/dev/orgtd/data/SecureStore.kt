package dev.orgtd.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * WebDAV 密码单独放一个加密的 SharedPreferences。
 * 加密初始化失败时退回私有明文文件（仍然不在外部存储），避免应用直接不可用。
 */
object SecureStore {

    private const val FILE = "orgtd.secrets"
    private const val KEY_PASSWORD = "webdav_password"

    private fun prefs(context: Context): SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        context.getSharedPreferences("$FILE.plain", Context.MODE_PRIVATE)
    }

    fun password(context: Context): String = prefs(context).getString(KEY_PASSWORD, "").orEmpty()

    fun setPassword(context: Context, value: String) {
        prefs(context).edit().putString(KEY_PASSWORD, value).apply()
    }
}
