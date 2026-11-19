package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticationFlowTest {
    @Test
    fun emailAccountsMustVerifyButGoogleAccountsDoNot() {
        assertTrue(requiresEmailVerification(emailVerified = false, googleAccount = false))
        assertFalse(requiresEmailVerification(emailVerified = true, googleAccount = false))
        assertFalse(requiresEmailVerification(emailVerified = false, googleAccount = true))
    }

    @Test
    fun newInstallOpensAuthentication() {
        assertEquals(
            AppScreen.AUTH,
            authenticationStartScreen(hasSignedInUser = false, hasEligibleLocalProfile = false, authenticationRequired = false)
        )
    }

    @Test
    fun signedInReturningUserGoesToHome() {
        assertEquals(
            AppScreen.HOME,
            authenticationStartScreen(
                hasSignedInUser = true,
                hasEligibleLocalProfile = true,
                authenticationRequired = true,
                signedInUserId = "firebase-uid",
                localAccountId = "firebase-uid"
            )
        )
    }

    @Test
    fun signedInUserWithoutLocalProfileContinuesProfileSetup() {
        assertEquals(
            AppScreen.REGISTER,
            authenticationStartScreen(hasSignedInUser = true, hasEligibleLocalProfile = false, authenticationRequired = true)
        )
    }

    @Test
    fun existingLocalProfileBypassesSignInAfterUpgrade() {
        assertEquals(
            AppScreen.HOME,
            authenticationStartScreen(hasSignedInUser = false, hasEligibleLocalProfile = true, authenticationRequired = false)
        )
    }

    @Test
    fun linkedLocalProfileRequiresItsAccountEvenIfAuthenticationFlagIsMissing() {
        assertEquals(
            AppScreen.AUTH,
            authenticationStartScreen(
                hasSignedInUser = false,
                hasEligibleLocalProfile = true,
                authenticationRequired = false,
                localAccountId = "profile-owner-uid"
            )
        )
    }

    @Test
    fun signingOutDoesNotLetAnAuthenticatedProfileBypassSignIn() {
        assertEquals(
            AppScreen.AUTH,
            authenticationStartScreen(hasSignedInUser = false, hasEligibleLocalProfile = true, authenticationRequired = true)
        )
    }

    @Test
    fun anotherFirebaseAccountCannotOpenTheLocalProfile() {
        assertEquals(
            AppScreen.AUTH,
            authenticationStartScreen(
                hasSignedInUser = true,
                hasEligibleLocalProfile = true,
                authenticationRequired = true,
                signedInUserId = "other-uid",
                localAccountId = "profile-owner-uid"
            )
        )
    }

    @Test
    fun firstAccountKeepsAnExistingUnboundLocalProfile() {
        assertEquals(
            LEGACY_PROFILE_NAMESPACE,
            profileNamespaceForAccount(LEGACY_PROFILE_NAMESPACE, emptyList(), "first-uid", null)
        )
    }

    @Test
    fun switchingAccountsSelectsSeparateLocalProfilesAndReturningRestoresTheMappedOne() {
        val firstNamespace = profileNamespaceForAccount(
            LEGACY_PROFILE_NAMESPACE,
            emptyList(),
            "first-uid",
            null
        )
        val secondNamespace = profileNamespaceForAccount(
            firstNamespace,
            listOf("first-uid"),
            "second-uid",
            null
        )

        assertEquals(LEGACY_PROFILE_NAMESPACE, firstNamespace)
        assertEquals(accountProfileNamespace("second-uid"), secondNamespace)
        assertEquals(
            firstNamespace,
            profileNamespaceForAccount(secondNamespace, listOf("second-uid"), "first-uid", firstNamespace)
        )
        assertEquals(
            secondNamespace,
            profileNamespaceForAccount(firstNamespace, listOf("first-uid"), "second-uid", secondNamespace)
        )
    }
}
