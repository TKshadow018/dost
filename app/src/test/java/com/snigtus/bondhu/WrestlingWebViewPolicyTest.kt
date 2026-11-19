package com.snigtus.dost

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WrestlingWebViewPolicyTest {
    @Test
    fun allowsOnlyTheGameHttpsOriginOnDefaultPort() {
        assertTrue(isAllowedWrestlingGameNavigation("https", "epicfurywrestling.web.app", -1))
        assertTrue(isAllowedWrestlingGameNavigation("HTTPS", "EPICFURYWRESTLING.WEB.APP", 443))
    }

    @Test
    fun rejectsOtherSchemesHostsAndPortsForAllFrames() {
        assertFalse(isAllowedWrestlingGameNavigation("http", "epicfurywrestling.web.app", -1))
        assertFalse(isAllowedWrestlingGameNavigation("https", "outside.example", 443))
        assertFalse(isAllowedWrestlingGameNavigation("https", "epicfurywrestling.web.app.attacker.example", 443))
        assertFalse(isAllowedWrestlingGameNavigation("https", "epicfurywrestling.web.app", 8443))
        assertFalse(isAllowedWrestlingGameNavigation(null, "epicfurywrestling.web.app", -1))
    }
}
