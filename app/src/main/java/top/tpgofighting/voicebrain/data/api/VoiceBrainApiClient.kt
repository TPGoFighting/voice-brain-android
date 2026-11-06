package top.tpgofighting.voicebrain.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import top.tpgofighting.voicebrain.model.*
import java.io.File
import java.util.concurrent.TimeUnit

/** 问答结果：答案正文 + 真实引用来源（服务端检索命中的原文片段） */
data class BrainAnswer(
    val answer: String,
    val sources: List<BrainSource> = emptyList()
)

/** 说话人分离的一句（离线精转写） */
data class DiarizedSentence(
    val beginMs: Long,
    val endMs: Long,
    val speakerId: Int,
    val text: String
)

class VoiceBrainApiClient(
    private val baseUrl: String = "https://voice.tpgofighting.top"
) {
    companion object {
        private const val TAG = "VoiceBrainApiClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // 流式问答专用：读超时放宽，逐行消费 SSE
    private val streamClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 生成结构化纪要（v1.1.0 契约对齐：请求发 persona/template/sessionId，
     * 响应从 data 层解析，未决事项读 unresolved_risks）
     */
    suspend fun generateMinutes(
        utterances: List<Utterance>,
        persona: Persona,
        template: String = "general",
        sessionId: String? = null,
        customTitle: String? = null
    ): Result<Minutes> = withContext(Dispatchers.IO) {
        try {
            val uttArray = buildJsonArray {
                utterances.forEach { u ->
                    add(buildJsonObject {
                        put("id", u.id)
                        put("speaker", u.speaker)
                        put("time", u.time)
                        put("text", u.rawText.ifBlank { u.text })
                        put("rawText", u.rawText.ifBlank { u.text })
                        if (!u.cleansedText.isNullOrBlank()) put("displayText", u.cleansedText)
                    })
                }
            }

            val bodyJson = buildJsonObject {
                put("utterances", uttArray)
                put("persona", persona.id)
                put("template", template)
                if (sessionId != null) put("sessionId", sessionId)
                if (customTitle != null) put("customTitle", customTitle)
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/api/llm/generate-minutes")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $respBody"))
            }

            val root = json.parseToJsonElement(respBody).jsonObject
            val obj = root["data"]?.jsonObject ?: root
            val title = obj["title"]?.jsonPrimitive?.content ?: customTitle ?: "语音纪要"
            val tldr = obj["tldr"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val decisions = obj["decisions"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val actionItems = obj["action_items"]?.jsonArray?.mapIndexed { idx, it ->
                val itemObj = it.jsonObject
                ActionItem(
                    id = "act-$idx",
                    task = itemObj["task"]?.jsonPrimitive?.content ?: "",
                    assignee = itemObj["assignee"]?.jsonPrimitive?.content ?: "待定",
                    priority = itemObj["priority"]?.jsonPrimitive?.content ?: "P1",
                    status = itemObj["status"]?.jsonPrimitive?.content ?: "pending"
                )
            } ?: emptyList()
            val unresolved = (obj["unresolved_risks"] ?: obj["unresolved_questions"])?.jsonArray
                ?.map { it.jsonPrimitive.content } ?: emptyList()
            val mindmap = obj["mindmap_markdown"]?.jsonPrimitive?.content ?: ""
            val respTemplate = obj["template"]?.jsonPrimitive?.content ?: template
            val respTemplateLabel = obj["templateLabel"]?.jsonPrimitive?.content
                ?: MINUTES_TEMPLATES.firstOrNull { it.id == respTemplate }?.label ?: "通用"

            Result.success(
                Minutes(
                    title = title,
                    date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                    duration = "${(utterances.size * 5)} 秒",
                    personaId = persona.id,
                    personaName = persona.name,
                    tldr = tldr,
                    decisions = decisions,
                    actionItems = actionItems,
                    unresolvedQuestions = unresolved,
                    mindmapMarkdown = mindmap,
                    template = respTemplate,
                    templateLabel = respTemplateLabel
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "generateMinutes failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 排版润色正式商业汇报稿 (Tier 3 Markdown)
     */
    suspend fun polishDraft(transcript: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = buildJsonObject {
                put("text", transcript)
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/api/llm/polish")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $respBody"))
            }

            val obj = json.parseToJsonElement(respBody).jsonObject
            val draft = obj["draft"]?.jsonPrimitive?.content ?: ""
            Result.success(draft)
        } catch (e: Exception) {
            Log.e(TAG, "polishDraft failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 获取长期记忆库全量数据
     */
    suspend fun fetchMemories(): Result<List<MemoryItem>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/memory/all")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $respBody"))
            }

            val obj = json.parseToJsonElement(respBody).jsonObject
            val memList = obj["memories"]?.jsonArray?.mapIndexed { idx, it ->
                val m = it.jsonObject
                MemoryItem(
                    id = m["id"]?.jsonPrimitive?.content ?: "mem-$idx",
                    category = m["category"]?.jsonPrimitive?.content ?: "fact",
                    content = m["content"]?.jsonPrimitive?.content ?: "",
                    tags = m["tags"]?.jsonArray?.map { t -> t.jsonPrimitive.content } ?: emptyList(),
                    importance = m["importance"]?.jsonPrimitive?.intOrNull ?: 3,
                    createdAt = m["createdAt"]?.jsonPrimitive?.content ?: ""
                )
            } ?: emptyList()

            Result.success(memList)
        } catch (e: Exception) {
            Log.e(TAG, "fetchMemories failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 问问第二大脑（非流式，流式失败时兜底）
     */
    suspend fun querySecondBrain(question: String): Result<BrainAnswer> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = buildJsonObject {
                put("question", question)
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/api/memory/query")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $respBody"))
            }

            val obj = json.parseToJsonElement(respBody).jsonObject
            Result.success(parseBrainAnswer(obj))
        } catch (e: Exception) {
            Log.e(TAG, "querySecondBrain failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 问问第二大脑（流式 SSE）：delta 逐字回调，final 事件带真实来源。
     * 流在首字前失败会抛异常，调用方回退非流式接口。
     */
    suspend fun querySecondBrainStream(
        question: String,
        onDelta: (String) -> Unit
    ): Result<BrainAnswer> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = buildJsonObject {
                put("question", question)
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/api/memory/query/stream")
                .header("Accept", "text/event-stream")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = streamClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val source = response.body?.source()
                ?: return@withContext Result.failure(Exception("流式响应为空"))

            var finalAnswer: BrainAnswer? = null
            var sawDelta = false
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty() || payload == "[DONE]") continue
                val evt = try {
                    json.parseToJsonElement(payload).jsonObject
                } catch (e: Exception) {
                    continue
                }
                when (evt["type"]?.jsonPrimitive?.content) {
                    "delta" -> {
                        val text = evt["text"]?.jsonPrimitive?.content ?: ""
                        if (text.isNotEmpty()) {
                            sawDelta = true
                            withContext(Dispatchers.Main) { onDelta(text) }
                        }
                    }
                    "final" -> {
                        finalAnswer = parseBrainAnswer(evt)
                    }
                    "error" -> {
                        val msg = evt["message"]?.jsonPrimitive?.content ?: "流式问答出错"
                        if (!sawDelta) return@withContext Result.failure(Exception(msg))
                    }
                }
            }
            val answer = finalAnswer
                ?: return@withContext Result.failure(Exception("流式问答未收到 final 事件"))
            Result.success(answer)
        } catch (e: Exception) {
            Log.e(TAG, "querySecondBrainStream failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun parseBrainAnswer(obj: JsonObject): BrainAnswer {
        val answer = obj["answer"]?.jsonPrimitive?.content ?: "未能查询到对应记忆"
        val sources = obj["sources"]?.jsonArray?.map { s ->
            val so = s.jsonObject
            BrainSource(
                sessionId = so["sessionId"]?.jsonPrimitive?.content ?: "",
                sessionTitle = so["sessionTitle"]?.jsonPrimitive?.content ?: "",
                text = so["text"]?.jsonPrimitive?.content ?: ""
            )
        } ?: emptyList()
        return BrainAnswer(answer = answer, sources = sources)
    }

    /**
     * 全文检索（GET /api/search）：逐字稿原文 + 长期记忆
     */
    suspend fun search(query: String, limit: Int = 30): Result<List<SearchHit>> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=$limit"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val obj = json.parseToJsonElement(respBody).jsonObject
            val hits = obj["results"]?.jsonArray?.map { h ->
                val ho = h.jsonObject
                SearchHit(
                    kind = ho["kind"]?.jsonPrimitive?.content ?: "transcript",
                    sessionId = ho["sessionId"]?.jsonPrimitive?.content ?: "",
                    sessionTitle = ho["sessionTitle"]?.jsonPrimitive?.content ?: "",
                    stamp = ho["stamp"]?.jsonPrimitive?.content ?: "",
                    text = ho["text"]?.jsonPrimitive?.content ?: "",
                    category = ho["category"]?.jsonPrimitive?.content ?: ""
                )
            } ?: emptyList()
            Result.success(hits)
        } catch (e: Exception) {
            Log.e(TAG, "search failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** 历史会话列表（GET /api/sessions 返回裸数组） */
    suspend fun fetchSessions(): Result<List<SessionSummary>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/api/sessions").get().build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val arr = json.parseToJsonElement(respBody).jsonArray
            val list = arr.map { s ->
                val so = s.jsonObject
                val utterances = so["utterances"]?.jsonArray
                SessionSummary(
                    id = so["id"]?.jsonPrimitive?.content ?: "",
                    title = so["title"]?.jsonPrimitive?.content ?: so["topic"]?.jsonPrimitive?.content ?: "未命名会话",
                    stamp = so["createdAt"]?.jsonPrimitive?.content ?: "",
                    utteranceCount = utterances?.size ?: 0
                )
            }.filter { it.id.isNotBlank() }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "fetchSessions failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** 读取单个历史会话的逐字稿 */
    suspend fun fetchSession(sessionId: String): Result<List<Utterance>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/api/sessions/$sessionId").get().build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val so = json.parseToJsonElement(respBody).jsonObject
            val utterances = so["utterances"]?.jsonArray?.mapIndexed { idx, u ->
                val uo = u.jsonObject
                val raw = uo["rawText"]?.jsonPrimitive?.content ?: uo["text"]?.jsonPrimitive?.content ?: ""
                Utterance(
                    id = uo["id"]?.jsonPrimitive?.intOrNull ?: (idx + 1),
                    sentenceId = uo["sentenceId"]?.jsonPrimitive?.content ?: "sess-$idx",
                    speaker = uo["speaker"]?.jsonPrimitive?.content ?: "发言人",
                    time = uo["time"]?.jsonPrimitive?.content ?: "",
                    text = raw,
                    rawText = raw,
                    isCleansed = uo["isCleansed"]?.jsonPrimitive?.booleanOrNull ?: false,
                    cleansedText = uo["displayText"]?.jsonPrimitive?.content,
                    beginMs = uo["begin_time"]?.jsonPrimitive?.longOrNull ?: 0L
                )
            } ?: emptyList()
            Result.success(utterances)
        } catch (e: Exception) {
            Log.e(TAG, "fetchSession failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ---------- 说话人分离（离线精转写 paraformer-v2 + diarization） ----------

    suspend fun diarizeUpload(wav: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = wav.readBytes().toRequestBody("audio/wav".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/api/asr/diarize/upload")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $respBody"))
            }
            val obj = json.parseToJsonElement(respBody).jsonObject
            val audioId = obj["audioId"]?.jsonPrimitive?.content
                ?: return@withContext Result.failure(Exception("上传响应缺少 audioId"))
            Result.success(audioId)
        } catch (e: Exception) {
            Log.e(TAG, "diarizeUpload failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun diarizeStart(audioId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = buildJsonObject { put("audioId", audioId) }.toString()
            val request = Request.Builder()
                .url("$baseUrl/api/asr/diarize/start")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $respBody"))
            }
            val obj = json.parseToJsonElement(respBody).jsonObject
            val taskId = obj["taskId"]?.jsonPrimitive?.content
                ?: return@withContext Result.failure(Exception("启动响应缺少 taskId"))
            Result.success(taskId)
        } catch (e: Exception) {
            Log.e(TAG, "diarizeStart failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** 返回 Pair(状态, 句子列表)；状态为 RUNNING 时句子为空 */
    suspend fun diarizeStatus(taskId: String): Result<Pair<String, List<DiarizedSentence>>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/api/asr/diarize/status/$taskId").get().build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $respBody"))
            }
            val obj = json.parseToJsonElement(respBody).jsonObject
            val status = obj["status"]?.jsonPrimitive?.content ?: "UNKNOWN"
            if (status == "FAILED") {
                val msg = obj["message"]?.jsonPrimitive?.content ?: "精转写失败"
                return@withContext Result.failure(Exception(msg))
            }
            val sentences = obj["sentences"]?.jsonArray?.map { s ->
                val so = s.jsonObject
                DiarizedSentence(
                    beginMs = so["beginMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                    endMs = so["endMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                    speakerId = so["speakerId"]?.jsonPrimitive?.intOrNull ?: 0,
                    text = so["text"]?.jsonPrimitive?.content ?: ""
                )
            } ?: emptyList()
            Result.success(Pair(status, sentences))
        } catch (e: Exception) {
            Log.e(TAG, "diarizeStatus failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 获取 Obsidian Vault 状态与同步笔记
     */
    suspend fun fetchObsidianStatus(): Result<ObsidianStatus> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/obsidian/status")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                // 失败必须如实上报，绝不回退成编造的默认状态
                return@withContext Result.failure(IllegalStateException("obsidian/status HTTP ${response.code}"))
            }

            Result.success(parseObsidianStatusBody(respBody))
        } catch (e: Exception) {
            Log.e(TAG, "fetchObsidianStatus failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 向 Obsidian 知识库 (带 Hermes RAG / 反向追记) 提问
     */
    suspend fun askObsidianVault(
        query: String,
        targetNotePath: String? = null,
        autoWriteBack: Boolean = false
    ): Result<Pair<String, List<String>>> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = buildJsonObject {
                put("query", query)
                if (targetNotePath != null) put("targetNotePath", targetNotePath)
                put("autoWriteBack", autoWriteBack)
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/api/obsidian/ask")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $respBody"))
            }

            val obj = json.parseToJsonElement(respBody).jsonObject
            val answer = obj["answer"]?.jsonPrimitive?.content ?: ""
            val sources = obj["sources"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            Result.success(Pair(answer, sources))
        } catch (e: Exception) {
            Log.e(TAG, "askObsidianVault failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}

private val obsidianStatusJson = Json { ignoreUnknownKeys = true }

/**
 * 解析 /api/obsidian/status 的响应体。只认服务端真实字段
 * （2026-11-06 对照 ATP 实测）：vaultExists / voiceNotesCount /
 * agentStatus{status, notes}。字段缺失时给出诚实的 0 / false，
 * 绝不回退到任何编造的默认值。抽成独立函数供单元测试锁定映射。
 */
internal fun parseObsidianStatusBody(body: String): ObsidianStatus {
    val obj = obsidianStatusJson.parseToJsonElement(body).jsonObject
    val data = obj["data"]?.jsonObject ?: obj
    val agent = data["agentStatus"]?.jsonObject
    val vaultExists = data["vaultExists"]?.jsonPrimitive?.booleanOrNull ?: false
    val agentOk = agent?.get("status")?.jsonPrimitive?.contentOrNull == "ok"
    return ObsidianStatus(
        loaded = true,
        connected = vaultExists && agentOk,
        vaultPath = data["vaultPath"]?.jsonPrimitive?.content ?: "",
        totalNotes = agent?.get("notes")?.jsonPrimitive?.intOrNull ?: 0,
        syncedVoiceNotes = data["voiceNotesCount"]?.jsonPrimitive?.intOrNull ?: 0,
        hermesAvailable = agentOk
    )
}
