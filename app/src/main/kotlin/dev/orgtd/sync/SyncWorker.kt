package dev.orgtd.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.orgtd.TaskMapper
import dev.orgtd.data.Sources
import dev.orgtd.orgmode.OrgDocument
import dev.orgtd.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 后台同步：读一次远端文件（顺带刷新离线缓存），再按最新内容重排闹钟。
 *
 * 不在后台写文件，只读；写操作仍然只发生在用户操作时。
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val source = Sources.create(applicationContext) ?: return@withContext Result.success()
            val snapshot = source.read() ?: return@withContext Result.success()
            val tasks = TaskMapper.toTasks(OrgDocument.parse(snapshot.text))
            ReminderScheduler.sync(applicationContext, tasks)
            dev.orgtd.widget.WidgetRenderer.refreshAll(applicationContext)
            Result.success()
        } catch (error: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
