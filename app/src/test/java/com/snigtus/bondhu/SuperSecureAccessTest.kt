package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class SuperSecureAccessTest {
    @Test
    fun superSecureSessionDisablesAiUntilTheUserLeavesTheMode() {
        SuperSecureSession.active = false
        assertTrue(superSecureSessionAllowsAi())
        assertTrue(superSecureSessionAllowsTelemetry())

        try {
            SuperSecureSession.active = true
            assertFalse(superSecureSessionAllowsAi())
            assertFalse(superSecureSessionAllowsTelemetry())
        } finally {
            SuperSecureSession.active = false
        }

        assertTrue(superSecureSessionAllowsAi())
    }

    @Test
    fun shieldSessionExcludesTelemetryAndServerArchiveOperations() = runBlocking {
        SuperSecureSession.active = true
        try {
            assertFalse(anonymousTelemetryAllowed(true))
            assertFalse(anonymousTelemetryAllowed(false))
            assertTrue(
                OpenRouterClient().logConversationMessage(
                    "install",
                    "Owner",
                    "friend",
                    "Friend",
                    ChatMessage("user", "Shield-only text")
                )
            )
            try {
                OpenRouterClient().deleteServerArchive("install")
                throw AssertionError("Shield must not delete or read an AI archive.")
            } catch (_: IllegalStateException) {
            }
        } finally {
            SuperSecureSession.active = false
        }
        assertTrue(anonymousTelemetryAllowed(true))
        assertFalse(anonymousTelemetryAllowed(false))
    }

    @Test
    fun onlyOwnersAppointManagersAndManagersCannotRemoveOtherManagers() {
        assertTrue(secureCanManageMembers(SecureOrgRole.OWNER))
        assertTrue(secureCanManageMembers(SecureOrgRole.MANAGER))
        assertFalse(secureCanManageMembers(SecureOrgRole.MEMBER))
        assertTrue(secureCanManageRoles(SecureOrgRole.OWNER))
        assertFalse(secureCanManageRoles(SecureOrgRole.MANAGER))

        assertTrue(secureCanRemoveMember(SecureOrgRole.MANAGER, SecureOrgRole.MEMBER, false))
        assertFalse(secureCanRemoveMember(SecureOrgRole.MANAGER, SecureOrgRole.MANAGER, false))
        assertTrue(secureCanRemoveMember(SecureOrgRole.OWNER, SecureOrgRole.MANAGER, false))
        assertFalse(secureCanRemoveMember(SecureOrgRole.OWNER, SecureOrgRole.OWNER, false))
        assertFalse(secureCanRemoveMember(SecureOrgRole.OWNER, SecureOrgRole.MEMBER, true))
    }

    @Test
    fun organizationMemberCapCountsTheOwnerAndManagers() {
        assertTrue(secureCanAddMembers(1))
        assertTrue(secureCanAddMembers(MAX_SECURE_ORG_MEMBERS - 1))
        assertFalse(secureCanAddMembers(MAX_SECURE_ORG_MEMBERS))
        assertFalse(secureCanAddMembers(0))
        assertTrue(secureCanAddMembers(9, 10))
        assertFalse(secureCanAddMembers(10, 10))
        assertTrue(secureCanAddMembers(24, 25))
        assertFalse(secureCanAddMembers(25, 25))
        assertTrue(secureOrganizationExceedsSeatLimit(11, 10))
        assertFalse(secureOrganizationExceedsSeatLimit(10, 10))
    }

    @Test
    fun organizationsBecomeReadOnlyFourteenDaysAfterPlanExpiry() {
        val expiredAt = 1_000_000L
        assertFalse(secureOrganizationIsReadOnly(expiredAt, expiredAt + 13L * 24 * 60 * 60 * 1000))
        assertTrue(secureOrganizationIsReadOnly(expiredAt, expiredAt + SHIELD_GRACE_MILLIS))
        assertTrue(secureOrganizationIsReadOnly(null, expiredAt))
    }

    @Test
    fun directConversationIdsAreStablePrivateAndOrderIndependent() {
        val first = secureDirectConversationId("alice", "bob")
        val reverse = secureDirectConversationId("bob", "alice")

        assertEquals(first, reverse)
        assertNotEquals("alice-bob", first)
    }
}
