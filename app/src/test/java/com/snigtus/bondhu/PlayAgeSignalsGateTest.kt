package com.snigtus.dost

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayAgeSignalsGateTest {
    @Test
    fun aSharedPlayRangeWithALowerBoundBelowThirteenBlocksTheApp() {
        assertTrue(ageSignalBlocksAccess(true, 12))
        assertTrue(ageSignalBlocksAccess(true, 0))
        assertFalse(ageSignalBlocksAccess(false, 12))
        assertFalse(ageSignalBlocksAccess(true, null))
        assertFalse(ageSignalBlocksAccess(true, 13))
        assertFalse(ageSignalBlocksAccess(true, 17))
        assertFalse(ageSignalBlocksAccess(true, 18))
    }

    @Test
    fun sharedPlayAgeRangesProvideCohortHintsWithoutBlockingTeenAccess() {
        assertEquals(DostAgeCohort.TEEN, ageSignalCohort(13, 17))
        assertEquals(DostAgeCohort.TEEN, ageSignalCohort(16, 16))
        assertEquals(DostAgeCohort.ADULT, ageSignalCohort(18, null))
        assertEquals(null, ageSignalCohort(12, 14))
        assertEquals(null, ageSignalCohort(21, 18))
        assertEquals(null, ageSignalCohort(null, null))
    }
}
