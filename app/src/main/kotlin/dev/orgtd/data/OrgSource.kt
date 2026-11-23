package dev.orgtd.data

import android.content.Context
import android.net.Uri
import java.io.File

/** 一次读取的结果；[etag] / [lastModified] 用于写回时做并发校验。 */
data class Snapshot(
    val text: String,
    val etag: String? = null,
    val lastModified: String? = null,
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

    override fun read(): Snapshot? = client.get()?.let { Snapshot(it.text, it.etag, it.lastModified) }

    override fun write(text: String, expected: Snapshot?, backupOf: String?): Snapshot? {
        // 远端不留 .bak（多一次请求、多一份同步面），改成写本地私有目录
        if (backupOf != null && backupOf != text) {
            runCatching {
                val dir = File(context.filesDir, "backup").apply { mkdirs() }
                File(dir, fileName).writeText(backupOf)
            }
        }
        return client.put(text, expected?.etag, expected?.lastModified)
            ?.let { Snapshot(it.text, it.etag, it.lastModified) }
    }
}

object Sources {

    fun create(context: Context): OrgSource? {
        val prefs = OrgPrefs(context)
        return when (prefs.mode) {
            StorageMode.LOCAL -> prefs.treeUri
                ?.let { runCatching { Uri.parse(it) }.getOrNull() }
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
