package dev.orgtd.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import dev.orgtd.TaskUi
import dev.orgtd.data.OrgPrefs
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 把「带时刻的任务」同步成系统闹钟。
 *
 * 闹钟集合独立于界面存在：每次同步先撤销上一批，再按当前任务重建；
 * 开机、改时区、文件变化后都会走同一条 [sync]。
 */
object ReminderScheduler {

    const val EXTRA_ID = "reminder_id"
    const val EXTRA_TITLE = "reminder_title"
    const val EXTRA_TIME = "reminder_time"

    private const val PREFS = "orgtd.alarms"
    private const val KEY_CODES = "codes"

    fun sync(context: Context, tasks: List<TaskUi>) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        prefs.getStringSet(KEY_CODES, emptySet())?.forEach { raw ->
            runCatching { cancel(context, raw.toInt()) }
        }

        val now = System.currentTimeMillis()
        val scheduled = mutableSetOf<String>()
        val defaultTime = if (OrgPrefs(context).remindDateOnly) LocalTime.of(9, 0) else null

        tasks.forEach { task ->
            if (task.done) return@forEach
            val at = task.reminderAt
                ?: task.date?.let { date -> defaultTime?.let { LocalDateTime.of(date, it) } }
                ?: return@forEach
            val trigger = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (trigger <= now) return@forEach

            val code = code(task)
            schedule(context, manager, code, trigger, task)
            scheduled += code.toString()
        }

        prefs.edit { putStringSet(KEY_CODES, scheduled) }
    }

    fun code(task: TaskUi): Int = "${task.title}|${task.reminderAt}".hashCode()

    private fun schedule(context: Context, manager: AlarmManager, code: Int, trigger: Long, task: TaskUi) {
        val pending = pendingIntent(context, code, task)
        val exact = android.os.Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

        if (exact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }

    private fun cancel(context: Context, code: Int) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pendingIntent(context, code, null))
    }

    private fun pendingIntent(context: Context, code: Int, task: TaskUi?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ID, code)
            if (task != null) {
                putExtra(EXTRA_TITLE, task.title)
                putExtra(EXTRA_TIME, task.reminderAt?.toString())
            }
        }
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
