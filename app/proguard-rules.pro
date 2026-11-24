# 目前不开启混淆；发布版一旦开启，保留 orgmode 的公开 API。
# -keep class dev.orgtd.orgmode.** { *; }

# WorkManager 通过反射实例化 Worker，需要保留构造函数
-keep class dev.orgtd.sync.SyncWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
