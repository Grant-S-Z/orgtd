package dev.orgtd.orgmode

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/**
 * 一个 org 文件的可编辑视图。
 *
 * 保真策略：文档内部就是**原始行数组**（每行含行尾换行符），解析只产生只读索引，
 * 所有变更都是「改写某几行 / 插入某几行 / 删除某几行」。未涉及的行不存在被重写的路径，
 * 因此往返保真由数据结构保证，而不是靠格式化器小心翼翼。
 *
 * 文档不可变：每个变更返回新实例，调用方拿到的 [Headline] 与旧实例绑定，
 * 变更后必须重新取。
 */
class OrgDocument private constructor(val lines: List<String>) {

    val entries: List<Headline> = parseEntries(lines)

    fun serialize(): String = lines.joinToString("")

    fun entryAt(lineIndex: Int): Headline? = entries.firstOrNull { it.lineIndex == lineIndex }

    fun entryById(id: String): Headline? = entries.firstOrNull { it.id == id }

    // ---------------------------------------------------------------- 变更

    /** 打勾 / 取消打勾。只改标题行与 CLOSED 行。 */
    fun toggleDone(target: Headline, now: LocalDateTime): OrgDocument {
        val keyword = target.keyword ?: return this
        val out = lines.toMutableList()

        if (keyword == "DONE") {
            out[target.lineIndex] = rewriteLine(out[target.lineIndex], headlineText(target, keyword = "TODO"))
            removePlanning(out, target, "CLOSED")
        } else {
            out[target.lineIndex] = rewriteLine(out[target.lineIndex], headlineText(target, keyword = "DONE"))
            if (target.closed == null) {
                val indent = planningIndent(out, target)
                out.add(target.lineIndex + 1, "${indent}CLOSED: ${OrgTime.formatClosed(now)}${newline(out)}")
            }
        }
        return OrgDocument(out)
    }

    /**
     * 子任务全部完成时，把祖先任务也标记为 DONE（逐级向上）。
     *
     * 只处理「带状态词的条目」作为父任务；清单标题（无状态词）不会被误完成。
     * 通过 `:ID:` 定位目标条目，插入 CLOSED 行导致的行号变化不会影响定位。
     */
    fun completeDoneAncestors(targetId: String, now: LocalDateTime): OrgDocument {
        var doc = this
        repeat(16) {
            val entries = doc.entries
            val target = entries.firstOrNull { it.id == targetId } ?: return doc
            val ancestor = nearestAncestor(entries, target) ?: return doc
            if (ancestor.keyword != "TODO") return doc

            val children = directTaskChildren(entries, ancestor)
            if (children.isEmpty() || !children.all { it.isDone }) return doc

            doc = doc.toggleDone(ancestor, now)
        }
        return doc
    }

    /** 子任务被重新打开时，把已经是 DONE 的祖先任务改回 TODO（逐级向上）。 */
    fun reopenAncestorsWithOpenChild(targetId: String, now: LocalDateTime): OrgDocument {
        var doc = this
        repeat(16) {
            val entries = doc.entries
            val target = entries.firstOrNull { it.id == targetId } ?: return doc
            val ancestor = nearestAncestor(entries, target) ?: return doc
            if (ancestor.keyword != "DONE") return doc

            val children = directTaskChildren(entries, ancestor)
            if (children.any { !it.isDone }) {
                doc = doc.toggleDone(ancestor, now)
            } else {
                return doc
            }
        }
        return doc
    }

    /** 设置 / 清除 SCHEDULED。传 null 表示删除。 */
    fun setScheduled(target: Headline, spec: DateSpec?): OrgDocument {
        val out = lines.toMutableList()
        val limit = minOf(target.bodyStart, target.endExclusive)
        val index = (target.lineIndex + 1 until limit).firstOrNull { i ->
            planningTokens(lineText(out[i])).any { it.first.equals("SCHEDULED", true) }
        }

        if (spec == null) {
            if (index == null) return this
            val rebuilt = rebuildPlanning(lineText(out[index]), remove = "SCHEDULED")
            if (rebuilt.isNullOrBlank()) {
                out.removeAt(index)
            } else {
                out[index] = rewriteLine(out[index], rebuilt)
            }
            return OrgDocument(out)
        }

        if (index != null) {
            val rebuilt = rebuildPlanning(
                lineText(out[index]),
                replace = "SCHEDULED" to OrgTime.formatSpec(spec),
            ) ?: return this
            out[index] = rewriteLine(out[index], rebuilt)
            return OrgDocument(out)
        }

        val indent = planningIndent(out, target)
        val insertAt = limit.coerceAtMost(out.size)
        ensureNewlineBefore(out, insertAt)
        out.add(insertAt, "${indent}SCHEDULED: ${OrgTime.formatSpec(spec)}${newline(out)}")
        return OrgDocument(out)
    }

