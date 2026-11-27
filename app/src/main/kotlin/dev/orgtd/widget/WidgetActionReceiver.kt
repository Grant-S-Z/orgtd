package dev.orgtd.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 组件内部的点击动作（勾选/刷新）。不导出，外部应用无法伪造。 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            OrgtdWidgetProvider.ACTION_TOGGLE -> {
                val line = intent.getIntExtra(OrgtdWidgetProvider.EXTRA_LINE, -1)
                val title = intent.getStringExtra(OrgtdWidgetProvider.EXTRA_TITLE).orEmpty()
                if (line < 0) return
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        WidgetData.toggle(context, line, title)
                        WidgetRenderer.refreshAll(context)
                    } finally {
                        pending.finish()
                    }
                }
            }

            OrgtdWidgetProvider.ACTION_REFRESH -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        WidgetRenderer.refreshAll(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
