package dev.orgtd.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.orgtd.SettingsUi
import dev.orgtd.data.DarkMode
import dev.orgtd.data.StorageMode
import dev.orgtd.ui.theme.OrgtdPalettes
import dev.orgtd.ui.theme.ThemePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: SettingsUi,
    onDismiss: () -> Unit,
    onPickFolder: () -> Unit,
    onSaveWebDav: (String, String, String) -> Unit,
    onSetTheme: (String) -> Unit,
    onSetDarkMode: (DarkMode) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scroll = rememberScrollState()

    var mode by rememberSaveable { mutableStateOf(settings.mode) }
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
            Text("主题", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                palettes.forEach { palette ->
                    FilterChip(
                        selected = settings.themeId == palette.id,
                        onClick = { onSetTheme(palette.id) },
                        label = { Text(palette.name) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .background(palette.seed, CircleShape),
                            )
                        },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    DarkMode.SYSTEM to "跟随系统",
                    DarkMode.LIGHT to "浅色",
                    DarkMode.DARK to "深色",
                ).forEach { (modeValue, label) ->
                    FilterChip(
                        selected = settings.darkMode == modeValue,
                        onClick = { onSetDarkMode(modeValue) },
                        label = { Text(label) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("存储位置", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == StorageMode.LOCAL,
                    onClick = { mode = StorageMode.LOCAL },
                    label = { Text("本地目录") },
                )
                FilterChip(
                    selected = mode == StorageMode.WEBDAV,
                    onClick = { mode = StorageMode.WEBDAV },
                    label = { Text("坚果云 WebDAV") },
                )
            }

            when (mode) {
                StorageMode.LOCAL -> {
                    Text(
                        text = "坚果云自己的目录在 Android/data 里，第三方应用无权访问。把同步工具落地的普通目录授权给 App 即可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onPickFolder) {
                        Text(if (settings.localFolderChosen) "重新选择目录" else "选择目录")
                    }
                }

                StorageMode.WEBDAV -> {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("org 文件地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("坚果云账号（邮箱）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedSecureTextField(
                        state = password,
                        label = { Text(if (settings.hasPassword) "应用密码（留空不修改）" else "应用密码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "应用密码在坚果云「安全选项 → 添加应用密码」生成，不是登录密码。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { onSaveWebDav(url, user, password.text.toString()) },
                        enabled = url.isNotBlank() && user.isNotBlank(),
                    ) { Text("保存并连接") }
                }
            }

            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
}
