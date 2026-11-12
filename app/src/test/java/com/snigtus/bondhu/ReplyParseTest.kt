package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyParseTest {
    private val client = OpenRouterClient()

    @Test
    fun validJsonParsesReplyMemoriesAndClampedImpacts() {
        val raw = """
            {"reply":"Good to hear from you.","memories":[{"category":"work_or_study","fact":"User teaches math"}],"follow_up_question":"","follow_up_delay_minutes":null,"friendship_impact":9,"love_impact":-5}
        """.trimIndent()
        val parsed = client.parseModelReply(raw, "server-id")
        assertFalse(parsed.parseFailed)
        assertEquals("Good to hear from you.", parsed.text)
        assertEquals(listOf(UserMemory("work_or_study", "User teaches math")), parsed.memories)
        assertEquals(2, parsed.friendshipImpact)
        assertEquals(-1, parsed.loveImpact)
        assertEquals("server-id", parsed.serverMessageId)
    }

    @Test
    fun fencedJsonStillParses() {
        val raw = "```json\n{\"reply\":\"Hello\",\"friendship_impact\":1,\"love_impact\":0}\n```"
        val parsed = client.parseModelReply(raw)
        assertFalse(parsed.parseFailed)
        assertEquals("Hello", parsed.text)
        assertEquals(1, parsed.friendshipImpact)
    }

    @Test
    fun parsedRepliesKeepAgeGateAndParseFailure() {
        val parsed = client.parseModelReply("""{"reply":"Hello","friendship_impact":2,"love_impact":2}""")
        val minor = applyLoveImpactGate(parsed, "17", "25")
        assertEquals(2, minor.friendshipImpact)
        assertEquals(0, minor.loveImpact)
        assertEquals(2, applyLoveImpactGate(parsed, "25", "25").loveImpact)
        assertTrue(applyLoveImpactGate(client.parseModelReply("{invalid}"), "17", "25").parseFailed)
    }

    @Test
    fun invalidModelTextDoesNotBecomeTheReply() {
        val raw = "Sure! Here is some junk {not json} and a fence."
        val parsed = client.parseModelReply(raw, "server-id")
        assertTrue(parsed.parseFailed)
        assertEquals("", parsed.text)
        assertTrue(parsed.memories.isEmpty())
        assertEquals(0, parsed.friendshipImpact)
        assertEquals(0, parsed.loveImpact)
        assertFalse(parsed.text.contains("junk"))
        assertFalse(parsed.text == raw)
    }

    @Test
    fun plainProseIsKeptWithoutMemoriesOrDeltas() {
        val parsed = client.parseModelReply("Glad you got home safely.")
        assertFalse(parsed.parseFailed)
        assertEquals("Glad you got home safely.", parsed.text)
        assertTrue(parsed.memories.isEmpty())
        assertEquals(0, parsed.friendshipImpact)
        assertEquals(0, parsed.loveImpact)
    }

    @Test
    fun debugLogSnippetIsCappedAtTwoHundredCharacters() {
        val raw = "x".repeat(500)
        assertEquals(OpenRouterClient.MODEL_REPLY_LOG_LIMIT, client.modelReplyDebugSnippet(raw).length)
        assertEquals(200, client.modelReplyDebugSnippet(raw).length)
    }

    @Test
    fun markdownFenceWithoutJsonDoesNotLeak() {
        val raw = "```json\nI refuse to use json\n```"
        val parsed = client.parseModelReply(raw)
        assertTrue(parsed.parseFailed)
        assertEquals("", parsed.text)
        assertTrue(parsed.memories.isEmpty())
        assertEquals(0, parsed.friendshipImpact)
        assertEquals(0, parsed.loveImpact)
    }
}
