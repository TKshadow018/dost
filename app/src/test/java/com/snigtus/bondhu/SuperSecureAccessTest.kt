package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
    }

    @Test
    fun directConversationIdsAreStablePrivateAndOrderIndependent() {
        val first = secureDirectConversationId("alice", "bob")
        val reverse = secureDirectConversationId("bob", "alice")

        assertEquals(first, reverse)
        assertNotEquals("alice-bob", first)
    }
}
