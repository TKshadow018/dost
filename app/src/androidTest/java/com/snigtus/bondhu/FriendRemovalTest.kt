package com.snigtus.dost

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FriendRemovalTest {
    @Test
    fun coldStoreRemovalDeletesTranscriptImagesAndFriendPhotos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val friendId = "removal-${UUID.randomUUID()}"
        val catalogId = "catalog-${UUID.randomUUID()}"
        val friendPhotoDirectory = File(context.filesDir, "system-friend-photos").apply { mkdirs() }
        val profilePhoto = File(friendPhotoDirectory, "profile-$friendId.image").apply { writeText("profile") }
        val catalogPhoto = File(friendPhotoDirectory, "$catalogId.image").apply { writeText("catalog") }
        val chatPhoto = File(File(context.filesDir, "chat_images").apply { mkdirs() }, "$friendId.jpg").apply { writeText("chat") }
        val transcript = File(File(context.filesDir, "messages"), "$friendId.json")
        val friend = Friend(
            id = friendId,
            name = "Test friend",
            age = "20",
            gender = "Other",
            photoUri = Uri.fromFile(profilePhoto).toString(),
            catalogId = catalogId
        )

        try {
            DostStore(context).saveMessages(
                friendId,
                listOf(ChatMessage(role = "user", content = "Photo", imagePath = chatPhoto.absolutePath))
            )
            val store = DostStore(context)
            val event = PlannedEvent(
                id = UUID.randomUUID().toString(),
                description = "Interview",
                dueAtMillis = System.currentTimeMillis() + 60_000L,
                confidence = "confirmed",
                followUpRequested = true
            )
            val proactive = ProactiveMessage(
                id = UUID.randomUUID().toString(),
                friendId = friendId,
                trigger = ProactiveTrigger.USER_REMINDER,
                sessionId = UUID.randomUUID().toString(),
                dueAtMillis = event.dueAtMillis,
                reason = "user_requested_reminder",
                idempotencyKey = event.id,
                eventId = event.id
            )
            store.savePlannedEvent(friendId, event)
            store.saveProactiveMessage(proactive)
            store.pauseProactive(friendId, Long.MAX_VALUE)
            assertTrue(transcript.isFile)
            assertTrue(chatPhoto.isFile)
            assertTrue(profilePhoto.isFile)
            assertTrue(catalogPhoto.isFile)

            DostStore(context).deleteFriendData(friend)

            assertFalse(transcript.exists())
            assertFalse(chatPhoto.exists())
            assertFalse(profilePhoto.exists())
            assertFalse(catalogPhoto.exists())
            assertTrue(store.loadPlannedEvents(friendId).isEmpty())
            assertTrue(store.proactiveMessage(proactive.id) == null)
            assertFalse(store.isProactivePaused(friendId))
        } finally {
            DostStore(context).deleteFriendData(friend)
        }
    }
}
