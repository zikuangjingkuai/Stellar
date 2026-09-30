package roro.stellar.manager.cloud

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * 云端控制配置 —— 硬编码，不在界面暴露、用户不可修改。
 * 需要更换服务器地址时，改这里并重新编译。
 */
object CloudConfig {

    // ── 硬编码配置（用户不可见 / 不可改）──
    const val SERVER_URL = "ws://103.236.70.241:26051/device"
    const val DEVICE_TOKEN = ""

    // ── 兼容常量（若仓库里还留着旧版 CloudSettingsCard.kt，防止编译报错）──
    const val KEY_ENABLED = "cloud_control_enabled"
    const val KEY_SERVER_URL = "cloud_server_url"
    const val KEY_TOKEN = "cloud_device_token"
    const val KEY_DEVICE_NAME = "cloud_device_name"

    /** 设备唯一标识（Android ID） */
    @SuppressLint("HardwareIds")
    fun deviceId(context: Context): String = try {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    } catch (_: Throwable) {
        "unknown"
    }

    /** 设备显示名（用手机型号） */
    fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
}
