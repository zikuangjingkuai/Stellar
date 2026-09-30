package roro.stellar.manager.cloud

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

/** 云端控制的设置键名与工具方法（复用 StellarSettings 的 SharedPreferences） */
object CloudConfig {
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
}
