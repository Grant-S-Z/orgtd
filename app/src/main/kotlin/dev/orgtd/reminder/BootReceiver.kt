package dev.orgtd.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.orgtd.TaskMapper
import dev.orgtd.data.Sources
import dev.orgtd.orgmode.OrgDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 开机、改时区、改时间、覆盖安装后重排闹钟。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = Sources.create(context)?.read()?.text ?: return@launch
                ReminderScheduler.sync(context, TaskMapper.toTasks(OrgDocument.parse(text)))
            } finally {
                pending.finish()
            }
        }
    }
}
