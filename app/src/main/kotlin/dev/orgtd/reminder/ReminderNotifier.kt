package dev.orgtd.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.orgtd.MainActivity
import dev.orgtd.R
import dev.orgtd.Strings
import dev.orgtd.data.OrgPrefs
import java.time.LocalDateTime

object ReminderNotifier {

    private const val CHANNEL_ID = "reminders"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val strings = Strings.resolve(OrgPrefs(context).language)
        val channel = NotificationChannel(
            CHANNEL_ID,
            strings.channelName,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = strings.channelDescription
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, id: Int, title: String, time: String?) {
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val strings = Strings.resolve(OrgPrefs(context).language)
        val readableTime = time?.let { raw ->
            runCatching { LocalDateTime.parse(raw) }.getOrNull()?.let(strings::formatReminderTime) ?: raw
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(readableTime ?: strings.reminderFallback)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, strings.actionComplete, action(context, id, ReminderActionReceiver.ACTION_COMPLETE, title))
            .addAction(0, strings.actionPostpone, action(context, id, ReminderActionReceiver.ACTION_POSTPONE, title))
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    private fun action(context: Context, id: Int, action: String, title: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id * 31 + action.hashCode(),
            Intent(context, ReminderActionReceiver::class.java).apply {
                this.action = action
                putExtra(ReminderScheduler.EXTRA_ID, id)
                putExtra(ReminderScheduler.EXTRA_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
