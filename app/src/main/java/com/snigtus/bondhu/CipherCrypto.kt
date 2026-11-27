package com.snigtus.dost

import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.proto.AesGcmKey
import com.google.crypto.tink.proto.KeyData
import com.google.crypto.tink.proto.KeyStatusType
import com.google.crypto.tink.proto.Keyset
import com.google.crypto.tink.proto.OutputPrefixType
import com.google.protobuf.ByteString
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.HKDFParameters
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

internal class CipherKeys(
    val messageKey: ByteArray,
    val checkKey: ByteArray
) {
    fun wipe() {
        messageKey.fill(0)
        checkKey.fill(0)
    }
}

internal data class CipherEncryptedMessage(
    val ct: String,
    val nonce: String,
    val version: Int = CIPHER_VERSION
)

internal class WrongCipherPassphraseException : GeneralSecurityException("Wrong passphrase.")

internal object CipherCrypto {
    private val random = SecureRandom()
    private val base64 = Base64.getEncoder()
    private const val ARGON2_MEMORY_KIB = 262_144
    private const val ARGON2_ITERATIONS = 3
    private const val ARGON2_PARALLELISM = 1
    private const val KEY_LENGTH = 32
    private const val NONCE_LENGTH = 12
    private const val TAG_LENGTH = 16
    private val messageInfo = "dost-cipher-message-v1".toByteArray(StandardCharsets.UTF_8)
    private val checkInfo = "dost-cipher-check-key-v1".toByteArray(StandardCharsets.UTF_8)

    fun newSalt(): ByteArray = ByteArray(16).also(random::nextBytes)

    fun deriveKeys(passphrase: CharArray, salt: ByteArray): CipherKeys {
        require(salt.size == 16) { "Cipher salt must be 16 bytes." }
        val password = encodePassphrase(passphrase)
        val root = try {
            argon2id(
                password = password,
                salt = salt,
                memoryKiB = ARGON2_MEMORY_KIB,
                iterations = ARGON2_ITERATIONS,
                parallelism = ARGON2_PARALLELISM
            )
        } finally {
            password.fill(0)
        }
        return try {
            CipherKeys(hkdf(root, messageInfo), hkdf(root, checkInfo))
        } finally {
            root.fill(0)
        }
    }

    fun encryptKeyCheck(chatId: String, key: ByteArray): String =
        base64.encodeToString(aead(key).encrypt(
            "dost-cipher-check-v1".toByteArray(StandardCharsets.UTF_8),
            keyCheckAad(chatId)
        ))

    fun verifyKeyCheck(chatId: String, encodedCheck: String, key: ByteArray) {
        try {
            val plaintext = aead(key).decrypt(base64.decode(encodedCheck), keyCheckAad(chatId))
            try {
                if (!plaintext.contentEquals("dost-cipher-check-v1".toByteArray(StandardCharsets.UTF_8))) {
                    throw WrongCipherPassphraseException()
                }
            } finally {
                plaintext.fill(0)
            }
        } catch (_: GeneralSecurityException) {
            throw WrongCipherPassphraseException()
        } catch (_: IllegalArgumentException) {
            throw WrongCipherPassphraseException()
        }
    }

    fun encryptMessage(
        chatId: String,
        senderUid: String,
        messageId: String,
        text: String,
        key: ByteArray
    ): CipherEncryptedMessage {
        val plaintext = text.toByteArray(StandardCharsets.UTF_8)
        require(isValidCipherMessageText(text))
        val sealed = try {
            aead(key).encrypt(plaintext, messageAad(chatId, senderUid, messageId, CIPHER_VERSION))
        } finally {
            plaintext.fill(0)
        }
        require(sealed.size > NONCE_LENGTH + TAG_LENGTH)
        return CipherEncryptedMessage(
            ct = base64.encodeToString(sealed.copyOfRange(NONCE_LENGTH, sealed.size)),
            nonce = base64.encodeToString(sealed.copyOfRange(0, NONCE_LENGTH))
        )
    }

    fun decryptMessage(
        chatId: String,
        senderUid: String,
        messageId: String,
        message: CipherEncryptedMessage,
        key: ByteArray
    ): String {
        require(message.version == CIPHER_VERSION)
        val nonce = base64.decode(message.nonce)
        val ciphertext = base64.decode(message.ct)
        require(nonce.size == NONCE_LENGTH && ciphertext.size in (TAG_LENGTH + 1)..2_016)
        val plaintext = aead(key).decrypt(
            nonce + ciphertext,
            messageAad(chatId, senderUid, messageId, message.version)
        )
        return try {
            String(plaintext, StandardCharsets.UTF_8)
        } finally {
            plaintext.fill(0)
        }
    }

