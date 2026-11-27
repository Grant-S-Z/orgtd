package dev.orgtd.widget

import android.content.Context
import dev.orgtd.Strings
import dev.orgtd.TaskMapper
import dev.orgtd.TaskMutations
import dev.orgtd.TaskUi
import dev.orgtd.data.OrgPrefs
import dev.orgtd.data.Sources
import dev.orgtd.reminder.ReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WidgetTask(
    val line: Int,
    val title: String,
    val time: String?,
    val overdue: Boolean,
)

data class WidgetState(
    val tasks: List<WidgetTask> = emptyList(),
    val error: String? = null,
)

/** 桌面组件的数据加载与写操作，复用 App 的存储层和 TaskMutations。 */
object WidgetData {

    const val MAX_ROWS = 4
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun load(context: Context): WidgetState = withContext(Dispatchers.IO) {
        runCatching {
            val source = Sources.create(context) ?: error("no source")
            val snapshot = source.read() ?: return@runCatching WidgetState()

            val tasks = TaskMapper.toTasks(dev.orgtd.orgmode.OrgDocument.parse(snapshot.text))
            val today = LocalDate.now()
            val rows = tasks
                .filter { task ->
                    !task.done && !task.archived && !task.projectCompleted &&
                        task.date != null && !task.date.isAfter(today)
                }
                .sortedWith(
                    compareBy<TaskUi> { it.date ?: LocalDate.MAX }
                        .thenBy { it.time ?: LocalTime.MAX },
                )
                .take(MAX_ROWS)
                .map { task ->
                    WidgetTask(
                        line = task.line,
                        title = task.title,
                        time = task.time?.format(TIME),
                        overdue = task.date?.isBefore(today) == true,
                    )
                }
            WidgetState(rows)
        }.getOrElse { WidgetState(error = it.message) }
    }

    /** 在组件上勾选任务；失败时返回 false，组件会显示错误。 */
    suspend fun toggle(context: Context, line: Int, title: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val source = Sources.create(context) ?: error("no source")
            val snapshot = source.read() ?: error("cannot read")
            val doc = dev.orgtd.orgmode.OrgDocument.parse(snapshot.text)

            // 行号可能已过期，标题对不上就放弃，避免改错任务
            val entry = doc.entryAt(line) ?: return@runCatching false
            if (entry.title != title) return@runCatching false

            val next = TaskMutations.toggle(doc, line, LocalDateTime.now())
            val text = next.serialize()
            if (text != snapshot.text) source.write(text, snapshot, snapshot.text)
            ReminderScheduler.sync(context, TaskMapper.toTasks(next))
            true
        }.getOrDefault(false)
    }

    fun header(context: Context): String {
        val strings = Strings.resolve(OrgPrefs(context).language)
        return "orgtd · ${strings.today}"
    }
}
