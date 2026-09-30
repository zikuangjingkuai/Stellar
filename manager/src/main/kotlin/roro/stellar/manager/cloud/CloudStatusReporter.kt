package roro.stellar.manager.cloud

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import org.json.JSONObject
import roro.stellar.Stellar
import roro.stellar.manager.util.EnvironmentUtils

/** 采集设备状态，用于定时上报 */
object CloudStatusReporter {

    fun collect(context: Context): JSONObject {
        val json = JSONObject()
        json.put("online", true)
        json.put("timestamp", System.currentTimeMillis())
        json.put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
        json.put("android", Build.VERSION.RELEASE)
        json.put("sdk", Build.VERSION.SDK_INT)

        // 运行状态：服务在线？拿到 ADB 权限？
        val serviceRunning = runCatching { Stellar.pingBinder() }.getOrDefault(false)
        json.put("serviceRunning", serviceRunning)
        val hasAdb = if (serviceRunning) {
            runCatching {
                Stellar.checkRemotePermission("android.permission.WRITE_SECURE_SETTINGS") ==
                        PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
        } else false
        json.put("hasAdbPermission", hasAdb)
        val isRoot = serviceRunning && runCatching { Stellar.uid == 0 }.getOrDefault(false)
        json.put("runMode", if (!serviceRunning) "stopped" else if (isRoot) "root" else "adb")

        // 电量
        runCatching {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            json.put("battery", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            json.put("charging", status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL)
        }

        // 网络
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            json.put("wifiConnected", caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
            json.put("network", when {
                caps == null -> "none"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            })
        }
        runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ssid = wm.connectionInfo?.ssid?.trim('"')
            if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") json.put("wifiSsid", ssid)
        }

        // IP 地址
        runCatching {
            EnvironmentUtils.getWifiIpAddress()?.takeIf { it.isNotEmpty() }?.let { json.put("ip", it) }
        }
        if (!json.has("ip")) {
            runCatching {
                java.net.NetworkInterface.getNetworkInterfaces().toList()
                    .flatMap { it.inetAddresses.toList() }
                    .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                    ?.let { json.put("ip", it.hostAddress ?: "") }
            }
        }

        // 设备序列号（走 shell）
        if (serviceRunning) {
            runCatching {
                val p = Stellar.newProcess(arrayOf("sh", "-c", "getprop ro.serialno"), null, null)
                val s = p.inputStream.bufferedReader().readText().trim()
                p.waitForTimeout(3_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (s.isNotEmpty() && !s.equals("unknown", ignoreCase = true)) json.put("serial", s)
            }
        }

        return json
    }
}
