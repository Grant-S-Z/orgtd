package dev.orgtd

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.orgtd.UiStyle
import dev.orgtd.data.DarkMode
import dev.orgtd.ui.LocalAppleStyle
import dev.orgtd.ui.LocalStrings
import dev.orgtd.ui.OrgtdApp
import dev.orgtd.ui.theme.OrgtdTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: TaskViewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            val darkTheme = when (state.settings.darkMode) {
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
                DarkMode.SYSTEM -> isSystemInDarkTheme()
            }

            val appleStyle = state.settings.uiStyle == UiStyle.APPLE
            val themeId = if (appleStyle) "apple" else state.settings.themeId

            OrgtdTheme(themeId = themeId, darkTheme = darkTheme) {
                val context = LocalContext.current

                val picker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree(),
                ) { uri: Uri? ->
                    if (uri != null) {
                        runCatching {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                            )
                        }
                        viewModel.openTree(uri)
                    }
                }

                val notificationPermission = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { }

                LaunchedEffect(Unit) {
                    // Android 13+ 才需要运行时申请，HarmonyOS 4.2（Android 12 底座）不会触发
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val alarmManager = context.getSystemService(AlarmManager::class.java)
                fun exactAlarmGranted(): Boolean =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true

                var exactGranted by remember { mutableStateOf(exactAlarmGranted()) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) exactGranted = exactAlarmGranted()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val strings = remember(state.settings.language) {
                    Strings.resolve(state.settings.language)
                }

                CompositionLocalProvider(
                    LocalStrings provides strings,
                    LocalAppleStyle provides appleStyle,
                ) {
                OrgtdApp(
                    state = state,
                    fileName = viewModel.fileName,
                    exactAlarmGranted = exactGranted,
                    onRequestExactAlarm = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching {
                                startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData("package:$packageName".toUri()),
                                )
                            }
                        }
                    },
                    onPickFolder = { picker.launch(null) },
                    onCreateInternalFile = viewModel::createInternalFile,
                    onExportFile = viewModel::exportContent,
                    onCreateFileInFolder = viewModel::createFileInCurrentFolder,
                    onUseInternal = viewModel::useInternal,
                    onRefresh = viewModel::refresh,
                    onToggle = viewModel::toggle,
                    onAddTask = viewModel::addTask,
                    onAddTaskToList = viewModel::addTaskToList,
                    onUpdateTask = viewModel::updateTask,
                    onPostponeTask = viewModel::postponeTask,
                    onArchiveTask = viewModel::archiveTask,
                    onUnarchiveTask = viewModel::unarchiveTask,
                    onMoveTask = viewModel::moveTask,
                    onArchiveAllCompleted = viewModel::archiveAllCompleted,
                    onDeleteTask = viewModel::deleteTask,
                    onUndoDelete = viewModel::undoDelete,
                    onAddList = viewModel::addList,
                    onRenameList = viewModel::renameList,
                    onDeleteList = viewModel::deleteList,
                    onMoveList = viewModel::moveList,
                    onSaveWebDav = viewModel::saveWebDav,
                    onSetTheme = viewModel::setTheme,
                    onSetDarkMode = viewModel::setDarkMode,
                    onSetLanguage = viewModel::setLanguage,
                    onSetUiStyle = viewModel::setUiStyle,
                    onSetRemindDateOnly = viewModel::setRemindDateOnly,
                    onSetFileName = viewModel::setFileName,
                    onDismissError = viewModel::clearError,
                )
                }
            }
        }
    }
}
