package com.snigtus.dost

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

internal class PlayIntegrityAgeEligibilityClient(context: Context) {
    private val appContext = context.applicationContext

    suspend fun attest(uid: String, idToken: String, eligible: Boolean): Long {
        require(uid.isNotBlank()) { "A Firebase account is required for People." }
        require(idToken.isNotBlank()) { "A Firebase sign-in token is required for People." }

        val nonce = newNonce()
        val requestHash = ageEligibilityRequestHash(uid, eligible, nonce)
        val cloudProjectNumber = BuildConfig.FIREBASE_SENDER_ID.toLongOrNull()
            ?: throw IllegalStateException("Play Integrity is not configured for this Firebase project.")
        val manager = IntegrityManagerFactory.createStandard(appContext)
        val provider = manager.prepareIntegrityToken(
            PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .build()
        ).await()
        val integrityToken = provider.request(
            StandardIntegrityTokenRequest.builder()
                .setRequestHash(requestHash)
                .build()
        ).await().token()

        return withContext(Dispatchers.IO) {
            submit(uid, idToken, eligible, nonce, integrityToken)
        }
    }

    private fun submit(
        uid: String,
        idToken: String,
        eligible: Boolean,
        nonce: String,
        integrityToken: String
    ): Long {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer $idToken")
            val body = JSONObject()
                .put("uid", uid)
                .put("eligible", eligible)
                .put("nonce", nonce)
                .put("integrity_token", integrityToken)
                .toString()
                .toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(body) }

            val status = connection.responseCode
            val responseStream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IOException("The age-eligibility service rejected the request (HTTP $status).")
            }
            val response = try {
                JSONObject(responseBody)
            } catch (error: Exception) {
                throw IOException("The age-eligibility service returned an invalid response.", error)
            }
            val expiresAt = response.optLong("eligible_until", 0)
            val now = System.currentTimeMillis() / 1000
            if (response.optBoolean("eligible", false) != eligible
                || expiresAt <= now
                || expiresAt > now + MAX_CLAIM_LIFETIME_SECONDS
            ) {
                throw IOException("The age-eligibility service returned an invalid eligibility claim.")
            }
            return expiresAt
        } finally {
            connection.disconnect()
        }
    }

    private fun newNonce(): String {
        val bytes = ByteArray(NONCE_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private companion object {
        const val ENDPOINT = "https://bisque-bear-900175.hostingersite.com/api/age_eligibility.php"
        const val NONCE_BYTES = 32
        const val MAX_CLAIM_LIFETIME_SECONDS = 21600
    }
}

internal fun ageEligibilityRequestHash(uid: String, eligible: Boolean, nonce: String): String {
    require(uid.isNotBlank())
    require(nonce.matches(Regex("[a-f0-9]{64}")))
    val payload = "dost-age-v1\n$uid\n${if (eligible) "1" else "0"}\n$nonce"
    val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}
