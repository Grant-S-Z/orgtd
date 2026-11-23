package dev.orgtd.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 通知上的「完成 / 推迟」动作。写文件必须成功，失败就不清通知。 */
class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_COMPLETE = "dev.orgtd.action.COMPLETE"
        const val ACTION_POSTPONE = "dev.orgtd.action.POSTPONE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: return
        val id = intent.getIntExtra(ReminderScheduler.EXTRA_ID, title.hashCode())
        val action = intent.action ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val changed = when (action) {
                    ACTION_COMPLETE -> ReminderActions.complete(context, title)
                    ACTION_POSTPONE -> ReminderActions.postpone(context, title)
                    else -> false
                }
                if (changed) ReminderNotifier.cancel(context, id)
            } finally {
                pending.finish()
            }
        }
    }
}
