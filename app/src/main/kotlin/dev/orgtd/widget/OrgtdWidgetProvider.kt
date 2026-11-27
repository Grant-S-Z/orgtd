package dev.orgtd.widget

import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OrgtdWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context)
    }

    private fun refresh(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRenderer.refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "dev.orgtd.widget.TOGGLE"
        const val ACTION_REFRESH = "dev.orgtd.widget.REFRESH"
        const val EXTRA_LINE = "line"
        const val EXTRA_TITLE = "title"
    }
}