    fun rename(target: Headline, title: String): OrgDocument {
        if (title == target.title) return this
        val out = lines.toMutableList()
        out[target.lineIndex] = rewriteLine(out[target.lineIndex], headlineText(target, title = title))
        return OrgDocument(out)
    }

    fun setPriority(target: Headline, priority: Char?): OrgDocument {
        if (priority == target.priority) return this
        val out = lines.toMutableList()
        out[target.lineIndex] = rewriteLine(out[target.lineIndex], headlineText(target, priority = priority))
        return OrgDocument(out)
    }

    /** 给条目补 `:ID:`：有抽屉就插在 `:END:` 前，没有就整块插入。 */
    fun ensureId(target: Headline, uuid: String = UUID.randomUUID().toString()): OrgDocument {
        if (target.properties.containsKey("ID")) return this

        val out = lines.toMutableList()
        val nl = newline(out)
        val drawer = findPropertiesDrawer(out, target)

        if (drawer != null) {
            val (_, end) = drawer
            val indent = out[end].takeWhile { it == ' ' || it == '\t' }
            out.add(end, "${indent}:ID: $uuid$nl")
        } else {
            out.addAll(
                target.lineIndex + 1,
                listOf(
                    "  :PROPERTIES:$nl",
                    "  :ID: $uuid$nl",
                    "  :END:$nl",
                ),
            )
        }
        return OrgDocument(out)
    }

    /** 写入/覆盖一个属性；没有属性抽屉时自动补一个。 */
    fun setProperty(target: Headline, key: String, value: String): OrgDocument {
        val out = lines.toMutableList()
        val nl = newline(out)
        val drawer = findPropertiesDrawer(out, target)

        if (drawer == null) {
            out.addAll(
                target.lineIndex + 1,
                listOf(
                    "  :PROPERTIES:$nl",
                    "  :$key: $value$nl",
                    "  :END:$nl",
                ),
            )
            return OrgDocument(out)
        }

        val (start, end) = drawer
        val indent = out[end].takeWhile { it == ' ' || it == '\t' }
        for (i in start + 1 until end) {
            val text = lineText(out[i])
            if (PROPERTY_RE.find(text)?.groupValues?.get(1).equals(key, ignoreCase = true)) {
                out[i] = "$indent:$key: $value$nl"
                return OrgDocument(out)
            }
        }
        out.add(end, "$indent:$key: $value$nl")
        return OrgDocument(out)
    }

    /** 删除属性；抽屉变空时一并删掉。 */
    fun removeProperty(target: Headline, key: String): OrgDocument {
        val out = lines.toMutableList()
        val drawer = findPropertiesDrawer(out, target) ?: return this
        val (start, end) = drawer

        for (i in start + 1 until end) {
            val match = PROPERTY_RE.find(lineText(out[i])) ?: continue
            if (match.groupValues[1].equals(key, ignoreCase = true)) {
                out.removeAt(i)
                break
            }
        }
        if (end - 1 <= start + 1) {
            out.removeAt(end - 1)
            out.removeAt(start)
            return OrgDocument(out)
        }
        return OrgDocument(out)
    }

