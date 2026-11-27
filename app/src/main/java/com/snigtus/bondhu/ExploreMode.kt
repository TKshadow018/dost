package com.snigtus.dost

internal fun exploreRequestAllowed(exploreEnabled: Boolean): Boolean = exploreEnabled

internal fun exploreDisclosureRequired(enabled: Boolean, disclosureAccepted: Boolean): Boolean =
    enabled && !disclosureAccepted
