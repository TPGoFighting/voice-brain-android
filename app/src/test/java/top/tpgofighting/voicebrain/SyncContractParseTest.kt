package top.tpgofighting.voicebrain

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.tpgofighting.voicebrain.data.api.applyBearerToken
import top.tpgofighting.voicebrain.data.api.buildGenerateMinutesPayload
import top.tpgofighting.voicebrain.data.api.parseMemoriesBody
import top.tpgofighting.voicebrain.data.api.parseSessionDetailBody
import top.tpgofighting.voicebrain.data.api.parseSessionsBody
import top.tpgofighting.voicebrain.model.Persona
import top.tpgofighting.voicebrain.model.Utterance

/**
 * 锁定 Scheme B（酸奶跨端同步）接口解析与请求构造契约：
 * - /api/memory/all 兼容裸 JSON 数组与 {"memories": [...]} 包装格式
 * - /api/sessions 提取 source 与 myNotes 并过滤隐藏回声句计数
 * - /api/sessions/:id 保留 source / myNotes / 双轨 track & speaker，过滤 hidden=true 回声句
 * - Bearer Token 仅在非空时注入 Authorization 请求头
 * - generate-minutes 仅在 myNotes 非空时附带该字段，且忽略 hidden 句
 */
class SyncContractParseTest {

    @Test
    fun parseMemoriesBodyHandlesBareJsonArray() {
        val body = """
            [
              {
                "id": "mem-101",
                "category": "decision",
                "content": "采用 SQLite + LWW 作为多端统一同步底座",
                "tags": ["sync", "sqlite"],
                "importance": 5,
                "createdAt": "2026-11-23T06:00:00.000Z"
              }
            ]
        """.trimIndent()

        val items = parseMemoriesBody(body)
        assertEquals(1, items.size)
        val first = items.first()
        assertEquals("mem-101", first.id)
        assertEquals("decision", first.category)
        assertEquals("采用 SQLite + LWW 作为多端统一同步底座", first.content)
        assertEquals(listOf("sync", "sqlite"), first.tags)
        assertEquals(5, first.importance)
        assertEquals("2026-11-23T06:00:00.000Z", first.createdAt)
    }

    @Test
    fun parseMemoriesBodyHandlesWrappedObject() {
        val body = """
            {
              "memories": [
                {
                  "id": "mem-202",
                  "category": "preference",
                  "content": "纪要优先吸收用户手写笔记作为重点锚点",
                  "tags": ["minutes"],
                  "importance": 4,
                  "createdAt": "2026-11-22T12:00:00.000Z"
                }
              ]
            }
        """.trimIndent()

        val items = parseMemoriesBody(body)
        assertEquals(1, items.size)
        assertEquals("mem-202", items[0].id)
        assertEquals("preference", items[0].category)
        assertEquals(listOf("minutes"), items[0].tags)
        assertEquals(4, items[0].importance)
    }

    @Test
    fun parseMemoriesBodyHandlesEmptyAndNullFields() {
        assertTrue(parseMemoriesBody("[]").isEmpty())
        assertTrue(parseMemoriesBody("""{"memories": []}""").isEmpty())
        val items = parseMemoriesBody("""[{"id": "m-null", "content": "测试空字段", "tags": null, "createdAt": null}]""")
        assertEquals(1, items.size)
        assertEquals("m-null", items[0].id)
        assertEquals("fact", items[0].category)
        assertTrue(items[0].tags.isEmpty())
        assertEquals("", items[0].createdAt)
    }

    @Test
    fun parseSessionsBodyExtractsSourceAndMyNotes() {
        val body = """
            [
              {
                "id": "sess-desktop-1",
                "title": "客户需求同步会",
                "source": "desktop",
                "status": "done",
                "durationSeconds": 185,
                "myNotes": "重点确认周三前交付同步 Token",
                "createdAt": "2026-11-23T06:10:00.000Z",
                "utterances": [
                  {"id": 1, "text": "第一句", "hidden": false},
                  {"id": 2, "text": "回声重复", "hidden": true},
                  {"id": 3, "text": "第三句", "hidden": false}
                ]
              },
              {
                "id": "sess-legacy-2",
                "topic": "老会话兜底标题",
                "createdAt": "2026-11-20T08:00:00.000Z",
                "utterancesCount": 7
              }
            ]
        """.trimIndent()

        val sessions = parseSessionsBody(body)
        assertEquals(2, sessions.size)

        val desktop = sessions[0]
        assertEquals("sess-desktop-1", desktop.id)
        assertEquals("客户需求同步会", desktop.title)
        assertEquals("desktop", desktop.source)
        assertEquals("重点确认周三前交付同步 Token", desktop.myNotes)
        assertEquals(2, desktop.utteranceCount)
        assertEquals("3分5秒", desktop.durationLabel)

        val legacy = sessions[1]
        assertEquals("sess-legacy-2", legacy.id)
        assertEquals("老会话兜底标题", legacy.title)
        assertEquals("android", legacy.source)
        assertEquals("", legacy.myNotes)
        assertEquals(7, legacy.utteranceCount)
    }

