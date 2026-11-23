package dev.orgtd.data

import android.content.Context
import android.net.Uri
import java.io.IOException
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ConflictException : IOException("文件已在别处修改")

data class RemoteFile(val text: String, val etag: String?, val lastModified: String?)

/**
 * 极小的 WebDAV 客户端：只需要对单个文件 GET / PUT。
 *
 * 这里故意不解析 PROPFIND——目录由用户直接给出完整文件 URL，
 * 少一次请求，也少一类解析错误。并发保护靠 If-Match / If-Unmodified-Since。
 */
class WebDavClient(
    private val url: String,
    private val username: String,
    private val password: String,
    private val client: OkHttpClient = defaultClient(),
) {

    fun get(): RemoteFile? {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", Credentials.basic(username, password))
            .build()

        client.newCall(request).execute().use { response ->
            return when {
                response.code == 404 -> null
                !response.isSuccessful -> throw IOException("读取失败：HTTP ${response.code}")
                else -> RemoteFile(
                    text = response.body?.string().orEmpty(),
                    etag = response.header("ETag"),
                    lastModified = response.header("Last-Modified"),
                )
            }
        }
    }

    fun put(text: String, etag: String?, lastModified: String?): RemoteFile? {
        val request = Request.Builder()
            .url(url)
            .put(text.toRequestBody("text/plain; charset=utf-8".toMediaType()))
            .header("Authorization", Credentials.basic(username, password))
            .apply {
                if (!etag.isNullOrBlank()) header("If-Match", etag)
                else if (!lastModified.isNullOrBlank()) header("If-Unmodified-Since", lastModified)
            }
            .build()

        client.newCall(request).execute().use { response ->
            return when {
                response.code == 412 || response.code == 409 -> throw ConflictException()
                !response.isSuccessful -> throw IOException("写入失败：HTTP ${response.code}")
                else -> RemoteFile(
                    text = text,
                    etag = response.header("ETag"),
                    lastModified = response.header("Last-Modified"),
                )
            }
        }
    }

    /** 保存前确认能连上、文件存在且可读。 */
    fun check(): String {
        val file = get() ?: throw IOException("服务器上找不到这个文件")
        return file.text
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}
