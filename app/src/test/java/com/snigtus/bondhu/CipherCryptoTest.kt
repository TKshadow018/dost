package com.snigtus.dost

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class CipherCryptoTest {
    @Test
    fun argon2idMatchesRfc9106KnownAnswer() {
        val output = CipherCrypto.argon2id(
            password = ByteArray(32) { 1 },
            salt = ByteArray(16) { 2 },
            memoryKiB = 65_536,
            iterations = 3,
            parallelism = 4,
            secret = ByteArray(8) { 3 },
            additional = ByteArray(12) { 4 }
        )
        assertEquals(
            "c814d9d1dc7f37aa13f0d77f2494bda1c8de6b016dd388d29952a4c4672b6ce8",
            output.joinToString("") { "%02x".format(it) }
        )
    }

    @Test
    fun tinkAesGcmDecryptsKnownNistAnswer() {
        val key = ByteArray(16)
        val nonce = ByteArray(12)
        val ciphertextAndTag = hex("0388dace60b6a392f328c2b971b2fe78ab6e47d42cec13bdf53a67b21257bddf")
        val plaintext = CipherCrypto.aead(key).decrypt(nonce + ciphertextAndTag, byteArrayOf())
        assertArrayEquals(ByteArray(16), plaintext)
    }

    @Test
    fun encryptedMessageRoundTripsAndBindsAad() {
        val keys = CipherCrypto.deriveKeys("amber river stone quiet meadow".toCharArray(), ByteArray(16) { 7 })
        try {
            val id = CipherCrypto.newMessageId()
            val encrypted = CipherCrypto.encryptMessage("chat-a", "alice", id, "hello there", keys.messageKey)
            assertEquals("hello there", CipherCrypto.decryptMessage("chat-a", "alice", id, encrypted, keys.messageKey))
            assertThrows(Exception::class.java) {
                CipherCrypto.decryptMessage("chat-b", "alice", id, encrypted, keys.messageKey)
            }
            assertThrows(Exception::class.java) {
                CipherCrypto.decryptMessage("chat-a", "bob", id, encrypted, keys.messageKey)
            }
            assertThrows(Exception::class.java) {
                CipherCrypto.decryptMessage("chat-a", "alice", "different-id", encrypted, keys.messageKey)
            }
            val damaged = encrypted.copy(ct = Base64.getEncoder().encodeToString(byteArrayOf(0) + Base64.getDecoder().decode(encrypted.ct).drop(1)))
            assertThrows(Exception::class.java) {
                CipherCrypto.decryptMessage("chat-a", "alice", id, damaged, keys.messageKey)
            }
        } finally {
            keys.wipe()
        }
    }

    @Test
    fun wrongPassphraseFailsKeyCheck() {
        val salt = ByteArray(16) { 9 }
        val correct = CipherCrypto.deriveKeys("copper garden silent cloud".toCharArray(), salt)
        val wrong = CipherCrypto.deriveKeys("wrong phrase with several words".toCharArray(), salt)
        try {
            val check = CipherCrypto.encryptKeyCheck("chat-a", correct.checkKey)
            CipherCrypto.verifyKeyCheck("chat-a", check, correct.checkKey)
            assertThrows(WrongCipherPassphraseException::class.java) {
                CipherCrypto.verifyKeyCheck("chat-a", check, wrong.checkKey)
            }
        } finally {
            correct.wipe()
            wrong.wipe()
        }
    }

    @Test
    fun passphrasePolicyAndGeneratedStrengthAreEnforced() {
        assertTrue(isValidCipherPassphrase("one two three four five"))
        assertTrue(isValidCipherPassphrase("a".repeat(20)))
        assertFalse(isValidCipherPassphrase("one two three four"))
        assertEquals(6, generatedCipherPassphrase().split(" ").size)
        assertEquals(2, cipherPassphraseStrength(generatedCipherPassphrase()))
        assertEquals(0, cipherPassphraseStrength("repeat repeat repeat repeat repeat repeat"))
    }

    @Test
    fun messageTextLimitUsesUtf8Bytes() {
        assertTrue(isValidCipherMessageText("a".repeat(2_000)))
        assertTrue(isValidCipherMessageText("অ".repeat(666)))
        assertFalse(isValidCipherMessageText("অ".repeat(667)))
        assertFalse(isValidCipherMessageText("   "))
    }

    private fun hex(value: String): ByteArray = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