    fun newMessageId(): String = UUID.randomUUID().toString()

    internal fun argon2id(
        password: ByteArray,
        salt: ByteArray,
        memoryKiB: Int = ARGON2_MEMORY_KIB,
        iterations: Int = ARGON2_ITERATIONS,
        parallelism: Int = ARGON2_PARALLELISM,
        secret: ByteArray = byteArrayOf(),
        additional: ByteArray = byteArrayOf()
    ): ByteArray {
        require(salt.size >= 8)
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withSecret(secret)
            .withAdditional(additional)
            .withMemoryAsKB(memoryKiB)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .build()
        return ByteArray(KEY_LENGTH).also { output ->
            Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password, output)
        }
    }

    internal fun aead(key: ByteArray): Aead {
        require(key.size == 16 || key.size == 32)
        AeadConfig.register()
        val aesKey = AesGcmKey.newBuilder()
            .setVersion(0)
            .setKeyValue(ByteString.copyFrom(key))
            .build()
        val keyData = KeyData.newBuilder()
            .setTypeUrl("type.googleapis.com/google.crypto.tink.AesGcmKey")
            .setValue(aesKey.toByteString())
            .setKeyMaterialType(KeyData.KeyMaterialType.SYMMETRIC)
            .build()
        val keyset = Keyset.newBuilder()
            .setPrimaryKeyId(1)
            .addKey(Keyset.Key.newBuilder()
                .setKeyData(keyData)
                .setStatus(KeyStatusType.ENABLED)
                .setKeyId(1)
                .setOutputPrefixType(OutputPrefixType.RAW))
            .build()
        return CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(keyset.toByteArray()))
            .getPrimitive(Aead::class.java)
    }

    private fun hkdf(input: ByteArray, info: ByteArray): ByteArray =
        ByteArray(KEY_LENGTH).also { output ->
            HKDFBytesGenerator(SHA256Digest()).apply {
                init(HKDFParameters(input, null, info))
                generateBytes(output, 0, output.size)
            }
        }

    private fun encodePassphrase(passphrase: CharArray): ByteArray {
        val encoded = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .encode(CharBuffer.wrap(passphrase))
        val bytes = ByteArray(encoded.remaining())
        encoded.get(bytes)
        if (encoded.hasArray()) encoded.array().fill(0)
        return bytes
    }

    private fun keyCheckAad(chatId: String): ByteArray =
        "dost-cipher-check|$chatId|$CIPHER_VERSION".toByteArray(StandardCharsets.UTF_8)

    private fun messageAad(chatId: String, senderUid: String, messageId: String, version: Int): ByteArray =
        "$chatId|$senderUid|$messageId|$version".toByteArray(StandardCharsets.UTF_8)
}

internal const val CIPHER_VERSION = 1

internal fun isValidCipherPassphrase(passphrase: String): Boolean {
    val words = passphrase.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return words.size >= 5 || passphrase.length >= 20
}

internal fun isValidCipherMessageText(text: String): Boolean =
    text.isNotBlank() && text.toByteArray(StandardCharsets.UTF_8).size <= 2_000

internal fun generatedCipherPassphrase(): String {
    val starts = listOf("b", "br", "ch", "d", "f", "g", "gr", "h", "j", "k", "kr", "l", "m", "n", "p", "pr")
    val vowels = listOf("a", "e", "i", "o", "u", "ai", "ou", "ea")
    val endings = listOf("b", "d", "f", "g", "k", "l", "m", "n")
    val words = linkedSetOf<String>()
    while (words.size < 6) {
        val index = random.nextInt(8_192)
        val prefix = starts[index / 512] + vowels[(index / 64) % 8]
        val suffix = vowels[(index / 8) % 8] + endings[index % 8]
        words += prefix + suffix
    }
    return words.joinToString(" ")
}

internal fun cipherPassphraseStrength(passphrase: String): Int {
    val words = passphrase.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    if (!isValidCipherPassphrase(passphrase) || passphrase.toSet().size < 4
        || (words.size >= 5 && words.distinct().size < 4)
    ) return 0
    return when {
        words.size >= 6 && words.distinct().size >= 6 && passphrase.length >= 24 -> 2
        else -> 1
    }
}
