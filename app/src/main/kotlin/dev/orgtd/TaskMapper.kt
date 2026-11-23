package dev.orgtd

import dev.orgtd.orgmode.Headline
import dev.orgtd.orgmode.OrgDocument
import java.time.LocalDateTime

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
                children = doc.entries.count {
                    it.level == headline.level + 1 &&
                        it.lineIndex > headline.lineIndex &&
                        it.lineIndex < headline.endExclusive
                },
                reminderAt = reminderAt(headline),
            )
        }
    }

    fun toLists(doc: OrgDocument, tasks: List<TaskUi>): List<ListUi> =
        doc.entries.filter { it.isList }.map { list ->
            val open = tasks.count {
                !it.done && it.line > list.lineIndex && it.line < list.endExclusive
            }
            ListUi(list.lineIndex, list.title, open)
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
