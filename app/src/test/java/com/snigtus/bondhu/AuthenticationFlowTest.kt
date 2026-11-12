package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthenticationFlowTest {
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
}
