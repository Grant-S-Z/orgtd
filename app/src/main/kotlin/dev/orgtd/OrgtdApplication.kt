package dev.orgtd

import android.app.Application
import dev.orgtd.data.OrgPrefs
import dev.orgtd.data.StorageMode
import dev.orgtd.sync.SyncScheduler

class OrgtdApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.sync(this, OrgPrefs(this).mode == StorageMode.WEBDAV)
    }
}