    /**
     * 新建任务。[list] 为 null 时追加到文件末尾（未归类），否则插到该清单子树末尾。
     */
    fun appendTask(
        title: String,
        id: String,
        scheduled: LocalDate? = null,
        list: Headline? = null,
    ): OrgDocument {
        val out = lines.toMutableList()
        val nl = newline(out)
        val insertAt = list?.endExclusive?.coerceIn(0, out.size) ?: out.size
        ensureNewlineBefore(out, insertAt)

        val level = if (list != null) list.level + 1 else 1
        val stars = "*".repeat(level)

        val block = ArrayList<String>(5)
        block += "$stars TODO $title$nl"
        block += "  :PROPERTIES:$nl"
        block += "  :ID: $id$nl"
        block += "  :END:$nl"
        if (scheduled != null) {
            block += "SCHEDULED: ${OrgTime.formatSpec(DateSpec(scheduled))}$nl"
        }
        out.addAll(insertAt, block)
        return OrgDocument(out)
    }

    fun delete(target: Headline): OrgDocument =
        OrgDocument(lines.filterIndexed { i, _ -> i < target.lineIndex || i >= target.endExclusive })

    /**
     * 重复任务完成后推进 SCHEDULED，状态保持 TODO。
     *
     * - `+1w` / `++1w`：从原定日期往后推，推到严格晚于完成日为止（补上漏掉的周期）
     * - `.+3d`：从完成日往后推
     *
     * 不是重复任务时返回 null，调用方按普通任务处理。
     */
    fun advanceRepeat(target: Headline, completedOn: LocalDate): OrgDocument? {
        val spec = target.scheduled ?: return null
        val repeater = spec.repeater ?: return null
        val match = REPEATER_RE.matchEntire(repeater) ?: return null

        val prefix = match.groupValues[1]
        val count = match.groupValues[2].toInt()
        val unit = match.groupValues[3]

        var next = if (prefix == ".+") {
            shift(completedOn, count, unit)
        } else {
            var cursor = shift(spec.date, count, unit)
            var guard = 0
            while (!cursor.isAfter(completedOn) && guard++ < 500) {
                cursor = shift(cursor, count, unit)
            }
            cursor
        }
        if (next == spec.date) next = shift(next, count, unit)

        return setScheduled(target, spec.copy(date = next))
    }

    /** 在文件末尾新建清单；若已有 `* 已删除`，插在它前面，避免新清单掉进回收站。 */
    fun appendList(name: String): OrgDocument {
        val out = lines.toMutableList()
        val nl = newline(out)
        val at = trashStart(out) ?: out.size
        ensureNewlineBefore(out, at)
        out.add(at, "* $name$nl")
        return OrgDocument(out)
    }

    /** 删除清单：整棵子树降一级，挂到文末的 `* 已删除` 下，并记录删除时间。 */
    fun archiveList(target: Headline, now: LocalDateTime): OrgDocument {
        if (target.level != 1) return this

        val block = lines.subList(target.lineIndex, target.endExclusive).toList()
        if (block.isEmpty()) return this

        val nl = newline(lines)
        val demoted = block.map { demoteLine(it) }

        val rest = lines.filterIndexed { i, _ -> i < target.lineIndex || i >= target.endExclusive }
        val out = rest.toMutableList()

        if (trashStart(out) == null) {
            ensureNewlineBefore(out, out.size)
            out += "* 已删除$nl"
        }

        ensureNewlineBefore(out, out.size)
        out += demoted.first()
        out += "  :PROPERTIES:$nl"
        out += "  :DELETED_AT: ${OrgTime.formatClosed(now)}$nl"
        out += "  :END:$nl"
        out += demoted.drop(1)
        return OrgDocument(out)
    }

    /** 相邻顶层清单上下移动，[delta] 只接受 ±1。 */
    fun moveList(target: Headline, delta: Int): OrgDocument {
        if (target.level != 1 || delta != 1 && delta != -1) return this

        val lists = entries.filter { it.level == 1 }
        val index = lists.indexOfFirst { it.lineIndex == target.lineIndex }
        val other = index + delta
        if (index < 0 || other !in lists.indices) return this

        val first = lists[minOf(index, other)]
        val second = lists[maxOf(index, other)]
        if (first.endExclusive != second.lineIndex) return this

        val out = ArrayList<String>(lines.size)
        out += lines.subList(0, first.lineIndex)
        out += lines.subList(second.lineIndex, second.endExclusive)
        out += lines.subList(first.lineIndex, first.endExclusive)
        out += lines.subList(second.endExclusive, lines.size)
        return OrgDocument(out)
    }

