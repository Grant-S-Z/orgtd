package dev.orgtd.update

import android.content.Context
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(val latestVersion: String, val releaseUrl: String)

/**
 * 通过 GitHub Releases 检查新版本。
 *
 * 公开仓库不需要 token；只在用户点「检查更新」时请求一次。
 */
object UpdateChecker {

    private const val API = "https://api.github.com/repos/Grant-S-Z/orgtd/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"

    fun check(context: Context): Result<UpdateInfo> = runCatching {
        val request = Request.Builder()
            .url(API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "orgtd-android")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            val version = json.optString("tag_name").removePrefix("v").removePrefix("V")
            if (version.isBlank()) throw IOException("empty tag_name")
            UpdateInfo(version, json.optString("html_url"))
        }
    }

    /** 返回 latest 是否比 current 新。 */
    fun isNewer(latest: String, current: String): Boolean = compare(latest, current) > 0

    private fun compare(a: String, b: String): Int {
        val left = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = (left.getOrNull(i) ?: 0) - (right.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }
}
