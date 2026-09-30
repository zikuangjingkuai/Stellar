package roro.stellar.manager.cloud

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * 云端控制配置 —— 全部硬编码，不在界面暴露、用户不可修改。
 * 需要更换服务器地址时，改这里并重新编译。
 */
object CloudConfig {

    /** 服务器地址（固定） */
    const val SERVER_URL = "ws://103.236.70.241:26051/device"

    /** 设备令牌（服务器 DEVICE_TOKEN 留空时，这里也留空） */
    const val DEVICE_TOKEN = ""

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