    // ---------------------------------------------------------------- 内部

    private fun removePlanning(out: MutableList<String>, target: Headline, keyword: String) {
        val limit = minOf(target.bodyStart, target.endExclusive, out.size)
        for (i in (limit - 1) downTo (target.lineIndex + 1)) {
            val text = lineText(out[i])
            val hits = planningTokens(text)
            if (hits.none { it.first.equals(keyword, true) }) continue
            val rebuilt = rebuildPlanning(text, remove = keyword)
            if (rebuilt.isNullOrBlank()) out.removeAt(i) else out[i] = rewriteLine(out[i], rebuilt)
        }
    }

    private fun planningIndent(out: List<String>, target: Headline): String {
        val limit = minOf(target.bodyStart, target.endExclusive, out.size)
        for (i in target.lineIndex + 1 until limit) {
            val text = lineText(out[i])
            if (planningTokens(text).isNotEmpty()) {
                return text.takeWhile { it == ' ' || it == '\t' }
            }
        }
        return ""
    }

    private fun findPropertiesDrawer(out: List<String>, target: Headline): Pair<Int, Int>? {
        val limit = minOf(target.bodyStart, target.endExclusive, out.size)
        var start = -1
        for (i in target.lineIndex + 1 until limit) {
            val text = lineText(out[i]).trim()
            if (text.isEmpty()) break
            if (text.equals(":PROPERTIES:", true)) {
                start = i
                break
            }
        }
        if (start < 0) return null
        for (j in start + 1 until limit) {
            if (lineText(out[j]).trim().equals(":END:", true)) return start to j
        }
        return null
    }

    private fun headlineText(
        h: Headline,
        keyword: String? = h.keyword,
        title: String = h.title,
        priority: Char? = h.priority,
    ): String = buildString {
        append("*".repeat(h.level)).append(' ')
        keyword?.let { append(it).append(' ') }
        priority?.let { append("[#").append(it).append("] ") }
        append(title)
        if (h.tags.isNotEmpty()) {
            if (title.isNotEmpty()) append(' ')
            append(h.tags.joinToString(":", ":", ":"))
        }
    }

    companion object {
        fun parse(text: String): OrgDocument = OrgDocument(splitLines(text))
    }
}

// -------------------------------------------------------------------- 解析

private val HEADLINE_RE = Regex("""^(\*+)[ \t]+(.*)$""")
private val KEYWORD_RE = Regex("""^([A-Z][A-Z0-9]*)(?:[ \t]+|$)""")
private val PRIORITY_RE = Regex("""^\[#([A-Z])\][ \t]*""")
private val TAGS_RE = Regex("""(?:^|[ \t]+)(:(?:[^\s:]+:)+)$""")
private val PLANNING_TOKEN_RE = Regex("""(SCHEDULED|DEADLINE|CLOSED):\s*(<[^>]*>|\[[^\]]*\])""")
private val PLANNING_LINE_RE = Regex("""^\s*(SCHEDULED|DEADLINE|CLOSED):""")
private val DRAWER_RE = Regex("""^:[A-Za-z0-9_\-]+:$""")
private val PROPERTY_RE = Regex("""^\s*:([A-Za-z0-9_\-]+):\s*(.*)$""")
private val FILE_KEYWORDS_RE = Regex("""^#\+(?:SEQ_)?TODO:\s*(.*)$""", RegexOption.IGNORE_CASE)
private val TS_RE = Regex(
    """^(\d{4})-(\d{2})-(\d{2})(?:\s+[A-Za-z]{3})?(?:\s+(\d{1,2}):(\d{2}))?(?:\s+((?:\+\+?|\.\+)\d+[hdwmy]))?(?:\s+(-\d+[hdwmy]))?$""",
)

private fun directTaskChildren(entries: List<Headline>, parent: Headline): List<Headline> =
    entries.filter {
        it.level == parent.level + 1 &&
            it.lineIndex > parent.lineIndex &&
            it.lineIndex < parent.endExclusive &&
            it.keyword != null
    }

private fun nearestAncestor(entries: List<Headline>, target: Headline): Headline? =
    entries
        .filter { it.level < target.level && it.lineIndex < target.lineIndex && target.lineIndex < it.endExclusive }
        .maxByOrNull { it.level }

