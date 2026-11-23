package dev.orgtd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.orgtd.SettingsUi
import dev.orgtd.data.StorageMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: SettingsUi,
    onDismiss: () -> Unit,
    onPickFolder: () -> Unit,
    onSaveWebDav: (String, String, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var mode by rememberSaveable { mutableStateOf(settings.mode) }
    var url by rememberSaveable { mutableStateOf(settings.webDavUrl) }
    var user by rememberSaveable { mutableStateOf(settings.webDavUser) }
    var password by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (settings.hasPassword) "应用密码（留空不修改）" else "应用密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "应用密码在坚果云「安全选项 → 添加应用密码」生成，不是登录密码。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { onSaveWebDav(url, user, password) },
                        enabled = url.isNotBlank() && user.isNotBlank(),
                    ) { Text("保存并连接") }
                }
            }

            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
}
