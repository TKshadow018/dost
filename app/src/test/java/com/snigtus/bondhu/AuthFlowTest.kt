package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFlowTest {
    @Test
    fun emailAndPasswordValidationMatchesFirebaseRequirements() {
        assertEquals(AuthValidationError.INVALID_EMAIL, authValidationError("not-an-email", "secret1", false))
        assertEquals(AuthValidationError.PASSWORD_REQUIRED, authValidationError("a@example.com", "", false))
        assertEquals(AuthValidationError.PASSWORD_TOO_SHORT, authValidationError("a@example.com", "12345", true))
        assertNull(authValidationError(" a@example.com ", "12345", false))
        assertNull(authValidationError("a@example.com", "123456", true))
    }

    @Test
    fun authenticationKeepsEligibleLocalProfilesAndOnboardsNewUsers() {
        assertEquals(AppScreen.HOME, authenticatedDestination(hasEligibleProfile = true))
        assertEquals(AppScreen.REGISTER, authenticatedDestination(hasEligibleProfile = false))
    }

    @Test
    fun localProfileCanOnlyBeLinkedOnceAndReusedByItsAccount() {
        assertTrue(canUseLocalProfile("", "account-1"))
        assertTrue(canUseLocalProfile("account-1", "account-1"))
        assertFalse(canUseLocalProfile("account-1", "account-2"))
    }

    @Test
    fun emailVerificationIsRequiredOnlyForUnverifiedNonGoogleAccounts() {
        assertTrue(requiresEmailVerification(emailVerified = false, googleAccount = false))
        assertFalse(requiresEmailVerification(emailVerified = true, googleAccount = false))
        assertFalse(requiresEmailVerification(emailVerified = false, googleAccount = true))
    }
}