private fun splitLines(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val out = ArrayList<String>()
    var start = 0
    for (i in text.indices) {
        if (text[i] == '\n') {
            out += text.substring(start, i + 1)
            start = i + 1
        }
    }
    if (start < text.length) out += text.substring(start)
    return out
}

private fun lineText(raw: String): String = raw.trimEnd('\r', '\n')

private fun terminatorOf(raw: String): String = when {
    raw.endsWith("\r\n") -> "\r\n"
    raw.endsWith("\n") -> "\n"
    else -> ""
}

private fun rewriteLine(original: String, newText: String): String = newText + terminatorOf(original)

private fun newline(lines: List<String>): String =
    if (lines.any { it.endsWith("\r\n") }) "\r\n" else "\n"

private fun ensureNewlineBefore(out: MutableList<String>, index: Int) {
    if (index in 1..out.size) {
        val prev = out[index - 1]
        if (!prev.endsWith("\n")) out[index - 1] = prev + newline(out)
    }
}

private fun keywordSet(lines: List<String>): Set<String> {
    val keywords = linkedSetOf("TODO", "DONE")
    for (raw in lines) {
        val match = FILE_KEYWORDS_RE.find(lineText(raw).trim()) ?: continue
        match.groupValues[1]
            .split('|')
            .flatMap { it.split(' ', '\t') }
            .map { it.trim() }
            .filter { it.matches(Regex("[A-Za-z][A-Za-z0-9]*")) }
            .forEach { keywords += it }
    }
    return keywords
}

private fun parseEntries(lines: List<String>): List<Headline> {
    val keywords = keywordSet(lines)

    val rough = ArrayList<Headline>()
    for (i in lines.indices) {
        val parsed = parseHeadline(i, lineText(lines[i]), keywords) ?: continue
        rough += parsed
    }

    return rough.mapIndexed { index, headline ->
        val end = rough.drop(index + 1).firstOrNull { it.level <= headline.level }?.lineIndex ?: lines.size
        parseBody(lines, headline.copy(endExclusive = end))
    }
}

private fun parseHeadline(lineIndex: Int, text: String, keywords: Set<String>): Headline? {
    val match = HEADLINE_RE.find(text) ?: return null
    val level = match.groupValues[1].length
    var content = match.groupValues[2].trim()

    var keyword: String? = null
    KEYWORD_RE.find(content)?.let { kw ->
        val candidate = kw.groupValues[1]
        if (candidate in keywords) {
            keyword = candidate
            content = content.substring(kw.range.last + 1).trimStart()
        }
    }

    var priority: Char? = null
    PRIORITY_RE.find(content)?.let { p ->
        priority = p.groupValues[1][0]
        content = content.substring(p.range.last + 1).trimStart()
    }

    var tags: List<String> = emptyList()
    TAGS_RE.find(content)?.let { t ->
        tags = t.groupValues[1].trim(':').split(':').filter { it.isNotEmpty() }
        content = content.substring(0, t.range.first).trimEnd()
    }

    return Headline(
        lineIndex = lineIndex,
        endExclusive = lineIndex,
        level = level,
        keyword = keyword,
        priority = priority,
        title = content.trim(),
        tags = tags,
        scheduled = null,
        deadline = null,
        closed = null,
        properties = emptyMap(),
        bodyStart = lineIndex,
    )
}

