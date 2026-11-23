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

/** Obsidian 问答结果：服务端 {ok, answer, sources:[{path,title}], backend, writtenBack, writeBackPath} */
data class ObsidianAskResult(
    val answer: String,
    val sources: List<ObsidianSourceRef> = emptyList(),
    val backend: String = "",
    val writtenBack: Boolean = false,
    val writeBackPath: String = ""
)

/** Vault 笔记原文（GET /api/obsidian/note） */
data class VaultNoteResult(
    val path: String = "",
    val title: String = "",
    val body: String = ""
)

/** 说话人分离的一句（离线精转写） */
data class DiarizedSentence(
    val beginMs: Long,
    val endMs: Long,
    val speakerId: Int,
    val text: String
)

class VoiceBrainApiClient(
    private val baseUrl: String = "https://voice.tpgofighting.top",
    private val syncTokenProvider: () -> String = { "" }
) {
    companion object {
        private const val TAG = "VoiceBrainApiClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val authInterceptor = Interceptor { chain ->
        chain.proceed(applyBearerToken(chain.request(), syncTokenProvider()))
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // 长耗时请求专用：Obsidian Agent 问答与纪要生成在服务端可达 90~120s，
    // 默认 60s 读超时会在答案返回前先断开，误报「网络异常」
    private val longClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // 流式问答专用：读超时放宽，逐行消费 SSE
    private val streamClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 生成结构化纪要（v1.1.0 契约对齐：请求发 persona/template/sessionId，
     * v1.4.0 支持附带 myNotes；响应从 data 层解析，未决事项读 unresolved_risks）
     */
    suspend fun generateMinutes(
        utterances: List<Utterance>,
        persona: Persona,
        template: String = "general",
        sessionId: String? = null,
        customTitle: String? = null,
        myNotes: String = ""
    ): Result<Minutes> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = buildGenerateMinutesPayload(
                utterances = utterances,
                persona = persona,
                template = template,
                sessionId = sessionId,
                customTitle = customTitle,
                myNotes = myNotes
            ).toString()

            val request = Request.Builder()
                .url("$baseUrl/api/llm/generate-minutes")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = longClient.newCall(request).execute()
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

            Result.success(parseMemoriesBody(respBody))
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
            Result.success(parseSearchBody(respBody))
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
            Result.success(parseSessionsBody(respBody))
        } catch (e: Exception) {
            Log.e(TAG, "fetchSessions failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** 读取单个历史会话详情（含 source、myNotes、可见逐字稿与可选纪要） */
    suspend fun fetchSessionDetail(sessionId: String): Result<SessionDetail> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/api/sessions/$sessionId").get().build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            Result.success(parseSessionDetailBody(sessionId, respBody))
        } catch (e: Exception) {
            Log.e(TAG, "fetchSessionDetail failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** 读取单个历史会话的逐字稿 */
    suspend fun fetchSession(sessionId: String): Result<List<Utterance>> =
        fetchSessionDetail(sessionId).map { it.utterances }

    /** 更新会话个人笔记（PATCH /api/sessions/:id/notes） */
    suspend fun patchSessionNotes(
        sessionId: String,
        myNotes: String,
        updatedAt: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ts = updatedAt?.trim()?.takeIf { it.isNotEmpty() }
                ?: java.time.Instant.now().toString()
            val bodyJson = buildJsonObject {
                put("myNotes", myNotes)
                put("updatedAt", ts)
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/api/sessions/$sessionId/notes")
                .patch(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $respBody"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "patchSessionNotes failed: ${e.message}", e)
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
    ): Result<ObsidianAskResult> = withContext(Dispatchers.IO) {
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

            val response = longClient.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $respBody"))
            }

            val obj = json.parseToJsonElement(respBody).jsonObject
            if (obj["ok"]?.jsonPrimitive?.booleanOrNull == false) {
                val err = obj["error"]?.jsonPrimitive?.contentOrNull ?: "知识库问答失败"
                return@withContext Result.failure(Exception(err))
            }
            // 契约（v1.3.5 修复）：sources 是对象数组 [{path, title}]，不是字符串数组；
            // 此前按 List<String> 解析在真机上必然抛异常，问答永远落到失败兜底。
            // 同时容忍旧式字符串条目，避免服务端形状微调再次打穿。
            val sources = obj["sources"]?.jsonArray?.mapNotNull { el ->
                when (el) {
                    is JsonObject -> {
                        val path = el["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        ObsidianSourceRef(
                            path = path,
                            title = el["title"]?.jsonPrimitive?.contentOrNull ?: path
                        )
                    }
                    is JsonPrimitive -> el.contentOrNull?.let { ObsidianSourceRef(path = it, title = it) }
                    else -> null
                }
            } ?: emptyList()
            Result.success(
                ObsidianAskResult(
                    answer = obj["answer"]?.jsonPrimitive?.content ?: "",
                    sources = sources,
                    backend = obj["backend"]?.jsonPrimitive?.contentOrNull ?: "",
                    writtenBack = obj["writtenBack"]?.jsonPrimitive?.booleanOrNull ?: false,
                    writeBackPath = obj["writeBackPath"]?.jsonPrimitive?.contentOrNull ?: ""
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "askObsidianVault failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** 读取 Vault 笔记原文（GET /api/obsidian/note?path=…） */
    suspend fun fetchVaultNote(path: String): Result<VaultNoteResult> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/api/obsidian/note?path=${java.net.URLEncoder.encode(path, "UTF-8")}"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val obj = json.parseToJsonElement(respBody).jsonObject
            if (obj["ok"]?.jsonPrimitive?.booleanOrNull == false) {
                val err = obj["error"]?.jsonPrimitive?.contentOrNull ?: "笔记读取失败"
                return@withContext Result.failure(Exception(err))
            }
            Result.success(
                VaultNoteResult(
                    path = obj["path"]?.jsonPrimitive?.contentOrNull ?: path,
                    title = obj["title"]?.jsonPrimitive?.contentOrNull ?: path,
                    body = obj["body"]?.jsonPrimitive?.contentOrNull
                        ?: obj["content"]?.jsonPrimitive?.contentOrNull ?: ""
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchVaultNote failed: ${e.message}", e)
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
/**
 * 解析 /api/search 的响应体。服务端真实形状（2026-11-06 对照 ATP 实测）：
 * {success, utterances:[{sessionId, sessionTitle, sessionTime, utteranceTime, text, matched}],
 *  memories:[{id, content, category, tags}], terms:[...]}。
 * 此前客户端误读不存在的 "results" 字段，导致 App 内全文检索永远 0 条——
 * 本函数按真实形状解析，并由单元测试锁定契约。
 */
internal fun parseSearchBody(body: String): List<SearchHit> {
    val obj = obsidianStatusJson.parseToJsonElement(body).jsonObject
    val hits = mutableListOf<SearchHit>()
    obj["utterances"]?.jsonArray?.forEach { h ->
        val ho = h.jsonObject
        hits += SearchHit(
            kind = "transcript",
            sessionId = ho["sessionId"]?.jsonPrimitive?.content ?: "",
            sessionTitle = ho["sessionTitle"]?.jsonPrimitive?.content ?: "",
            stamp = ho["sessionTime"]?.jsonPrimitive?.content ?: "",
            text = ho["text"]?.jsonPrimitive?.content ?: ""
        )
    }
    obj["memories"]?.jsonArray?.forEach { h ->
        val ho = h.jsonObject
        hits += SearchHit(
            kind = "memory",
            sessionId = ho["id"]?.jsonPrimitive?.content ?: "",
            text = ho["content"]?.jsonPrimitive?.content ?: "",
            category = ho["category"]?.jsonPrimitive?.content ?: ""
        )
    }
    return hits
}

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

internal fun applyBearerToken(request: Request, rawToken: String): Request {
    val token = rawToken.trim()
    if (token.isEmpty() || request.header("Authorization") != null) return request
    return request.newBuilder()
        .header("Authorization", "Bearer $token")
        .build()
}

internal fun buildGenerateMinutesPayload(
    utterances: List<Utterance>,
    persona: Persona,
    template: String = "general",
    sessionId: String? = null,
    customTitle: String? = null,
    myNotes: String = ""
): JsonObject {
    val uttArray = buildJsonArray {
        utterances.filterNot { it.hidden }.forEach { u ->
            add(buildJsonObject {
                put("id", u.id)
                put("speaker", u.speaker)
                put("track", u.track)
                put("time", u.time)
                put("text", u.rawText.ifBlank { u.text })
                put("rawText", u.rawText.ifBlank { u.text })
                if (!u.cleansedText.isNullOrBlank()) put("displayText", u.cleansedText)
            })
        }
    }
    return buildJsonObject {
        put("utterances", uttArray)
        put("persona", persona.id)
        put("template", template)
        if (sessionId != null) put("sessionId", sessionId)
        if (customTitle != null) put("customTitle", customTitle)
        if (myNotes.isNotBlank()) put("myNotes", myNotes)
    }
}

/**
 * 解析 /api/memory/all 的响应体：兼容 Scheme B 裸 JSON 数组 [...] 与旧式包装对象 {"memories": [...]}。
 */
internal fun parseMemoriesBody(body: String): List<MemoryItem> {
    if (body.isBlank()) return emptyList()
    val element = obsidianStatusJson.parseToJsonElement(body)
    val arr = when (element) {
        is JsonArray -> element
        is JsonObject -> element["memories"] as? JsonArray ?: return emptyList()
        else -> return emptyList()
    }
    return arr.mapIndexedNotNull { idx, item ->
        val m = item as? JsonObject ?: return@mapIndexedNotNull null
        val tags = (m["tags"] as? JsonArray)?.mapNotNull { t ->
            (t as? JsonPrimitive)?.contentOrNull
        } ?: emptyList()
        MemoryItem(
            id = (m["id"] as? JsonPrimitive)?.contentOrNull ?: "mem-$idx",
            category = (m["category"] as? JsonPrimitive)?.contentOrNull ?: "fact",
            content = (m["content"] as? JsonPrimitive)?.contentOrNull ?: "",
            tags = tags,
            importance = (m["importance"] as? JsonPrimitive)?.intOrNull ?: 3,
            createdAt = (m["createdAt"] as? JsonPrimitive)?.contentOrNull ?: ""
        )
    }
}

/**
 * 解析 /api/sessions 的响应体（Scheme B 裸数组），提取 source 与 myNotes 并排除 hidden 回声句。
 */
internal fun parseSessionsBody(body: String): List<SessionSummary> {
    if (body.isBlank()) return emptyList()
    val element = obsidianStatusJson.parseToJsonElement(body)
    val arr = when (element) {
        is JsonArray -> element
        is JsonObject -> element["sessions"] as? JsonArray ?: return emptyList()
        else -> return emptyList()
    }
    return arr.mapNotNull { s ->
        val so = s as? JsonObject ?: return@mapNotNull null
        val id = (so["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val utterances = so["utterances"] as? JsonArray
        val visibleCount = utterances?.count { el ->
            val uo = el as? JsonObject
            uo == null || ((uo["hidden"] as? JsonPrimitive)?.booleanOrNull != true &&
                (uo["hidden"] as? JsonPrimitive)?.intOrNull != 1)
        } ?: (so["utterancesCount"] as? JsonPrimitive)?.intOrNull
            ?: (so["utteranceCount"] as? JsonPrimitive)?.intOrNull
            ?: 0
        val durationSeconds = (so["durationSeconds"] as? JsonPrimitive)?.intOrNull ?: 0
        val durationLabel = (so["durationLabel"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: formatDurationLabel(durationSeconds)
        SessionSummary(
            id = id,
            title = (so["title"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: (so["topic"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: "未命名会话",
            stamp = (so["createdAt"] as? JsonPrimitive)?.contentOrNull ?: "",
            utteranceCount = visibleCount,
            durationLabel = durationLabel,
            source = (so["source"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "android",
            myNotes = (so["myNotes"] as? JsonPrimitive)?.contentOrNull ?: ""
        )
    }
}

/**
 * 解析 /api/sessions/:id 的响应体，保留 source / myNotes / 双轨 track & speaker，
 * 并自动过滤 hidden == true 的桌面端回声去重句。
 */
internal fun parseSessionDetailBody(sessionId: String, body: String): SessionDetail {
    val root = obsidianStatusJson.parseToJsonElement(body).jsonObject
    val so = (root["session"] as? JsonObject) ?: root
    val resolvedId = (so["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: sessionId
    val resolvedTitle = (so["title"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: (so["topic"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: ""
    val source = (so["source"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "android"
    val myNotes = (so["myNotes"] as? JsonPrimitive)?.contentOrNull ?: ""
    val utterances = (so["utterances"] as? JsonArray)?.mapIndexedNotNull { idx, u ->
        val uo = u as? JsonObject ?: return@mapIndexedNotNull null
        val hidden = (uo["hidden"] as? JsonPrimitive)?.booleanOrNull
            ?: ((uo["hidden"] as? JsonPrimitive)?.intOrNull == 1)
        if (hidden) return@mapIndexedNotNull null
        val track = (uo["track"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "single"
        val defaultSpeaker = when (track) {
            "me" -> "我"
            "them" -> "对方"
            else -> "发言人"
        }
        val speaker = (uo["speaker"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: defaultSpeaker
        val raw = (uo["rawText"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: (uo["text"] as? JsonPrimitive)?.contentOrNull
            ?: ""
        Utterance(
            id = (uo["id"] as? JsonPrimitive)?.intOrNull
                ?: (uo["seq"] as? JsonPrimitive)?.intOrNull
                ?: (idx + 1),
            sentenceId = (uo["sentenceId"] as? JsonPrimitive)?.contentOrNull ?: "sess-$idx",
            speaker = speaker,
            time = (uo["time"] as? JsonPrimitive)?.contentOrNull ?: "",
            text = raw,
            rawText = raw,
            isCleansed = (uo["isCleansed"] as? JsonPrimitive)?.booleanOrNull
                ?: ((uo["isCleansed"] as? JsonPrimitive)?.intOrNull == 1),
            cleansedText = (uo["displayText"] as? JsonPrimitive)?.contentOrNull,
            beginMs = (uo["startMs"] as? JsonPrimitive)?.longOrNull
                ?: (uo["beginMs"] as? JsonPrimitive)?.longOrNull
                ?: (uo["begin_time"] as? JsonPrimitive)?.longOrNull
                ?: 0L,
            track = track,
            hidden = false
        )
    } ?: emptyList()

    val minutesObj = so["minutes"] as? JsonObject
    val minutes = minutesObj?.let { mo ->
        val durationSeconds = (so["durationSeconds"] as? JsonPrimitive)?.intOrNull ?: 0
        parseMinutesObject(
            obj = mo,
            fallbackTitle = resolvedTitle.ifBlank { "语音纪要" },
            fallbackDate = (so["updatedAt"] as? JsonPrimitive)?.contentOrNull
                ?: (so["createdAt"] as? JsonPrimitive)?.contentOrNull
                ?: "",
            fallbackDuration = formatDurationLabel(durationSeconds),
            polishedDraft = (so["polishedDraft"] as? JsonPrimitive)?.contentOrNull ?: ""
        )
    }

    return SessionDetail(
        id = resolvedId,
        title = resolvedTitle,
        source = source,
        myNotes = myNotes,
        utterances = utterances,
        minutes = minutes
    )
}

private fun formatDurationLabel(durationSeconds: Int): String {
    if (durationSeconds <= 0) return ""
    val mins = durationSeconds / 60
    val secs = durationSeconds % 60
    return if (mins > 0) "${mins}分${secs}秒" else "${secs}秒"
}

private fun parseMinutesObject(
    obj: JsonObject,
    fallbackTitle: String,
    fallbackDate: String,
    fallbackDuration: String,
    polishedDraft: String = ""
): Minutes {
    val title = (obj["title"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: fallbackTitle
    val tldr = (obj["tldr"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    val decisions = (obj["decisions"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    val actionItems = (obj["action_items"] as? JsonArray)?.mapIndexedNotNull { idx, it ->
        val itemObj = it as? JsonObject ?: return@mapIndexedNotNull null
        ActionItem(
            id = "act-$idx",
            task = (itemObj["task"] as? JsonPrimitive)?.contentOrNull ?: "",
            assignee = (itemObj["assignee"] as? JsonPrimitive)?.contentOrNull ?: "待定",
            priority = (itemObj["priority"] as? JsonPrimitive)?.contentOrNull ?: "P1",
            status = (itemObj["status"] as? JsonPrimitive)?.contentOrNull ?: "pending"
        )
    } ?: emptyList()
    val unresolvedArr = (obj["unresolved_risks"] as? JsonArray) ?: (obj["unresolved_questions"] as? JsonArray)
    val unresolved = unresolvedArr?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    val mindmap = (obj["mindmap_markdown"] as? JsonPrimitive)?.contentOrNull ?: ""
    val respTemplate = (obj["template"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "general"
    val respTemplateLabel = (obj["templateLabel"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: MINUTES_TEMPLATES.firstOrNull { it.id == respTemplate }?.label ?: "通用"
    val personaId = (obj["persona_id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: (obj["personaId"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: ""
    val personaName = (obj["persona_name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: (obj["personaName"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: ""

    return Minutes(
        title = title,
        date = (obj["date"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: fallbackDate,
        duration = (obj["duration"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: fallbackDuration,
        personaId = personaId,
        personaName = personaName,
        tldr = tldr,
        decisions = decisions,
        actionItems = actionItems,
        unresolvedQuestions = unresolved,
        mindmapMarkdown = mindmap,
        polishedDraft = polishedDraft.ifBlank { (obj["polishedDraft"] as? JsonPrimitive)?.contentOrNull ?: "" },
        template = respTemplate,
        templateLabel = respTemplateLabel
    )
}
