package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppCredentialTest {
    @Test
    fun emptySecretOmitsTheHeader() {
        assertNull(dostAppCredentialValue("11111111-1111-4111-8111-111111111111", 1700000000L, ""))
    }

    @Test
    fun matchesTheServerPayload() {
        assertEquals(
            "v1.1700000000.6583f387a240d44cb150b813082463e6a72dea26ae6cbec862b5d90152e87e61",
            dostAppCredentialValue(
                "11111111-1111-4111-8111-111111111111",
                1700000000L,
                "test-secret-value-0123456789"
            )
        )
    }
}
