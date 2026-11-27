package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Test

class SplashBrandLanguageTest {
    @Test
    fun nonEnglishAppLanguageTakesPriorityOverChatLanguage() {
        assertEquals(
            AppLanguage.HINDI,
            splashBrandLanguage(AppLanguage.HINDI, AppLanguage.ENGLISH)
        )
        assertEquals(
            AppLanguage.BANGLA,
            splashBrandLanguage(AppLanguage.BANGLA, AppLanguage.HINDI)
        )
    }

    @Test
    fun nonEnglishChatLanguageIsUsedWhenAppLanguageIsEnglish() {
        assertEquals(
            AppLanguage.BANGLA,
            splashBrandLanguage(AppLanguage.ENGLISH, AppLanguage.BANGLA)
        )
    }

    @Test
    fun englishIsUsedOnlyWhenBothLanguagesAreEnglish() {
        assertEquals(
            AppLanguage.ENGLISH,
            splashBrandLanguage(AppLanguage.ENGLISH, AppLanguage.ENGLISH)
        )
    }
}
