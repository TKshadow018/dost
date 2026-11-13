package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryMergeTest {
    private val sylhet = UserMemory("address", "User lives in Sylhet")
    private val dhaka = UserMemory("address", "User lives in Dhaka", replaces = "User lives in Sylhet")

    @Test
    fun categoryIsShownSeparatelyFromTheFact() {
        assertEquals("Work or study", displayMemoryCategory("work_or_study"))
        assertEquals("কাজ বা পড়াশোনা", memoryCategoryLabel("work_or_study", AppLanguage.BANGLA))
        assertEquals("काम या पढ़ाई", memoryCategoryLabel("work_or_study", AppLanguage.HINDI))
        assertEquals("Address", memoryCategoryLabel("address", AppLanguage.ENGLISH))
    }

    @Test
    fun exactDuplicateIsNotStoredTwice() {
        val merged = mergeUserMemories(listOf(sylhet), listOf(UserMemory("address", "user lives in sylhet")))
        assertEquals(listOf(sylhet), merged)
    }

    @Test
    fun correctionReplacesTheOldFactInsteadOfAppending() {
        val merged = mergeUserMemories(listOf(sylhet, UserMemory("profile", "User teaches math")), listOf(dhaka))
        assertEquals(2, merged.size)
        assertEquals("User lives in Dhaka", merged[0].fact)
        assertEquals("", merged[0].replaces)
        assertFalse(merged.any { it.fact.contains("Sylhet") })
        assertTrue(merged.any { it.fact == "User teaches math" })
    }

    @Test
    fun nearDuplicateCorrectionStillReplacesTheStoredFact() {
        val stored = UserMemory("address", "User lives in a tiny apartment in Sylhet")
        val correction = UserMemory(
            "address",
            "User lives in a tiny apartment in Dhaka",
            replaces = "User lives in a tiny apartment in Sylhet."
        )
        val merged = mergeUserMemories(listOf(stored), listOf(correction))
        assertEquals(listOf("User lives in a tiny apartment in Dhaka"), merged.map { it.fact })
    }

    @Test
    fun shortUnrelatedTextDoesNotWipeAnExistingFact() {
        val correction = UserMemory("address", "User lives in Dhaka", replaces = "in")
        val merged = mergeUserMemories(listOf(sylhet), listOf(correction))
        assertEquals(listOf("User lives in Sylhet", "User lives in Dhaka"), merged.map { it.fact })
    }

    @Test
    fun blankFactsAreIgnoredAndTheStoredListStaysCapped() {
        val existing = List(1000) { index -> UserMemory("other", "Fact $index") }
        val merged = mergeUserMemories(existing, listOf(UserMemory("other", "  "), UserMemory("other", "Fact newest")))
        assertEquals(MAX_STORED_USER_MEMORIES, merged.size)
        assertEquals("Fact 1", merged.first().fact)
        assertEquals("Fact newest", merged.last().fact)
        assertFalse(merged.any { it.fact.isBlank() })
    }

    @Test
    fun userCanReplaceAndDeleteAFact() {
        val math = UserMemory("work_or_study", "User teaches math")
        val replaced = replaceUserMemory(listOf(sylhet, math), math, UserMemory("education", "User teaches physics"))
        assertEquals(listOf(sylhet.fact, "User teaches physics"), replaced.map { it.fact })
        assertEquals("education", replaced[1].category)
        assertEquals(listOf(sylhet, math), replaceUserMemory(listOf(sylhet, math), math, UserMemory("education", "   ")))
        assertEquals(listOf(sylhet), withoutUserMemory(listOf(sylhet, math), math))
    }

    @Test
    fun correctingIntoAnExistingFactDoesNotLeaveADuplicate() {
        val existing = listOf(sylhet, UserMemory("address", "User lives in Dhaka"))
        val merged = mergeUserMemories(existing, listOf(dhaka))
        assertEquals(listOf("User lives in Dhaka"), merged.map { it.fact })
    }
}
