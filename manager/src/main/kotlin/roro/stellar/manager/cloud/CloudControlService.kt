package roro.stellar.manager.cloud

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import roro.stellar.manager.StellarSettings
import java.util.concurrent.TimeUnit

/** 云端控制前台服务：保持 WebSocket 长连接、接收指令、定时上报状态 */
class CloudControlService : Service() {

    companion object {
        private const val CHANNEL_ID = "stellar_cloud_control"
        private const val NOTIFICATION_ID = 0x5301
        private const val STATUS_INTERVAL_MS = 30 * 60 * 1000L   // 每 30 分钟上报一次
        private const val RECONNECT_DELAY_MS = 5_000L

        fun start(context: Context) {
            val intent = Intent(context, CloudControlService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CloudControlService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    @Volatile private var running = false
    @Volatile private var webSocket: WebSocket? = null
    private var statusJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("正在连接服务器…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            connect()
        }
        return START_STICKY
    }

    private fun connect() {
        val url = StellarSettings.getPreferences()
            .getString(CloudConfig.KEY_SERVER_URL, "")?.trim().orEmpty()
        if (url.isEmpty()) {
            updateNotification("未配置服务器地址")
            scheduleReconnect()
            return
        }
        webSocket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    updateNotification("已连接服务器")
                    sendRegister(ws)
                    startStatusLoop(ws)
                }

                override fun onMessage(ws: WebSocket, text: String) = handleMessage(ws, text)

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    statusJob?.cancel()
                    updateNotification("连接断开，重连中…")
                    scheduleReconnect()
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    statusJob?.cancel()
                    scheduleReconnect()
                }
            }
        )
    }

    private fun sendRegister(ws: WebSocket) {
        val prefs = StellarSettings.getPreferences()
        ws.send(JSONObject().apply {
            put("type", "register")
            put("deviceId", CloudConfig.deviceId(applicationContext))
            put("deviceName", prefs.getString(CloudConfig.KEY_DEVICE_NAME, "") ?: "")
            put("token", prefs.getString(CloudConfig.KEY_TOKEN, "") ?: "")
            put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
        }.toString())
    }

    private fun startStatusLoop(ws: WebSocket) {
        statusJob?.cancel()
        statusJob = scope.launch {
            while (isActive) {
                runCatching {
                    ws.send(CloudStatusReporter.collect(applicationContext).apply {
                        put("type", "status")
                        put("deviceId", CloudConfig.deviceId(applicationContext))
                    }.toString())
                }
                delay(STATUS_INTERVAL_MS)
            }
        }
    }

    private fun handleMessage(ws: WebSocket, text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            "command" -> {
                val id = json.optString("id")
                val command = json.optString("command")
                if (command.isBlank()) return
                scope.launch {
                    val r = CloudCommandExecutor.execute(command)
                    ws.send(JSONObject().apply {
                        put("type", "result")
                        put("id", id)
                        put("deviceId", CloudConfig.deviceId(applicationContext))
                        put("exitCode", r.exitCode)
                        put("stdout", r.stdout)
                        put("stderr", r.stderr)
                        r.error?.let { put("error", it) }
                    }.toString())
                }
            }
            "ping" -> ws.send(JSONObject().put("type", "pong").toString())
        }
    }

    private fun scheduleReconnect() {
        if (!running) return
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (running) connect()
        }
    }

    override fun onDestroy() {
        running = false
        statusJob?.cancel()
        scope.cancel()
        runCatching { webSocket?.close(1000, "destroyed") }
        runCatching { client.dispatcher.executorService.shutdown() }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "云端控制", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)
        return builder
            .setContentTitle("Stellar 云端控制")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        }
    }
}
