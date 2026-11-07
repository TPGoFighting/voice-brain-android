package top.tpgofighting.voicebrain.data.websocket

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

sealed class AsrEvent {
    data class Started(val taskId: String, val model: String) : AsrEvent()
    data class Interim(val text: String) : AsrEvent()
    data class Committed(val sentenceId: String, val text: String, val rawText: String) : AsrEvent()
    data class Cleansed(val sentenceId: String, val text: String, val latencyMs: Long) : AsrEvent()
    data class Reconnecting(val message: String) : AsrEvent()
    data class Error(val message: String) : AsrEvent()
    object Finished : AsrEvent()
}

class AsrWebSocketManager(
    private val serverUrl: String = "wss://voice.tpgofighting.top/ws/asr",
    private var sentenceSilenceMs: Int = 800,
    private val onEvent: (AsrEvent) -> Unit
) {
    companion object {
        private const val TAG = "AsrWebSocketManager"
        private const val MAX_BUFFER_CHUNKS = 600 // 60 seconds ring buffer
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    // stable session id for this recording: sent on every (re)connect so the server resumes the same session
    private var sessionId: String = ""

    // reconnect backoff counter (0.5s -> 1s -> 2s -> 4s cap), reset on open
    private var reconnectAttempts = 0
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pingJob: Job? = null

    @Volatile
    private var isRecording = false

    @Volatile
    private var isConnected = false

    // 环形 PCM 缓冲队列，断线自愈重连时保留最近 15 秒音频
    private val pcmBuffer = ConcurrentLinkedQueue<ByteArray>()

    private val json = Json { ignoreUnknownKeys = true }

    fun start() {
        isRecording = true
        sessionId = "sess-android-" + System.currentTimeMillis() + "-" + (1000..9999).random()
        reconnectAttempts = 0
        connect()
        startPingTimer()
    }

    fun updateSentenceSilence(silenceMs: Int) {
        sentenceSilenceMs = silenceMs
        if (isConnected && webSocket != null) {
            webSocket?.send("""{"type":"config","maxSentenceSilence":$silenceMs}""")
        }
    }

    private fun connect() {
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected to $serverUrl")
                isConnected = true
                reconnectAttempts = 0
                ws.send("""{"type":"start","sessionId":"$sessionId","maxSentenceSilence":$sentenceSilenceMs}""")

                // 冲刷缓冲的 PCM 队列
                flushBuffer()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                // 服务端主要以 JSON 文本下发 ASR 事件
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code / $reason")
                isConnected = false
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code / $reason")
                isConnected = false
                if (isRecording) {
                    onEvent(AsrEvent.Reconnecting("长连接断开，正在自动续连中..."))
                    scheduleReconnect()
                } else {
                    onEvent(AsrEvent.Finished)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                isConnected = false
                if (isRecording) {
                    onEvent(AsrEvent.Reconnecting("网络抖动，正在自愈重连中..."))
                    scheduleReconnect()
                } else {
                    onEvent(AsrEvent.Error(t.message ?: "网络连接异常"))
                }
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val element = json.parseToJsonElement(text)
            val obj = element.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: return

            when (type) {
                "asr_started" -> {
                    val taskId = obj["taskId"]?.jsonPrimitive?.content ?: ""
                    val model = obj["model"]?.jsonPrimitive?.content ?: "fun-asr-realtime"
                    onEvent(AsrEvent.Started(taskId, model))
                    flushBuffer()
                }
                "asr_interim" -> {
                    val interimText = obj["text"]?.jsonPrimitive?.content ?: ""
                    onEvent(AsrEvent.Interim(interimText))
                }
                "asr_committed" -> {
                    val sentenceId = obj["sentenceId"]?.jsonPrimitive?.content ?: ""
                    val committedText = obj["text"]?.jsonPrimitive?.content ?: ""
                    val rawText = obj["raw_text"]?.jsonPrimitive?.content ?: committedText
                    onEvent(AsrEvent.Committed(sentenceId, committedText, rawText))
                }
                "sentence_cleansed" -> {
                    val sentenceId = obj["sentenceId"]?.jsonPrimitive?.content ?: ""
                    val cleansed = obj["cleansed"]?.jsonPrimitive?.content ?: ""
                    val latencyMs = obj["latencyMs"]?.jsonPrimitive?.longOrNull ?: 0L
                    onEvent(AsrEvent.Cleansed(sentenceId, cleansed, latencyMs))
                }
                "asr_reconnecting" -> {
                    val msg = obj["message"]?.jsonPrimitive?.content ?: "引擎续连中..."
                    onEvent(AsrEvent.Reconnecting(msg))
                }
                "asr_finished" -> {
                    if (!isRecording) {
                        onEvent(AsrEvent.Finished)
                    }
                }
                "asr_error" -> {
                    val msg = obj["message"]?.jsonPrimitive?.content ?: "识别服务异常"
                    onEvent(AsrEvent.Error(msg))
                }
                "pong" -> {
                    // 心跳存活确认
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing WebSocket message: ${e.message}")
        }
    }

    fun sendChunk(chunk: ByteArray) {
        if (!isRecording) return

        if (isConnected && webSocket != null) {
            val sent = webSocket?.send(chunk.toByteString()) ?: false
            if (!sent) {
                bufferChunk(chunk)
            }
        } else {
            bufferChunk(chunk)
        }
    }

    private fun bufferChunk(chunk: ByteArray) {
        pcmBuffer.offer(chunk)
        while (pcmBuffer.size > MAX_BUFFER_CHUNKS) {
            pcmBuffer.poll()
        }
    }

    private fun flushBuffer() {
        coroutineScope.launch {
            while (isConnected && !pcmBuffer.isEmpty()) {
                val chunk = pcmBuffer.poll() ?: break
                webSocket?.send(chunk.toByteString())
            }
        }
    }

    private fun scheduleReconnect() {
        val delayMs = when (reconnectAttempts) {
            0 -> 500L
            1 -> 1000L
            2 -> 2000L
            else -> 4000L
        }
        reconnectAttempts++
        coroutineScope.launch {
            delay(delayMs)
            if (isRecording && !isConnected) {
                connect()
            }
        }
    }

    private fun startPingTimer() {
        pingJob?.cancel()
        pingJob = coroutineScope.launch {
            while (isActive && isRecording) {
                delay(15000)
                if (isConnected) {
                    webSocket?.send("""{"type":"ping"}""")
                }
            }
        }
    }

    fun stop() {
        isRecording = false
        pingJob?.cancel()
        pingJob = null

        if (isConnected) {
            webSocket?.send("""{"type":"stop"}""")
        }
        coroutineScope.launch {
            delay(500)
            webSocket?.close(1000, "Recording stopped by user")
            webSocket = null
            isConnected = false
            pcmBuffer.clear()
        }
    }
}