    @Test
    fun parseSessionDetailBodyPreservesDualTrackAndFiltersHiddenEcho() {
        val body = """
            {
              "id": "sess-desktop-42",
              "title": "桌面双轨评审",
              "source": "desktop",
              "status": "done",
              "durationSeconds": 120,
              "myNotes": "1. 确认同步认证\n2. 核对双轨回声过滤",
              "minutes": null,
              "utterances": [
                {
                  "id": 1,
                  "seq": 1,
                  "sentenceId": "me-1",
                  "track": "me",
                  "speaker": "我",
                  "time": "10:01:05",
                  "startMs": 5000,
                  "endMs": 8200,
                  "text": "我们这周完成安卓同步",
                  "rawText": "我们这周完成安卓同步",
                  "displayText": "我们这周完成安卓同步。",
                  "isCleansed": true,
                  "hidden": false
                },
                {
                  "id": 2,
                  "seq": 2,
                  "sentenceId": "me-echo-2",
                  "track": "me",
                  "speaker": "我",
                  "time": "10:01:09",
                  "startMs": 9000,
                  "endMs": 10200,
                  "text": "收到没问题",
                  "rawText": "收到没问题",
                  "displayText": "收到没问题",
                  "isCleansed": false,
                  "hidden": true
                },
                {
                  "id": 3,
                  "seq": 3,
                  "sentenceId": "them-1",
                  "track": "them",
                  "speaker": "对方",
                  "time": "10:01:09",
                  "startMs": 8900,
                  "endMs": 10500,
                  "text": "收到没问题",
                  "rawText": "收到没问题",
                  "displayText": null,
                  "isCleansed": false,
                  "hidden": false
                }
              ]
            }
        """.trimIndent()

        val detail = parseSessionDetailBody("sess-desktop-42", body)
        assertEquals("sess-desktop-42", detail.id)
        assertEquals("桌面双轨评审", detail.title)
        assertEquals("desktop", detail.source)
        assertEquals("1. 确认同步认证\n2. 核对双轨回声过滤", detail.myNotes)
        assertNull(detail.minutes)

        assertEquals(2, detail.utterances.size)

        val meUtt = detail.utterances[0]
        assertEquals("me-1", meUtt.sentenceId)
        assertEquals("me", meUtt.track)
        assertEquals("我", meUtt.speaker)
        assertEquals(5000L, meUtt.beginMs)
        assertEquals("我们这周完成安卓同步。", meUtt.cleansedText)
        assertTrue(meUtt.isCleansed)
        assertFalse(meUtt.hidden)

        val themUtt = detail.utterances[1]
        assertEquals("them-1", themUtt.sentenceId)
        assertEquals("them", themUtt.track)
        assertEquals("对方", themUtt.speaker)
        assertEquals(8900L, themUtt.beginMs)
        assertNull(themUtt.cleansedText)
        assertFalse(themUtt.hidden)
    }

    @Test
    fun parseSessionDetailBodyParsesEmbeddedMinutes() {
        val body = """
            {
              "id": "sess-min-1",
              "title": "架构定调",
              "source": "desktop",
              "myNotes": "保持零新增原生依赖",
              "utterances": [],
              "minutes": {
                "title": "架构定调纪要",
                "persona_id": "architect",
                "persona_name": "系统架构师",
                "tldr": ["确定 Scheme B 契约"],
                "decisions": ["使用 Bearer Token 鉴权"],
                "action_items": [
                  {"task": "补齐 Android 单测", "assignee": "我", "priority": "P0"}
                ],
                "unresolved_risks": ["待真机验证长录音"]
              }
            }
        """.trimIndent()

        val detail = parseSessionDetailBody("sess-min-1", body)
        val minutes = detail.minutes
        assertNotNull(minutes)
        assertEquals("架构定调纪要", minutes!!.title)
        assertEquals("architect", minutes.personaId)
        assertEquals("系统架构师", minutes.personaName)
        assertEquals(listOf("确定 Scheme B 契约"), minutes.tldr)
        assertEquals(listOf("使用 Bearer Token 鉴权"), minutes.decisions)
        assertEquals(1, minutes.actionItems.size)
        assertEquals("补齐 Android 单测", minutes.actionItems[0].task)
        assertEquals(listOf("待真机验证长录音"), minutes.unresolvedQuestions)
    }

    @Test
    fun applyBearerTokenAttachesHeaderOnlyWhenNonBlank() {
        val baseRequest = Request.Builder().url("https://voice.tpgofighting.top/api/sessions").build()

        val withToken = applyBearerToken(baseRequest, "  sync-secret-123  ")
        assertEquals("Bearer sync-secret-123", withToken.header("Authorization"))

        val withBlank = applyBearerToken(baseRequest, "   ")
        assertNull(withBlank.header("Authorization"))

        val withEmpty = applyBearerToken(baseRequest, "")
        assertNull(withEmpty.header("Authorization"))
    }

    @Test
    fun buildGenerateMinutesPayloadIncludesMyNotesAndSkipsHidden() {
        val utterances = listOf(
            Utterance(
                id = 1,
                sentenceId = "u1",
                speaker = "我",
                time = "10:00:01",
                text = "今天确认同步方案",
                rawText = "今天确认同步方案",
                track = "me",
                hidden = false
            ),
            Utterance(
                id = 2,
                sentenceId = "u2",
                speaker = "我",
                time = "10:00:02",
                text = "被过滤的回声",
                rawText = "被过滤的回声",
                track = "me",
                hidden = true
            )
        )

        val withNotes = buildGenerateMinutesPayload(
            utterances = utterances,
            persona = Persona.DEFAULT,
            template = "meeting",
            sessionId = "sess-1",
            myNotes = "强调 P0 交付节点"
        )
        assertEquals("强调 P0 交付节点", withNotes["myNotes"]?.jsonPrimitive?.content)
        val uttArr = withNotes["utterances"]!!.jsonArray
        assertEquals(1, uttArr.size)
        assertEquals("me", uttArr[0].jsonObject["track"]?.jsonPrimitive?.content)

        val withoutNotes = buildGenerateMinutesPayload(
            utterances = utterances,
            persona = Persona.DEFAULT,
            myNotes = "   "
        )
        assertNull(withoutNotes["myNotes"])
    }
}
