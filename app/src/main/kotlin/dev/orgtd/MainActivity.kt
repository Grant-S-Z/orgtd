package dev.orgtd

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.orgtd.ui.OrgtdApp
import dev.orgtd.ui.theme.OrgtdTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            OrgtdTheme {
                val viewModel: TaskViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()

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

                OrgtdApp(
                    state = state,
                    fileName = viewModel.fileName,
                    onPickFolder = { picker.launch(null) },
                    onRefresh = viewModel::refresh,
                    onToggle = viewModel::toggle,
                    onAddTask = viewModel::addTask,
                    onUpdateTask = viewModel::updateTask,
                    onDeleteTask = viewModel::deleteTask,
                    onUndoDelete = viewModel::undoDelete,
                    onDismissError = viewModel::clearError,
                )
            }
        }
    }
}
