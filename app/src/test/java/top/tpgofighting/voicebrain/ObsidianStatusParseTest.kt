package top.tpgofighting.voicebrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.tpgofighting.voicebrain.data.api.parseObsidianStatusBody

/**
 * 锁定 /api/obsidian/status 的字段映射，防止再次读错字段名、
 * 用编造的默认值冒充真实数据（v1.1.0 及以前的实际 bug）。
 */
class ObsidianStatusParseTest {

    // 2026-11-06 从 ATP 实测抓取的真实响应，字段与数值原样保留
    private val realPayload = """
        {"ok":true,"data":{"vaultPath":"/home/ubuntu/obsidian-vault","vaultExists":true,"voiceNotesCount":14,"wikiNodesCount":68,"agentStatus":{"status":"ok","vault_ready":true,"notes":6634,"chunks":60781,"indexing":false,"memory_budget_mb":512}}}
    """.trimIndent()

    @Test
    fun parsesRealServerPayload() {
        val s = parseObsidianStatusBody(realPayload)
        assertTrue(s.loaded)
        assertTrue(s.connected)
        assertEquals(6634, s.totalNotes)
        assertEquals(14, s.syncedVoiceNotes)
        assertTrue(s.hermesAvailable)
    }

    @Test
    fun legacyFieldNamesAreNotTrusted() {
        // 旧客户端读的字段名服务端根本不返回；即使响应里出现这些名字
        // 也不得作为数据源（旧版正是因此常年显示 23 / 8 / 已挂载）
        val s = parseObsidianStatusBody(
            """{"data":{"connected":true,"totalNotes":23,"syncedVoiceNotes":8,"hermesAvailable":true}}"""
        )
        assertEquals(0, s.totalNotes)
        assertEquals(0, s.syncedVoiceNotes)
        assertFalse(s.connected)
        assertFalse(s.hermesAvailable)
    }

    @Test
    fun missingFieldsYieldHonestZeros() {
        val s = parseObsidianStatusBody("""{"ok":true,"data":{}}""")
        assertTrue(s.loaded)
        assertEquals(0, s.totalNotes)
        assertEquals(0, s.syncedVoiceNotes)
        assertFalse(s.connected)
    }

    @Test
    fun vaultDownIsReportedDisconnected() {
        val s = parseObsidianStatusBody(
            """{"data":{"vaultExists":false,"voiceNotesCount":3,"agentStatus":{"status":"ok","notes":10}}}"""
        )
        assertTrue(s.loaded)
        assertFalse(s.connected)
        assertEquals(10, s.totalNotes)
        assertEquals(3, s.syncedVoiceNotes)
    }
}
