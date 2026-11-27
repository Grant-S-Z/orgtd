package dev.orgtd.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.orgtd.MainActivity
import dev.orgtd.R
import dev.orgtd.Strings
import dev.orgtd.data.OrgPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 把 WidgetState 渲染到所有桌面组件实例上。 */
object WidgetRenderer {

    private val ROWS = intArrayOf(R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3)
    private val TITLES = intArrayOf(R.id.widget_title_0, R.id.widget_title_1, R.id.widget_title_2, R.id.widget_title_3)

    suspend fun refreshAll(context: Context) {
        val state = WidgetData.load(context)
        withContext(Dispatchers.Main) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, OrgtdWidgetProvider::class.java))
            ids.forEach { id -> manager.updateAppWidget(id, build(context, id, state)) }
        }
    }

    private fun build(context: Context, widgetId: Int, state: WidgetState): RemoteViews {
        val strings = Strings.resolve(OrgPrefs(context).language)
        val views = RemoteViews(context.packageName, R.layout.orgtd_widget)

        views.setTextViewText(R.id.widget_title, WidgetData.header(context))
        views.setOnClickPendingIntent(R.id.widget_title, openApp(context, widgetId * 10 + 9))
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            PendingIntent.getBroadcast(
                context,
                widgetId * 10 + 5,
                Intent(context, WidgetActionReceiver::class.java).setAction(OrgtdWidgetProvider.ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        state.error?.let {
            views.setViewVisibility(R.id.widget_error, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_error, "${strings.widgetError}: $it")
            views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
            ROWS.forEach { views.setViewVisibility(it, android.view.View.GONE) }
            return views
        }

        views.setViewVisibility(R.id.widget_error, android.view.View.GONE)
        views.setViewVisibility(R.id.widget_empty, if (state.tasks.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE)
        views.setTextViewText(R.id.widget_empty, strings.noTasksToday)

        ROWS.forEachIndexed { index, rowId ->
            val task = state.tasks.getOrNull(index)
            if (task == null) {
                views.setViewVisibility(rowId, android.view.View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(rowId, android.view.View.VISIBLE)
            val label = buildString {
                if (task.overdue) append("! ")
                task.time?.let { append(it).append(' ') }
                append(task.title)
            }
            views.setTextViewText(TITLES[index], label)
            views.setOnClickPendingIntent(
                rowId,
                PendingIntent.getBroadcast(
                    context,
                    widgetId * 10 + index,
                    Intent(context, WidgetActionReceiver::class.java)
                        .setAction(OrgtdWidgetProvider.ACTION_TOGGLE)
                        .putExtra(OrgtdWidgetProvider.EXTRA_LINE, task.line)
                        .putExtra(OrgtdWidgetProvider.EXTRA_TITLE, task.title),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        return views
    }

    private fun openApp(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
