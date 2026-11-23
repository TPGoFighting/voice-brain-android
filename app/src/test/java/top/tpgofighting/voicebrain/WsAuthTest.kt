package top.tpgofighting.voicebrain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.tpgofighting.voicebrain.data.api.applyBearerToken
import top.tpgofighting.voicebrain.data.api.sanitizeSyncToken
import top.tpgofighting.voicebrain.data.websocket.AsrEvent
import top.tpgofighting.voicebrain.data.websocket.WS_CLOSE_UNAUTHORIZED
import top.tpgofighting.voicebrain.data.websocket.WS_UNAUTHORIZED_MESSAGE
import top.tpgofighting.voicebrain.data.websocket.buildStartCommandJson
import top.tpgofighting.voicebrain.data.websocket.buildWebSocketRequest
import top.tpgofighting.voicebrain.data.websocket.decideWsCloseAction
import top.tpgofighting.voicebrain.data.websocket.parseWsErrorFrame

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

    @Test
    fun sanitizeSyncTokenStripsControlAndNonAsciiWithoutThrowingInOkHttp() {
        val dirty = " \r\n\u200B令牌\u3000abc-123_XYZ\t\n中文 "
        assertEquals("abc-123_XYZ", sanitizeSyncToken(dirty))

        val wsReq = buildWebSocketRequest("wss://voice.tpgofighting.top/ws/asr", dirty)
        assertEquals("Bearer abc-123_XYZ", wsReq.header("Authorization"))

        val httpReq = applyBearerToken(
            Request.Builder().url("https://voice.tpgofighting.top/api/sessions").build(),
            dirty
        )
        assertEquals("Bearer abc-123_XYZ", httpReq.header("Authorization"))

        val startObj = json.parseToJsonElement(
            buildStartCommandJson("sess-dirty", 800, dirty)
        ).jsonObject
        assertEquals("abc-123_XYZ", startObj["authToken"]?.jsonPrimitive?.content)

        // All-invalid input produces empty token -> no header and no authToken
        val onlyInvalid = "\r\n\u200B\u3000中文令牌"
        assertEquals("", sanitizeSyncToken(onlyInvalid))
        assertNull(buildWebSocketRequest("wss://voice.tpgofighting.top/ws/asr", onlyInvalid).header("Authorization"))
        assertNull(
            applyBearerToken(
                Request.Builder().url("https://voice.tpgofighting.top/api/sessions").build(),
                onlyInvalid
            ).header("Authorization")
        )
        val invalidStartObj = json.parseToJsonElement(
            buildStartCommandJson("sess-invalid", 800, onlyInvalid)
        ).jsonObject
        assertFalse(invalidStartObj.containsKey("authToken"))
    }

    @Test
    fun parseWsErrorFrameDetects4401Unauthorized() {
        val unauthObj = json.parseToJsonElement(
            """{"type":"error","code":4401,"message":"Unauthorized"}"""
        ).jsonObject
        val unauth = parseWsErrorFrame(unauthObj)
        assertTrue(unauth.isUnauthorized)
        assertEquals(WS_UNAUTHORIZED_MESSAGE, unauth.message)

        val genericObj = json.parseToJsonElement(
            """{"type":"asr_error","message":"ASR 上游超时"}"""
        ).jsonObject
        val generic = parseWsErrorFrame(genericObj)
        assertFalse(generic.isUnauthorized)
        assertEquals("ASR 上游超时", generic.message)
    }

    @Test
    fun decideWsCloseActionStopsReconnectOn4401Unauthorized() {
        val unauth = decideWsCloseAction(WS_CLOSE_UNAUTHORIZED, isRecording = true)
        assertFalse(unauth.shouldReconnect)
        assertTrue(unauth.isAuthFailure)
        assertEquals(AsrEvent.Error(WS_UNAUTHORIZED_MESSAGE), unauth.event)

        val transientDrop = decideWsCloseAction(1006, isRecording = true)
        assertTrue(transientDrop.shouldReconnect)
        assertFalse(transientDrop.isAuthFailure)
        assertTrue(transientDrop.event is AsrEvent.Reconnecting)

        val normalStop = decideWsCloseAction(1000, isRecording = false)
        assertFalse(normalStop.shouldReconnect)
        assertFalse(normalStop.isAuthFailure)
        assertEquals(AsrEvent.Finished, normalStop.event)
    }
}
