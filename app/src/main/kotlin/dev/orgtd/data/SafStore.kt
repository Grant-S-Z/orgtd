package dev.orgtd.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

/**
 * 通过 SAF 访问用户授权的目录。
 *
 * 只做「按文件名读 / 写」，不遍历、不监听；索引与分组都在上层完成。
 */
class SafStore(private val context: Context, private val treeUri: Uri) {

    fun root(): DocumentFile? = DocumentFile.fromTreeUri(context, treeUri)

    fun exists(name: String): Boolean = root()?.findFile(name)?.isFile == true

    fun read(name: String): String? {
        val file = root()?.findFile(name)?.takeIf { it.isFile } ?: return null
        return context.contentResolver.openInputStream(file.uri)?.use { it.readBytes().decodeToString() }
    }

    fun write(name: String, text: String) {
        val dir = root() ?: throw IOException("folder permission lost")
        // 用 application/octet-stream：SAF 不会按 MIME 给显示名补后缀
        // （用 text/plain 时 "gtd.org.bak" 会被改成 "gtd.org.bak.txt"）
        val file = dir.findFile(name)?.takeIf { it.isFile }
            ?: dir.createFile("application/octet-stream", name)
            ?: throw IOException("cannot create $name")

        context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
            out.write(text.toByteArray())
            out.flush()
        } ?: throw IOException("cannot write $name")
    }

    fun lastModified(name: String): Long =
        root()?.findFile(name)?.lastModified() ?: 0L
}
