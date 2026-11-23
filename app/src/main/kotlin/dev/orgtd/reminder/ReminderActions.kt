package dev.orgtd.reminder

import android.content.Context
import dev.orgtd.data.Sources
import dev.orgtd.orgmode.DateSpec
import dev.orgtd.orgmode.OrgDocument
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 不经过 ViewModel 的写操作（通知动作、开机重排共用）。
 *
 * 背景进程里没有界面状态，只按标题找第一条未完成的匹配任务；
 * 找不到就不动文件，返回 false 让调用方保留通知。
 */
object ReminderActions {

    fun complete(context: Context, title: String): Boolean = update(context) { doc ->
        val entry = doc.entries.firstOrNull { it.isTask && !it.isDone && it.title == title }
            ?: return@update null
        val withId = doc.ensureId(entry)
        val target = withId.entryAt(entry.lineIndex) ?: return@update null
        withId.advanceRepeat(target, LocalDate.now())
            ?: withId.toggleDone(target, LocalDateTime.now())
    }

    fun postpone(context: Context, title: String): Boolean = update(context) { doc ->
        val entry = doc.entries.firstOrNull { it.isTask && !it.isDone && it.title == title }
            ?: return@update null
        val withId = doc.ensureId(entry)
        val target = withId.entryAt(entry.lineIndex) ?: return@update null

        val old = target.scheduled
        val spec = DateSpec(
            date = LocalDate.now().plusDays(1),
            time = old?.time,
            repeater = old?.repeater,
            warning = old?.warning,
            active = old?.active ?: true,
        )
        withId.setScheduled(target, spec)
    }

    private fun update(context: Context, transform: (OrgDocument) -> OrgDocument?): Boolean {
        val source = Sources.create(context) ?: return false

        return runCatching {
            val snapshot = source.read() ?: return false
            val next = transform(OrgDocument.parse(snapshot.text)) ?: return false
            val serialized = next.serialize()
            if (serialized == snapshot.text) return false

            source.write(serialized, snapshot, snapshot.text)
            true
        }.getOrDefault(false)
    }
}
