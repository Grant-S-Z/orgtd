package dev.orgtd

import dev.orgtd.orgmode.Headline
import dev.orgtd.orgmode.OrgDocument
import java.time.LocalDateTime
import java.util.regex.Pattern

/**
 * org 文档到界面模型，View 层与后台（开机重排、通知动作）共用。
 *
 * 三层结构：清单（顶层无状态词标题）→ 项目（下层无状态词标题）→ 任务（带 TODO/DONE 的标题）。
 * 只有两层时项目为 null，行为和以前一致。
 */
object TaskMapper {

    private val COMPLETED_PROJECT_TAGS = setOf("PROJECT_DONE", "ARCHIVE")

    fun toTasks(doc: OrgDocument): List<TaskUi> {
        val lists = doc.entries.filter { it.isList }
        val projects = doc.entries.filter { it.isProject }
        val archivedRoots = doc.entries.filter {
            it.properties.containsKey("ARCHIVE_TIME") || it.properties.containsKey("ARCHIVED_AT")
        }

        return doc.entries.filter { it.isTask }.map { headline ->
            val list = nearestAncestor(lists, headline.lineIndex)
            val project = nearestAncestor(projects, headline.lineIndex)
            val completed = isCompleted(project) || isCompleted(list)

            TaskUi(
                line = headline.lineIndex,
                title = headline.title,
                done = headline.isDone,
                date = headline.date,
                time = headline.scheduled?.time,
                repeater = headline.scheduled?.repeater,
                warning = headline.scheduled?.warning ?: headline.deadline?.warning,
                priority = headline.priority,
                tags = headline.tags,
                listName = list?.title,
                listLine = list?.lineIndex,
                projectName = project?.title,
                projectLine = project?.lineIndex,
                projectCompleted = completed,
                level = headline.level,
                closed = headline.closed,
                children = childrenOf(doc, headline).size,
                childrenDone = childrenOf(doc, headline).count { it.isDone },
                reminderAt = reminderAt(headline),
                body = bodyText(doc, headline),
                archived = archivedRoots.any {
                    it.lineIndex <= headline.lineIndex && headline.lineIndex < it.endExclusive
                },
            )
        }
    }

    fun toLists(doc: OrgDocument, tasks: List<TaskUi>): List<ListUi> =
        doc.entries.filter { it.isList }.map { list ->
            val scoped = tasks.filter {
                it.line > list.lineIndex && it.line < list.endExclusive &&
                    !it.projectCompleted && !it.archived
            }
            ListUi(list.lineIndex, list.title, scoped.count { !it.done }, scoped.count { it.done })
        }

    fun toGroups(doc: OrgDocument, tasks: List<TaskUi>): List<GroupUi> {
        val lists = doc.entries.filter { it.isList }
        return doc.entries.filter { it.isProject }.map { project ->
            val list = nearestAncestor(lists, project.lineIndex)
            val scoped = tasks.filter { it.projectLine == project.lineIndex && !it.archived }
            GroupUi(
                line = project.lineIndex,
                name = project.title,
                path = list?.let { "${it.title} / ${project.title}" } ?: project.title,
                listName = list?.title,
                listLine = list?.lineIndex,
                open = scoped.count { !it.done },
                done = scoped.count { it.done },
                completed = isCompleted(project),
            )
        }
    }

    private fun nearestAncestor(entries: List<Headline>, line: Int): Headline? =
        entries
            .filter { it.lineIndex < line && line < it.endExclusive }
            .maxByOrNull { it.level }

    private fun isCompleted(headline: Headline?): Boolean =
        headline != null && headline.tags.any { tag ->
            COMPLETED_PROJECT_TAGS.any { it.equals(tag, ignoreCase = true) }
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

    /** 只对带时刻的计划行提醒：优先 SCHEDULED，其次 DEADLINE；有 warning 时提前。 */
    private fun reminderAt(headline: Headline): LocalDateTime? {
        (headline.scheduled ?: headline.deadline)?.let { spec ->
            val time = spec.time ?: return null
            return applyWarning(LocalDateTime.of(spec.date, time), spec.warning)
        }
        return null
    }

    private val WARNING = Regex("^(-+)(\\d+)([hdm])$")

    private fun applyWarning(base: LocalDateTime, warning: String?): LocalDateTime {
        val match = warning?.trim()?.let { WARNING.find(it) } ?: return base
        val amount = match.groupValues[2].toLongOrNull() ?: return base
        return when (match.groupValues[3]) {
            "h" -> base.minusHours(amount)
            "d" -> base.minusDays(amount)
            else -> base.minusMinutes(amount)
        }
    }
}
