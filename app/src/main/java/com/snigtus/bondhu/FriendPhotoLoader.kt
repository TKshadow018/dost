package com.snigtus.dost

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal object FriendPhotoLoader {
    private const val MAX_PHOTO_BYTES = 5 * 1024 * 1024
    private val validImageTypes = setOf("image/jpeg", "image/png", "image/webp")

    fun load(context: Context, photoUri: String): Bitmap? {
        if (photoUri.isBlank()) return null
        val uri = Uri.parse(photoUri)
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> downloadRemotePhoto(context, photoUri)?.let { BitmapFactory.decodeFile(it.path) }
            "file" -> uri.path?.let(BitmapFactory::decodeFile)
            else -> context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }
    }

    fun saveRemotePhoto(context: Context, photoUrl: String, catalogId: String): File {
        val cachedPhoto = downloadRemotePhoto(context, photoUrl)
            ?: throw IOException("Could not download the system friend's photo")
        val directory = File(context.filesDir, "system-friend-photos")
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Could not prepare storage for the system friend's photo")
        }
        val safeId = catalogId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val target = File(directory, "$safeId.image")
        val temporary = File(directory, "$safeId.image.tmp")
        cachedPhoto.copyTo(temporary, overwrite = true)
        if (target.exists() && !target.delete()) {
            temporary.delete()
            throw IOException("Could not update the system friend's photo")
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Could not save the system friend's photo")
        }
        return target
    }

    private fun downloadRemotePhoto(context: Context, photoUrl: String): File? {
        val cacheDirectory = File(context.cacheDir, "system-friend-photos")
        if (!cacheDirectory.isDirectory && !cacheDirectory.mkdirs() && !cacheDirectory.isDirectory) {
            return null
        }
        val cacheName = MessageDigest.getInstance("SHA-256")
            .digest(photoUrl.toByteArray())
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        val cachedPhoto = File(cacheDirectory, cacheName)
        if (cachedPhoto.isFile && cachedPhoto.length() in 1..MAX_PHOTO_BYTES.toLong()) {
            return cachedPhoto
        }
        val temporary = File(cacheDirectory, "$cacheName.tmp")
        val connection = URL(photoUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.useCaches = false
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Could not load system friend photo (HTTP ${connection.responseCode})")
            }
            val contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
            if (contentType !in validImageTypes || connection.contentLengthLong > MAX_PHOTO_BYTES) {
                throw IOException("System friend photo has an unsupported format or is too large")
            }
            var totalBytes = 0
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        if (totalBytes > MAX_PHOTO_BYTES) {
                            throw IOException("System friend photo is too large")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (totalBytes == 0 || BitmapFactory.decodeFile(temporary.path) == null) {
                throw IOException("System friend photo is not a valid image")
            }
            if (cachedPhoto.exists() && !cachedPhoto.delete()) {
                throw IOException("Could not update the system friend photo cache")
            }
            if (!temporary.renameTo(cachedPhoto)) {
                throw IOException("Could not cache the system friend photo")
            }
            return cachedPhoto
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }
}
