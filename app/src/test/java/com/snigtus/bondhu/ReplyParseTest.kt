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