private fun parseBody(lines: List<String>, headline: Headline): Headline {
    var scheduled: DateSpec? = null
    var deadline: DateSpec? = null
    var closed: LocalDateTime? = null
    val properties = LinkedHashMap<String, String>()
    var bodyStart = headline.endExclusive

    var i = headline.lineIndex + 1
    while (i < headline.endExclusive) {
        val text = lineText(lines[i])
        val trimmed = text.trim()

        if (trimmed.isEmpty()) {
            bodyStart = i
            break
        }

        if (trimmed.equals(":PROPERTIES:", ignoreCase = true)) {
            var j = i + 1
            while (j < headline.endExclusive) {
                val inner = lineText(lines[j]).trim()
                if (inner.equals(":END:", ignoreCase = true)) break
                PROPERTY_RE.find(inner)?.let { properties[it.groupValues[1]] = it.groupValues[2].trim() }
                j++
            }
            i = if (j < headline.endExclusive) j + 1 else headline.endExclusive
            continue
        }

        if (DRAWER_RE.matches(trimmed)) {
            var j = i + 1
            while (j < headline.endExclusive && !lineText(lines[j]).trim().equals(":END:", ignoreCase = true)) j++
            i = if (j < headline.endExclusive) j + 1 else headline.endExclusive
            continue
        }

        if (PLANNING_LINE_RE.containsMatchIn(text)) {
            for (token in planningTokens(text)) {
                when (token.first.uppercase()) {
                    "SCHEDULED" -> scheduled = parseSpec(token.second) ?: scheduled
                    "DEADLINE" -> deadline = parseSpec(token.second) ?: deadline
                    "CLOSED" -> closed = parseClosed(token.second) ?: closed
                }
            }
            i++
            continue
        }

        bodyStart = i
        break
    }

    return headline.copy(
        scheduled = scheduled,
        deadline = deadline,
        closed = closed,
        properties = properties,
        bodyStart = bodyStart,
    )
}

/** 一行里的计划 token，返回 (关键字, 原始时间戳)。 */
private fun planningTokens(text: String): List<Pair<String, String>> =
    PLANNING_TOKEN_RE.findAll(text).map { it.groupValues[1] to it.groupValues[2] }.toList()

/**
 * 重建计划行：替换或删除某个 token，其余原样保留。
 * 返回 null 表示这一行已经没有计划 token 了，应当整行删除。
 */
private fun rebuildPlanning(
    text: String,
    replace: Pair<String, String>? = null,
    remove: String? = null,
): String? {
    val tokens = planningTokens(text)
    if (tokens.isEmpty()) return null

    val indent = text.takeWhile { it == ' ' || it == '\t' }
    val parts = ArrayList<String>(tokens.size)
    for ((key, value) in tokens) {
        if (remove != null && key.equals(remove, true)) continue
        val newValue = if (replace != null && key.equals(replace.first, true)) replace.second else value
        parts += "$key: $newValue"
    }
    if (parts.isEmpty()) return null
    return indent + parts.joinToString(" ")
}

private fun parseSpec(raw: String): DateSpec? {
    if (raw.length < 3) return null
    val active = raw.startsWith("<")
    val inner = raw.substring(1, raw.length - 1).trim()
    val match = TS_RE.matchEntire(inner) ?: return null

    return runCatching {
        DateSpec(
            date = LocalDate.of(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
            ),
            time = if (match.groupValues[4].isEmpty()) {
                null
            } else {
                LocalTime.of(match.groupValues[4].toInt(), match.groupValues[5].toInt())
            },
            repeater = match.groupValues[6].ifEmpty { null },
            warning = match.groupValues[7].ifEmpty { null },
            active = active,
        )
    }.getOrNull()
}

private fun parseClosed(raw: String): LocalDateTime? {
    if (raw.length < 3) return null
    val inner = raw.substring(1, raw.length - 1).trim()
    val match = TS_RE.matchEntire(inner) ?: return null
    if (match.groupValues[4].isEmpty()) return null

    return runCatching {
        LocalDateTime.of(
            LocalDate.of(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
            ),
            LocalTime.of(match.groupValues[4].toInt(), match.groupValues[5].toInt()),
        )
    }.getOrNull()
}

private val REPEATER_RE = Regex("""^(\+\+|\.\+|\+)(\d+)([dwmy])$""")

private fun shift(date: LocalDate, count: Int, unit: String): LocalDate = when (unit) {
    "d" -> date.plusDays(count.toLong())
    "w" -> date.plusWeeks(count.toLong())
    "m" -> date.plusMonths(count.toLong())
    "y" -> date.plusYears(count.toLong())
    else -> date
}

private fun trashStart(lines: List<String>): Int? =
    lines.indexOfFirst { lineText(it).trim() == "* 已删除" }.takeIf { it >= 0 }

private fun demoteLine(raw: String): String {
    val text = lineText(raw)
    val stars = text.takeWhile { it == '*' }.length
    if (stars == 0 || stars == text.length || text[stars] != ' ') return raw
    return "*" + raw
}
