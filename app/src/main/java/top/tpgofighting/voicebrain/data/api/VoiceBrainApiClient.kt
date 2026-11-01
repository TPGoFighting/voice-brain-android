package top.tpgofighting.voicebrain.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import top.tpgofighting.voicebrain.model.*
import java.util.concurrent.TimeUnit

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

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 生成结构化纪要与决策对账
     */
    suspend fun generateMinutes(
        utterances: List<Utterance>,
        persona: Persona,
        customTitle: String? = null
    ): Result<Minutes> = withContext(Dispatchers.IO) {
        try {
            val uttArray = buildJsonArray {
                utterances.forEach { u ->
                    add(buildJsonObject {
                        put("id", u.id)
                        put("speaker", u.speaker)
                        put("time", u.time)
                        put("text", u.text)
                    })
                }
            }

            val bodyJson = buildJsonObject {
                put("utterances", uttArray)
                put("personaId", persona.id)
                put("customTitle", customTitle ?: "移动端语音纪要")
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

            val obj = json.parseToJsonElement(respBody).jsonObject
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
            val unresolved = obj["unresolved_questions"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            val mindmap = obj["mindmap_markdown"]?.jsonPrimitive?.content ?: ""

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
                    mindmapMarkdown = mindmap
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
     * 问问你的第二大脑 (Ask Your Second Brain)
     */
    suspend fun querySecondBrain(question: String): Result<String> = withContext(Dispatchers.IO) {
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
            val answer = obj["answer"]?.jsonPrimitive?.content ?: "未能查询到对应记忆"
            Result.success(answer)
        } catch (e: Exception) {
            Log.e(TAG, "querySecondBrain failed: ${e.message}", e)
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
                return@withContext Result.success(ObsidianStatus()) // 回退默认
            }

            val obj = json.parseToJsonElement(respBody).jsonObject
            val data = obj["data"]?.jsonObject ?: obj
            Result.success(
                ObsidianStatus(
                    connected = data["connected"]?.jsonPrimitive?.booleanOrNull ?: true,
                    vaultPath = data["vaultPath"]?.jsonPrimitive?.content ?: "/home/ubuntu/obsidian-vault",
                    totalNotes = data["totalNotes"]?.jsonPrimitive?.intOrNull ?: 23,
                    syncedVoiceNotes = data["syncedVoiceNotes"]?.jsonPrimitive?.intOrNull ?: 8,
                    hermesAvailable = data["hermesAvailable"]?.jsonPrimitive?.booleanOrNull ?: true
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchObsidianStatus failed: ${e.message}", e)
            Result.success(ObsidianStatus())
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
