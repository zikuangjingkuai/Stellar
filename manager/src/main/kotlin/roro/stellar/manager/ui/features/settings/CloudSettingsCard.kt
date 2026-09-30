package roro.stellar.manager.ui.features.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.cloud.CloudConfig
import roro.stellar.manager.cloud.CloudControlService
import roro.stellar.manager.ui.components.SettingsExpandableCard
import roro.stellar.manager.ui.components.SettingsInnerSwitchRow
import roro.stellar.manager.ui.theme.AppShape

@Composable
fun CloudSettingsCard() {
    val context = LocalContext.current
    val preferences = StellarSettings.getPreferences()

    var enabled by remember { mutableStateOf(preferences.getBoolean(CloudConfig.KEY_ENABLED, false)) }
    var serverUrl by remember { mutableStateOf(preferences.getString(CloudConfig.KEY_SERVER_URL, "") ?: "") }
    var token by remember { mutableStateOf(preferences.getString(CloudConfig.KEY_TOKEN, "") ?: "") }
    var deviceName by remember { mutableStateOf(preferences.getString(CloudConfig.KEY_DEVICE_NAME, "") ?: "") }
    var expanded by remember { mutableStateOf(false) }

    fun persist() {
        preferences.edit {
            putBoolean(CloudConfig.KEY_ENABLED, enabled)
            putString(CloudConfig.KEY_SERVER_URL, serverUrl.trim())
            putString(CloudConfig.KEY_TOKEN, token.trim())
            putString(CloudConfig.KEY_DEVICE_NAME, deviceName.trim())
        }
    }

    SettingsExpandableCard(
        icon = Icons.Default.Cloud,
        title = "云端控制",
        subtitle = "远程下发 ADB 指令 · 定时上报设备状态",
        expanded = expanded,
        onExpandChange = { expanded = it }
    ) {
        SettingsInnerSwitchRow(
            title = "启用云端控制",
            subtitle = if (enabled) "已开启，后台保持服务器连接" else "已关闭",
            checked = enabled,
            onCheckedChange = { newValue ->
                enabled = newValue
                persist()
                if (newValue) {
                    if (serverUrl.isBlank()) {
                        Toast.makeText(context, "请先填写服务器地址", Toast.LENGTH_SHORT).show()
                    } else {
                        CloudControlService.start(context)
                    }
                } else {
                    CloudControlService.stop(context)
                }
            }
        )

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("服务器地址 (ws:// 或 wss://)") },
            placeholder = { Text("ws://你的服务器:端口/device") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = AppShape.shapes.inputField
        )

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("设备令牌（可留空）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = AppShape.shapes.inputField
        )

        OutlinedTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            label = { Text("设备名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = AppShape.shapes.inputField
        )

        Text(
            text = "设备 ID：${CloudConfig.deviceId(context)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    persist()
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                shape = AppShape.shapes.buttonMedium
            ) { Text("保存") }

            Button(
                onClick = {
                    persist()
                    if (enabled) {
                        CloudControlService.stop(context)
                        CloudControlService.start(context)
                    }
                    Toast.makeText(context, "已重新连接", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                shape = AppShape.shapes.buttonMedium
            ) { Text("重连") }
        }
    }
}
