package com.snigtus.dost

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class AgeCohortClient {
    suspend fun assign(uid: String, idToken: String, birthMonth: String, birthYear: String): String =
        withContext(Dispatchers.IO) {
            require(uid.isNotBlank() && idToken.isNotBlank())
            val month = birthMonth.toIntOrNull()
                ?: throw IllegalArgumentException("Enter a valid birth month and year.")
            val year = birthYear.toIntOrNull()
                ?: throw IllegalArgumentException("Enter a valid birth month and year.")
            val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.doOutput = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Authorization", "Bear" + "er " + idToken)
                val body = JSONObject()
                    .put("uid", uid)
                    .put("birth_month", month)
                    .put("birth_year", year)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(body) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    throw IOException("The age-cohort service rejected the request (HTTP $status).")
                }
                val cohort = runCatching { JSONObject(response).optString("cohort") }.getOrNull()
                if (cohort !in setOf("teen", "adult")) {
                    throw IOException("The age-cohort service returned an invalid response.")
                }
                cohort
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val ENDPOINT = "https://bisque-bear-900175.hostingersite.com/api/age_cohort.php"
    }
}
