package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizedTextTest {
    @Test
    fun requestedSurfaceCopyHasBanglaAndHindiTranslations() {
        LocalizedText.values().forEach { copy ->
            assertNotEquals(copy.name, copy.resolve(AppLanguage.ENGLISH), copy.resolve(AppLanguage.BANGLA))
            assertNotEquals(copy.name, copy.resolve(AppLanguage.ENGLISH), copy.resolve(AppLanguage.HINDI))
        }
    }

    @Test
    fun formattedCopyKeepsDynamicValuesInEveryLanguage() {
        val friend = "Rina"
        AppLanguage.values().forEach { language ->
            assertTrue(LocalizedText.CHAT_WITH_FRIEND.resolve(language, friend).contains(friend))
            assertTrue(LocalizedText.BUSY_SCHEDULE_NOTE.resolve(language, "Asia/Dhaka").contains("Asia/Dhaka"))
        }
    }

    @Test
    fun languageSelectionChangesTheRenderedCopy() {
        assertEquals("নাম", LocalizedText.FRIEND_NAME.resolve(AppLanguage.BANGLA))
        assertEquals("नाम", LocalizedText.FRIEND_NAME.resolve(AppLanguage.HINDI))
        assertEquals("Name", LocalizedText.FRIEND_NAME.resolve(AppLanguage.ENGLISH))
    }

    @Test
    fun floatingPanelFriendshipStatusFollowsTheSelectedLanguage() {
        assertEquals("Complete stranger", friendshipLevel(0, AppLanguage.ENGLISH))
        assertEquals("সম্পূর্ণ অপরিচিত", friendshipLevel(0, AppLanguage.BANGLA))
        assertEquals("बिल्कुल अजनबी", friendshipLevel(0, AppLanguage.HINDI))
    }
}
