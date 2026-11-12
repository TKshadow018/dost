package com.snigtus.dost

data class OpenFriendRequest(val friendId: String, val nonce: Long)

object ChatLaunch {
    const val EXTRA_FRIEND_ID = "com.snigtus.dost.extra.FRIEND_ID"

    /**
     * Returns the friend id to show on the chat screen.
     * Null keeps the normal splash, register, or home destination.
     */
    fun friendToOpen(hasUserProfile: Boolean, requestedFriendId: String?, knownFriendIds: Set<String>): String? {
        val friendId = requestedFriendId?.trim().orEmpty()
        if (!hasUserProfile || friendId.isEmpty() || friendId !in knownFriendIds) return null
        return friendId
    }
}
