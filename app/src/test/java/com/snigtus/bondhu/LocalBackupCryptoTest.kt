package com.snigtus.dost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.json.JSONObject
import java.util.Base64

class LocalBackupCryptoTest {
    @Test
    fun encryptedBackupRoundTrips() {
        val source = """{"format":"dost-local-backup","messages":["hello"]}"""
        val encrypted = LocalBackupCrypto.encrypt(source, "a-long-passphrase".toCharArray())

        assertTrue(LocalBackupCrypto.isEncryptedBackup(encrypted))
        assertEquals(source, LocalBackupCrypto.decrypt(encrypted, "a-long-passphrase".toCharArray()))
    }

    @Test
    fun wrongPassphraseDoesNotRevealBackupContents() {
        val encrypted = LocalBackupCrypto.encrypt("private data", "a-long-passphrase".toCharArray())

        try {
            LocalBackupCrypto.decrypt(encrypted, "a-different-passphrase".toCharArray())
            fail("A wrong passphrase must not decrypt the backup.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("passphrase"))
        }
    }

    @Test
    fun modifiedCiphertextIsRejected() {
        val encrypted = JSONObject(LocalBackupCrypto.encrypt("private data", "a-long-passphrase".toCharArray()))
        val ciphertext = Base64.getDecoder().decode(encrypted.getString("ciphertext"))
        ciphertext[0] = (ciphertext[0].toInt() xor 1).toByte()
        encrypted.put("ciphertext", Base64.getEncoder().encodeToString(ciphertext))

        try {
            LocalBackupCrypto.decrypt(encrypted.toString(), "a-long-passphrase".toCharArray())
            fail("Modified ciphertext must not be accepted.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("passphrase"))
        }
    }

    @Test
    fun legacyJsonIsNotMistakenForEncryptedBackup() {
        assertFalse(LocalBackupCrypto.isEncryptedBackup("""{"format":"dost-local-backup","version":1}"""))
        assertFalse(LocalBackupCrypto.isEncryptedBackup("not json"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun encryptionRejectsShortPassphrases() {
        LocalBackupCrypto.encrypt("private data", "too-short".toCharArray())
    }

    @Test
    fun unsupportedKeyDerivationCostIsRejectedBeforeDecryption() {
        val encrypted = JSONObject(LocalBackupCrypto.encrypt("private data", "a-long-passphrase".toCharArray()))
            .put("iterations", 1)

        try {
            LocalBackupCrypto.decrypt(encrypted.toString(), "a-long-passphrase".toCharArray())
            fail("An unsupported key-derivation setting must not be accepted.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("security settings"))
        }
    }
}
