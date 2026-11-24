package dev.orgtd.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.orgtd.Language
import dev.orgtd.UiStyle
import dev.orgtd.SettingsUi
import dev.orgtd.data.DarkMode
import dev.orgtd.data.StorageMode
import dev.orgtd.ui.theme.OrgtdPalettes
import dev.orgtd.ui.theme.ThemePalette
import dev.orgtd.data.FileExporter
import dev.orgtd.ui.theme.displayName
import dev.orgtd.update.UpdateChecker
import dev.orgtd.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: SettingsUi,
    onDismiss: () -> Unit,
    onPickFolder: () -> Unit,
    onSaveWebDav: (String, String, String) -> Unit,
    onSetTheme: (String) -> Unit,
    onSetDarkMode: (DarkMode) -> Unit,
    onSetLanguage: (Language) -> Unit,
    onSetUiStyle: (UiStyle) -> Unit,
    onSetRemindDateOnly: (Boolean) -> Unit,
    onSetFileName: (String) -> Unit,
    onUseInternal: () -> Unit,
    onExportFile: suspend () -> String?,
) {
    val strings = LocalStrings.current
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scroll = rememberScrollState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var checkMessage by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<UpdateInfo?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    var mode by rememberSaveable { mutableStateOf(settings.mode) }
    var fileName by rememberSaveable(settings.fileNameValue) { mutableStateOf(settings.fileNameValue) }
    var url by rememberSaveable { mutableStateOf(settings.webDavUrl) }
    var user by rememberSaveable { mutableStateOf(settings.webDavUser) }
    val password = rememberTextFieldState("")

    val palettes: List<ThemePalette> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(OrgtdPalettes.dynamicEntry) + OrgtdPalettes.all
    } else {
        OrgtdPalettes.all
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(strings.styleLabel, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.uiStyle == UiStyle.MATERIAL,
                    onClick = { onSetUiStyle(UiStyle.MATERIAL) },
                    label = { Text(strings.styleMaterial) },
                )
                FilterChip(
                    selected = settings.uiStyle == UiStyle.APPLE,
                    onClick = { onSetUiStyle(UiStyle.APPLE) },
                    label = { Text(strings.styleApple) },
                )
            }

            if (settings.uiStyle == UiStyle.APPLE) {
                Text(
                    text = strings.appleThemeNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(strings.theme, style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    palettes.forEach { palette ->
                        FilterChip(
                            selected = settings.themeId == palette.id,
                            onClick = { onSetTheme(palette.id) },
                            label = { Text(palette.displayName(strings.isEnglish)) },
                            leadingIcon = {
                                Box(Modifier.size(14.dp).background(palette.seed, CircleShape))
                            },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    DarkMode.SYSTEM to strings.followSystem,
                    DarkMode.LIGHT to strings.lightMode,
                    DarkMode.DARK to strings.darkMode,
                ).forEach { (modeValue, label) ->
                    FilterChip(
                        selected = settings.darkMode == modeValue,
                        onClick = { onSetDarkMode(modeValue) },
                        label = { Text(label) },
                    )
                }
            }

            Text(strings.language, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Language.SYSTEM to strings.followSystemLanguage,
                    Language.ZH to strings.languageChinese,
                    Language.EN to strings.languageEnglish,
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = settings.language == value,
                        onClick = { onSetLanguage(value) },
                        label = { Text(label) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(strings.storage, style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = mode == StorageMode.INTERNAL,
                    onClick = { mode = StorageMode.INTERNAL },
                    label = { Text(strings.appStorage) },
                )
                FilterChip(
                    selected = mode == StorageMode.LOCAL,
                    onClick = { mode = StorageMode.LOCAL },
                    label = { Text(strings.localFolder) },
                )
                FilterChip(
                    selected = mode == StorageMode.WEBDAV,
                    onClick = { mode = StorageMode.WEBDAV },
                    label = { Text(strings.webdav) },
                )
            }

            when (mode) {
                StorageMode.INTERNAL -> {
                    Text(
                        text = strings.appStorageHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onUseInternal) { Text(strings.useAppStorage) }
                }

                StorageMode.LOCAL -> {
                    Text(
                        text = strings.localFolderHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onPickFolder) {
                        Text(if (settings.localFolderChosen) strings.reselectFolder else strings.chooseFolder)
                    }
                }

                StorageMode.WEBDAV -> {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(strings.davUrlLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text(strings.davAccountLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedSecureTextField(
                        state = password,
                        label = {
                            Text(if (settings.hasPassword) strings.davPasswordKeepLabel else strings.davPasswordLabel)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = strings.davHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { onSaveWebDav(url, user, password.text.toString()) },
                        enabled = url.isNotBlank() && user.isNotBlank(),
                    ) { Text(strings.saveConnect) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = strings.remindDateOnlyLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.remindDateOnly,
                    onCheckedChange = onSetRemindDateOnly,
                )
            }

            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text(strings.fileNameLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Button(onClick = { onSetFileName(fileName) }) { Text(strings.save) }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(strings.exportTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                text = strings.exportHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    scope.launch {
                        exportMessage = null
                        val content = onExportFile()
                        if (content == null) {
                            exportMessage = strings.exportFailed
                        } else {
                            runCatching {
                                FileExporter.share(context, settings.fileNameValue, content)
                            }.onFailure { exportMessage = "${strings.exportFailed}: ${it.message}" }
                        }
                    }
                },
            ) { Text(strings.exportTitle) }
            exportMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(strings.about, style = MaterialTheme.typography.titleMedium)
            Text(
                text = strings.versionLabel(UpdateChecker.currentVersion(context)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    scope.launch {
                        checking = true
                        checkMessage = null
                        val result = withContext(Dispatchers.IO) { UpdateChecker.check(context) }
                        checking = false
                        val current = UpdateChecker.currentVersion(context)
                        result.onSuccess { info ->
                            if (UpdateChecker.isNewer(info.latestVersion, current)) {
                                available = info
                            } else {
                                checkMessage = strings.upToDate
                            }
                        }.onFailure { error ->
                            checkMessage = "${strings.checkFailed}: ${error.message}"
                        }
                    }
                },
                enabled = !checking,
            ) { Text(if (checking) strings.checking else strings.checkUpdate) }
            checkMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    }

    available?.let { info ->
        AlertDialog(
            onDismissRequest = { available = null },
            title = { Text(strings.newVersion(info.latestVersion)) },
            text = { Text(info.releaseUrl) },
            confirmButton = {
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)))
                        }
                        available = null
                    },
                ) { Text(strings.openDownload) }
            },
            dismissButton = {
                TextButton(onClick = { available = null }) { Text(strings.cancel) }
            },
        )
    }
}
