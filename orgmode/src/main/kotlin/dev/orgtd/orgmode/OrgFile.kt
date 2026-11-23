package dev.orgtd.orgmode

/**
 * org 文件的极简容器。
 *
 * M0 阶段只确立一条不变量：**解析再序列化，必须与原文逐字节相同**。
 * M1 会把它替换成真正的 AST（headline / planning / property drawer / body），
 * 但那条不变量不会变，它是整个项目能安全写回 Emacs 文件的前提。
 */
class OrgFile private constructor(private val lines: List<Line>) {

    val lineCount: Int get() = lines.size

    val headlineCount: Int get() = lines.count { it.isHeadline }

    override fun toString(): String = buildString {
        lines.forEach { append(it.raw) }
    }

    /**
     * 一行原文。[raw] 含行尾换行符，序列化时原样输出，因此 CRLF、末行无换行都能保留。
     */
    data class Line(val raw: String) {
        val text: String get() = raw.trimEnd('\r', '\n')

        val isHeadline: Boolean
            get() {
                val stars = text.takeWhile { it == '*' }.length
                if (stars == 0) return false
                return stars == text.length || text[stars] == ' ' || text[stars] == '\t'
            }
    }

    companion object {
        fun parse(text: String): OrgFile = OrgFile(splitKeepingLineEndings(text))

        private fun splitKeepingLineEndings(text: String): List<Line> {
            if (text.isEmpty()) return emptyList()

            val lines = ArrayList<Line>()
            var start = 0
            for (i in text.indices) {
                if (text[i] == '\n') {
                    lines += Line(text.substring(start, i + 1))
                    start = i + 1
                }
            }
            if (start < text.length) lines += Line(text.substring(start))
            return lines
        }
    }
}
