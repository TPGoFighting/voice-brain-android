package top.tpgofighting.voicebrain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import top.tpgofighting.voicebrain.data.websocket.buildStartCommandJson
import top.tpgofighting.voicebrain.data.websocket.buildWebSocketRequest

class WsAuthTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun buildStartCommandJsonOmitsAuthTokenWhenBlank() {
        val emptyPayload = buildStartCommandJson("sess-1", 800, "")
        val emptyObj = json.parseToJsonElement(emptyPayload).jsonObject
        assertEquals("start", emptyObj["type"]?.jsonPrimitive?.content)
        assertEquals("sess-1", emptyObj["sessionId"]?.jsonPrimitive?.content)
        assertEquals(800, emptyObj["maxSentenceSilence"]?.jsonPrimitive?.intOrNull)
        assertFalse(emptyObj.containsKey("authToken"))

        val whitespacePayload = buildStartCommandJson("sess-2", 1200, "   \t\n  ")
        val whitespaceObj = json.parseToJsonElement(whitespacePayload).jsonObject
        assertEquals("start", whitespaceObj["type"]?.jsonPrimitive?.content)
        assertEquals("sess-2", whitespaceObj["sessionId"]?.jsonPrimitive?.content)
        assertEquals(1200, whitespaceObj["maxSentenceSilence"]?.jsonPrimitive?.intOrNull)
        assertFalse(whitespaceObj.containsKey("authToken"))
    }

    @Test
    fun buildStartCommandJsonIncludesTrimmedAuthTokenAndEscapesSpecialChars() {
        val rawToken = """  tok-"quoted"\slash  """
        val payload = buildStartCommandJson("sess-auth-1", 500, rawToken)
        val obj = json.parseToJsonElement(payload).jsonObject
        assertEquals("start", obj["type"]?.jsonPrimitive?.content)
        assertEquals("sess-auth-1", obj["sessionId"]?.jsonPrimitive?.content)
        assertEquals(500, obj["maxSentenceSilence"]?.jsonPrimitive?.intOrNull)
        assertEquals("""tok-"quoted"\slash""", obj["authToken"]?.jsonPrimitive?.content)
    }

    @Test
    fun buildWebSocketRequestAttachesBearerHeaderOnlyWhenNonBlank() {
        val url = "wss://voice.tpgofighting.top/ws/asr"

        val withoutToken = buildWebSocketRequest(url, "   ")
        assertNull(withoutToken.header("Authorization"))

        val withToken = buildWebSocketRequest(url, "  sync-ws-token-42  ")
        assertEquals("Bearer sync-ws-token-42", withToken.header("Authorization"))
    }
}
