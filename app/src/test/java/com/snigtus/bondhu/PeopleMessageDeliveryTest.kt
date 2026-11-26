package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PeopleMessageDeliveryTest {
    @Test
    fun messageIdAcceptsUuidAndRejectsFirestorePathSeparators() {
        assertTrue(isValidSocialMessageId("f9a58f5f-72aa-44bc-908c-92c7f5b5e1ab"))
        assertFalse(isValidSocialMessageId(""))
        assertFalse(isValidSocialMessageId("../other-chat"))
    }

    @Test
    fun confirmedSnapshotReplacesOptimisticCopyAndKeepsFailedRetryVisible() {
        val confirmed = SocialChatMessage(
            id = "sent-message",
            senderUid = "me",
            text = "Delivered",
            createdAtMillis = 200L
        )
        val pending = listOf(
            confirmed.copy(createdAtMillis = 190L, deliveryState = SocialMessageDeliveryState.SENDING),
            SocialChatMessage(
                id = "failed-message",
                senderUid = "me",
                text = "Retry me",
                createdAtMillis = 300L,
                deliveryState = SocialMessageDeliveryState.FAILED
            ),
            SocialChatMessage(
                id = "sending-message",
                senderUid = "me",
                text = "Sending",
                createdAtMillis = 250L,
                deliveryState = SocialMessageDeliveryState.SENDING
            )
        )

        val merged = mergeSocialMessages(listOf(confirmed), pending)

        assertEquals(listOf("sent-message", "sending-message", "failed-message"), merged.map { it.id })
        assertEquals(SocialMessageDeliveryState.SENT, merged.first().deliveryState)
        assertEquals(1, merged.count { it.id == "sent-message" })
        assertEquals(SocialMessageDeliveryState.FAILED, merged.last().deliveryState)
    }

    @Test
    fun olderHistoryPagesMergeChronologicallyAndPreferLiveWindowOnOverlap() {
        val liveWindow = listOf(
            SocialChatMessage("overlap", "peer", "Current copy", 20L),
            SocialChatMessage("newer", "me", "Newer", 30L)
        )
        val olderPage = listOf(
            SocialChatMessage("oldest", "peer", "Older", 10L),
            SocialChatMessage("overlap", "peer", "Stale copy", 15L)
        )

        val merged = mergeSocialMessages(liveWindow, olderPage)

        assertEquals(listOf("oldest", "overlap", "newer"), merged.map { it.id })
        assertEquals("Current copy", merged[1].text)
    }

    @Test
    fun persistedOutboxStatesRestoreAsWaitingOrFailed() {
        val waiting = PendingPeopleMessage(
            uid = "me",
            conversationId = "chat",
            messageId = "waiting",
            text = "Send later",
            createdAtMillis = 100L,
            status = PendingPeopleMessageStatus.WAITING
        )
        val failed = waiting.copy(messageId = "failed", status = PendingPeopleMessageStatus.FAILED)

        assertEquals(SocialMessageDeliveryState.WAITING, waiting.toSocialChatMessage().deliveryState)
        assertEquals(SocialMessageDeliveryState.FAILED, failed.toSocialChatMessage().deliveryState)
        assertTrue(isRetryablePeopleMessageFailure(IOException("offline")))
        assertFalse(isRetryablePeopleMessageFailure(IllegalStateException("invalid message")))
    }
}
