package dev.orgtd.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: return
        val id = intent.getIntExtra(ReminderScheduler.EXTRA_ID, title.hashCode())
        ReminderNotifier.show(context, id, title, intent.getStringExtra(ReminderScheduler.EXTRA_TIME))
    }
}
