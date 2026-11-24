package dev.orgtd

import android.app.Application
import dev.orgtd.sync.SyncScheduler

class OrgtdApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.ensureScheduled(this)
    }
}
