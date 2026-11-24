package dev.orgtd.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import java.io.IOException

/** 离线副本不允许写回。 */
class OfflineWriteException : IOException("offline")

/** 一次读取的结果；[etag] / [lastModified] 用于写回时做并发校验。 */
data class Snapshot(
    val text: String,
    val etag: String? = null,
    val lastModified: String? = null,
    /** true 表示网络不通，内容来自本地缓存。 */
    val fromCache: Boolean = false,
)

/**
 * 一个 org 文件来源：本地目录或 WebDAV。
 *
 * 上层只认「读 / 写 / 返回校验标记」，不关心是 SAF 还是网络。
 */
interface OrgSource {
    val label: String

    /** 文件不存在返回 null。 */
    fun read(): Snapshot?

    /** 写入并返回新的快照；[expected] 用于 If-Match 校验。 */
    fun write(text: String, expected: Snapshot?, backupOf: String?): Snapshot?
}

class LocalSource(
    context: Context,
    treeUri: Uri,
    private val name: String,
) : OrgSource {

    private val store = SafStore(context, treeUri)

    override val label: String = "本地目录"

    override fun read(): Snapshot? = store.read(name)?.let { Snapshot(it) }

    override fun write(text: String, expected: Snapshot?, backupOf: String?): Snapshot {
        if (backupOf != null && backupOf != text) store.write("$name.bak", backupOf)
        store.write(name, text)
        return Snapshot(text)
    }
}

class RemoteSource(
    private val context: Context,
    private val client: WebDavClient,
    private val fileName: String,
) : OrgSource {

    override val label: String = "坚果云 WebDAV"

    private val cacheDir = File(context.filesDir, "cache")
    private val cacheFile = File(cacheDir, fileName)
    private val metaFile = File(cacheDir, "$fileName.meta")

    /**
     * 网络失败时回退到上次成功同步的副本，界面会标记「离线」。
     * 409/412 这类冲突必须原样抛出，不能被缓存吞掉。
     */
    override fun read(): Snapshot? {
        return try {
            val remote = client.get()
            if (remote == null) {
                clearCache()
                null
            } else {
                saveCache(remote)
                Snapshot(remote.text, remote.etag, remote.lastModified, fromCache = false)
            }
        } catch (conflict: ConflictException) {
            throw conflict
        } catch (error: IOException) {
            loadCache() ?: throw error
        }
    }

    override fun write(text: String, expected: Snapshot?, backupOf: String?): Snapshot? {
        if (expected?.fromCache == true) {
            throw OfflineWriteException()
        }

        // 远端不留 .bak（多一次请求、多一份同步面），改成写本地私有目录
        if (backupOf != null && backupOf != text) {
            runCatching {
                val dir = File(context.filesDir, "backup").apply { mkdirs() }
                File(dir, fileName).writeText(backupOf)
            }
        }

        val remote = client.put(text, expected?.etag, expected?.lastModified)
            ?: throw IOException("empty response")
        saveCache(remote)
        return Snapshot(remote.text, remote.etag, remote.lastModified)
    }

    private fun saveCache(file: RemoteFile) {
        runCatching {
            cacheDir.mkdirs()
            cacheFile.writeText(file.text)
            metaFile.writeText(file.etag.orEmpty() + "\n" + file.lastModified.orEmpty())
        }
    }

    private fun loadCache(): Snapshot? {
        if (!cacheFile.isFile) return null
        val text = runCatching { cacheFile.readText() }.getOrNull() ?: return null
        val meta = runCatching { metaFile.readLines() }.getOrDefault(emptyList())
        return Snapshot(
            text = text,
            etag = meta.getOrNull(0)?.ifBlank { null },
            lastModified = meta.getOrNull(1)?.ifBlank { null },
            fromCache = true,
        )
    }

    private fun clearCache() {
        runCatching { cacheFile.delete() }
        runCatching { metaFile.delete() }
    }
}

object Sources {

    fun create(context: Context): OrgSource? {
        val prefs = OrgPrefs(context)
        return when (prefs.mode) {
            StorageMode.LOCAL -> prefs.treeUri
                ?.let { runCatching { it.toUri() }.getOrNull() }
                ?.let { LocalSource(context, it, prefs.fileName) }

            StorageMode.WEBDAV -> {
                val url = prefs.webDavUrl
                if (url.isBlank() || prefs.webDavUser.isBlank()) {
                    null
                } else {
                    RemoteSource(
                        context = context,
                        client = WebDavClient(url, prefs.webDavUser, SecureStore.password(context)),
                        fileName = prefs.fileName,
                    )
                }
            }
        }
    }
}
