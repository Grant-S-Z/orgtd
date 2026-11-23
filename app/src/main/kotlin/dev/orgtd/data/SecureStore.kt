package dev.orgtd.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * WebDAV 应用密码的加密存储。
 *
 * 用 Android Keystore 里的一把 AES-256 密钥做 AES/GCM 加密，密文是 `base64(iv):base64(cipher)`，
 * 明文密钥永远不落盘。之前用的 `EncryptedSharedPreferences` 在 androidx.security 1.1 里已废弃，
 * 这里换成等价的直接实现，少一个依赖。
 *
 * 注意：Keystore 密钥不参与备份，换机/恢复备份后旧密文解不开，读出来是空串，
 * 用户重新填一次应用密码即可。
 */
object SecureStore {

    private const val PREFS_FILE = "orgtd.secrets"
    private const val KEY_PASSWORD = "webdav_password"
    private const val KEY_ALIAS = "orgtd_webdav_password"

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128
    private const val KEY_SIZE = 256

    fun password(context: Context): String {
        val encoded = prefs(context).getString(KEY_PASSWORD, null)
        if (encoded.isNullOrBlank()) return ""
        return runCatching { decrypt(encoded) }.getOrDefault("")
    }

    fun setPassword(context: Context, value: String) {
        val encoded = if (value.isEmpty()) "" else encrypt(value)
        prefs(context).edit().putString(KEY_PASSWORD, encoded).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val parts = encoded.split(":", limit = 2)
        if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) return ""

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_BITS, Base64.decode(parts[0], Base64.NO_WRAP)),
        )
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }
}
