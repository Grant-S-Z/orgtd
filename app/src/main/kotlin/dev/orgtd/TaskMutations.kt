package dev.orgtd

import dev.orgtd.orgmode.OrgDocument
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 不依赖 Android 的任务写操作，App 的 ViewModel 和桌面组件共用，
 * 避免组件和主界面出现两套行为。
 */
object TaskMutations {

    /** 勾选/取消勾选，保留重复任务与父子级联逻辑。 */
    fun toggle(doc: OrgDocument, line: Int, now: LocalDateTime): OrgDocument {
        val entry = doc.entryAt(line) ?: return doc
        val withId = doc.ensureId(entry)
        val target = withId.entryAt(line) ?: return doc
        val id = target.id ?: return doc

        if (!target.isDone) {
            withId.advanceRepeat(target, now.toLocalDate())?.let { return it }
        }

        val toggled = withId.toggleDone(target, now)
        return if (target.isDone) {
            toggled.reopenAncestorsWithOpenChild(id, now)
        } else {
            toggled.completeDoneAncestors(id, now)
        }
    }

    /** 推迟到指定日期，保留时刻与重复规则。 */
    fun postpone(doc: OrgDocument, line: Int, date: LocalDate): OrgDocument {
        val entry = doc.entryAt(line) ?: return doc
        val withId = doc.ensureId(entry)
        val target = withId.entryAt(line) ?: return doc
        val old = target.scheduled
        val spec = dev.orgtd.orgmode.DateSpec(
            date = date,
            time = old?.time,
            repeater = old?.repeater,
            warning = old?.warning,
            active = old?.active ?: true,
        )
        return withId.setScheduled(target, spec)
    }
}
