package dev.orgtd

import dev.orgtd.orgmode.Headline
import dev.orgtd.orgmode.OrgDocument
import java.time.LocalDateTime
import java.util.regex.Pattern

/** org 文档到界面模型，View 层与后台（开机重排、通知动作）共用。 */
object TaskMapper {

    fun toTasks(doc: OrgDocument): List<TaskUi> {
        val lists = doc.entries.filter { it.isList }
        return doc.entries.filter { it.isTask }.map { headline ->
            TaskUi(
                line = headline.lineIndex,
                title = headline.title,
                done = headline.isDone,
                date = headline.date,
                time = headline.scheduled?.time,
                repeater = headline.scheduled?.repeater,
                priority = headline.priority,
                tags = headline.tags,
                listName = owningList(lists, headline.lineIndex),
                level = headline.level,
                closed = headline.closed,
                children = childrenOf(doc, headline).size,
                childrenDone = childrenOf(doc, headline).count { it.isDone },
                reminderAt = reminderAt(headline),
                body = bodyText(doc, headline),
                archived = headline.properties.containsKey("ARCHIVED_AT"),
            )
        }
    }

    fun toLists(doc: OrgDocument, tasks: List<TaskUi>): List<ListUi> =
        doc.entries.filter { it.isList }.map { list ->
            val open = tasks.count {
                !it.done && it.line > list.lineIndex && it.line < list.endExclusive
            }
            val done = tasks.count {
                it.done && it.line > list.lineIndex && it.line < list.endExclusive
            }
            ListUi(list.lineIndex, list.title, open, done)
        }

    private fun childrenOf(doc: OrgDocument, parent: Headline): List<Headline> =
        doc.entries.filter {
            it.level == parent.level + 1 &&
                it.lineIndex > parent.lineIndex &&
                it.lineIndex < parent.endExclusive &&
                it.keyword != null
        }

    private val PLANNING = Pattern.compile("^\\s*(SCHEDULED|DEADLINE|CLOSED):")

    /** 正文用于搜索：去掉抽屉、计划行和空行，其余拼成一段。 */
    private fun bodyText(doc: OrgDocument, headline: Headline): String {
        val from = headline.bodyStart.coerceAtLeast(headline.lineIndex + 1)
        val to = headline.endExclusive.coerceAtMost(doc.lines.size)
        if (from >= to) return ""

        val parts = ArrayList<String>()
        for (i in from until to) {
            val text = doc.lines[i].trimEnd('\r', '\n').trim()
            if (text.isEmpty()) continue
            if (text.startsWith("*")) continue
            if (text.startsWith(":") && text.endsWith(":") && text.length > 2) continue
            if (PLANNING.matcher(text).find()) continue
            parts += text
        }
        return parts.joinToString("\n")
    }

    /** 只对带时刻的计划行提醒：优先 SCHEDULED，其次 DEADLINE。 */
    private fun reminderAt(headline: Headline): LocalDateTime? {
        headline.scheduled?.let { spec ->
            spec.time?.let { return LocalDateTime.of(spec.date, it) }
        }
        headline.deadline?.let { spec ->
            spec.time?.let { return LocalDateTime.of(spec.date, it) }
        }
        return null
    }

    private fun owningList(lists: List<Headline>, line: Int): String? =
        lists.filter { it.lineIndex < line && line < it.endExclusive }.maxByOrNull { it.lineIndex }?.title
}
