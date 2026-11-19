package com.snigtus.dost

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayAgeSignalsGateTest {
    @Test
    fun appRequiresSharedAdultAgeRange() {
        assertTrue(adultAgeSignalAllowsAccess(true, 18, null))
        assertTrue(adultAgeSignalAllowsAccess(true, 18, 18))
        assertTrue(adultAgeSignalAllowsAccess(true, 21, null))
        assertFalse(adultAgeSignalAllowsAccess(false, 18, null))
        assertFalse(adultAgeSignalAllowsAccess(true, null, null))
        assertFalse(adultAgeSignalAllowsAccess(true, 16, 17))
        assertFalse(adultAgeSignalAllowsAccess(true, 17, 18))
        assertFalse(adultAgeSignalAllowsAccess(true, 21, 18))
    }

    @Test
    fun selfAttestationIsAvailableOnlyWhenNoPlaySignalCanBeUsed() {
        assertTrue(canSelfAttestAdultAge(PlayAgeAccess.NotShared))
        assertTrue(canSelfAttestAdultAge(PlayAgeAccess.Unavailable("unavailable")))
        assertFalse(canSelfAttestAdultAge(PlayAgeAccess.Checking))
        assertFalse(canSelfAttestAdultAge(PlayAgeAccess.Allowed))
        assertFalse(canSelfAttestAdultAge(PlayAgeAccess.Underage))
        assertFalse(canSelfAttestAdultAge(PlayAgeAccess.VerificationRequired))
    }
}
