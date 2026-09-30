package roro.stellar.manager.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import roro.stellar.Stellar
import java.util.concurrent.TimeUnit

/** 云端指令执行器：复用 Stellar 的 Shizuku shell 通道执行命令 */
object CloudCommandExecutor {

    data class Result(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val error: String? = null
    )

    suspend fun execute(command: String, timeoutMillis: Long = 30_000L): Result =
        withContext(Dispatchers.IO) {
            if (!Stellar.pingBinder()) {
                return@withContext Result(-1, "", "", "Stellar 服务未连接，无法执行命令")
            }
            try {
                val process = Stellar.newProcess(arrayOf("sh", "-c", command), null, null)
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                val finished = process.waitForTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                if (!finished) {
                    runCatching { process.destroy() }
                    return@withContext Result(-1, stdout, stderr, "命令执行超时")
                }
                Result(process.exitValue(), stdout, stderr, null)
            } catch (t: Throwable) {
                Result(-1, "", "", t.message ?: "未知错误")
            }
        }
}
