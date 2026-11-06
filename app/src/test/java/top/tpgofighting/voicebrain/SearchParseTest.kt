package top.tpgofighting.voicebrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.tpgofighting.voicebrain.data.api.parseSearchBody

/**
 * 锁定 /api/search 的真实响应契约（2026-11-06 ATP 实测形状）。
 * 历史教训：客户端曾误读不存在的 "results" 字段，App 内搜索永远返回 0 条，
 * 而服务端一直正常——解析层必须有测试守住。
 */
class SearchParseTest {

    // 与 ATP 实测同形状的样本（内容为示意）
    private val realShape = """
        {
          "success": true,
          "utterances": [
            {"sessionId": "sess-1", "sessionTitle": "Super Agent 双层架构", "sessionTime": "2026-11-01T17:46:32.056Z", "utteranceTime": "01:38:57", "text": "过去一年里，Coding Agent 已经证明模型能够使用工具", "matched": ["Agent"]}
          ],
          "memories": [
            {"id": "mem-1", "content": "内部 Super Agent 实例基于 DeepSeek Harness 运行", "category": "fact", "tags": ["DeepSeekHarness"]}
          ],
          "terms": ["Agent"]
        }
    """.trimIndent()

    @Test
    fun parsesUtterancesAndMemories() {
        val hits = parseSearchBody(realShape)
        assertEquals(2, hits.size)
        val transcript = hits.first { it.kind == "transcript" }
        assertEquals("sess-1", transcript.sessionId)
        assertEquals("Super Agent 双层架构", transcript.sessionTitle)
        assertTrue(transcript.text.contains("Coding Agent"))
        val memory = hits.first { it.kind == "memory" }
        assertEquals("fact", memory.category)
        assertTrue(memory.text.contains("DeepSeek Harness"))
    }

    @Test
    fun emptySectionsYieldEmptyList() {
        val hits = parseSearchBody("""{"success": true, "utterances": [], "memories": [], "terms": []}""")
        assertTrue(hits.isEmpty())
    }
}
