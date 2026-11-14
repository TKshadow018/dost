package com.snigtus.dost

import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object LocalBackupCrypto {
    private const val FORMAT = "dost-encrypted-local-backup"
    private const val VERSION = 1
    private const val ITERATIONS = 200_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128
    private const val MIN_PASSWORD_LENGTH = 12
    private const val MAX_PASSWORD_LENGTH = 256
    private const val MAX_CIPHERTEXT_BYTES = 25_000_000
    private val aad = "com.snigtus.dost.backup.v1".toByteArray(StandardCharsets.UTF_8)

    fun isEncryptedBackup(content: String): Boolean = try {
        JSONObject(content).optString("format") == FORMAT
    } catch (_: JSONException) {
        false
    }

    fun encrypt(plainText: String, password: CharArray): String {
        require(password.size in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "Use a passphrase between 12 and 256 characters."
        }
        val salt = ByteArray(SALT_LENGTH_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_LENGTH_BYTES).also(SecureRandom()::nextBytes)
        val key = deriveKey(password, salt)
        return try {
            val plainBytes = plainText.toByteArray(StandardCharsets.UTF_8)
            try {
                require(plainBytes.size <= MAX_CIPHERTEXT_BYTES - 16) { "The backup is too large to encrypt." }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, iv))
                cipher.updateAAD(aad)
                val encrypted = cipher.doFinal(plainBytes)
                JSONObject()
                    .put("format", FORMAT)
                    .put("version", VERSION)
                    .put("iterations", ITERATIONS)
                    .put("salt", Base64.getEncoder().encodeToString(salt))
                    .put("iv", Base64.getEncoder().encodeToString(iv))
                    .put("ciphertext", Base64.getEncoder().encodeToString(encrypted))
                    .toString()
            } finally {
                plainBytes.fill(0)
            }
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(envelopeText: String, password: CharArray): String {
        require(password.size in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "The passphrase must be between 12 and 256 characters."
        }
        val envelope = JSONObject(envelopeText)
        require(envelope.optString("format") == FORMAT && envelope.optInt("version", -1) == VERSION) {
            "This is not a supported encrypted Dost backup."
        }
        require(envelope.optInt("iterations", -1) == ITERATIONS) {
            "This encrypted backup uses unsupported security settings."
        }
        val salt = decodeField(envelope, "salt", SALT_LENGTH_BYTES)
        val iv = decodeField(envelope, "iv", IV_LENGTH_BYTES)
        val encrypted = Base64.getDecoder().decode(envelope.getString("ciphertext"))
        require(encrypted.size in 16..MAX_CIPHERTEXT_BYTES) { "The encrypted backup is too large or incomplete." }
        val key = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            cipher.updateAAD(aad)
            val plainBytes = cipher.doFinal(encrypted)
            try {
                String(plainBytes, StandardCharsets.UTF_8)
            } finally {
                plainBytes.fill(0)
            }
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("The passphrase is incorrect or the backup file is damaged.")
        } finally {
            key.fill(0)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun decodeField(envelope: JSONObject, field: String, expectedLength: Int): ByteArray {
        val decoded = Base64.getDecoder().decode(envelope.getString(field))
        require(decoded.size == expectedLength) { "The encrypted backup is invalid." }
        return decoded
    }
}
