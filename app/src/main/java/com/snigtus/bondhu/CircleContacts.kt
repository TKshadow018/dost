package com.snigtus.dost

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.security.SecureRandom

internal const val CIRCLE_HANDLE_PATTERN = "^[a-z][a-z0-9_]{2,19}$"
internal const val CIRCLE_INVITE_TOKEN_PATTERN = "^[a-f0-9]{64}$"
internal const val CIRCLE_INVITE_WEB_HOST = "bisque-bear-900175.hostingersite.com"

internal data class CircleContact(
    val uid: String,
    val status: String,
    val source: String,
    val incoming: Boolean
)

internal data class CircleDirectChat(
    val id: String,
    val peerUid: String,
    val lastMessage: String,
    val lastMessageAtMillis: Long
)

internal data class CirclePrivacyProfile(
    val uid: String,
    val ageCohort: String,
    val handleLookupEnabled: Boolean,
    val lastSeenVisibility: String,
    val onlineVisibility: String,
    val photoVisibility: String
)

internal fun circleDirectChatId(firstUid: String, secondUid: String): String {
    val members = listOf(firstUid, secondUid).sorted()
    return "${members[0]}_${members[1]}"
}

internal fun normalizeCircleHandle(raw: String): String {
    var handle = raw.trim().lowercase()
    if (handle.startsWith("@")) handle = handle.drop(1)
    return handle
}

internal fun isValidCircleHandle(handle: String): Boolean =
    handle.matches(CIRCLE_HANDLE_PATTERN.toRegex())

internal fun newCircleInviteToken(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

internal fun circleInviteWebUrl(token: String): String =
    "https://$CIRCLE_INVITE_WEB_HOST/api/circle_invite.php?t=$token"

internal fun circleInviteDeepLink(token: String): String =
    "dost://circle/invite/$token"

internal fun parseCircleInviteToken(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val value = raw.trim()
    val patterns = listOf(
        Regex("^dost://circle/invite/([a-fA-F0-9]{64})$"),
        Regex("https://$CIRCLE_INVITE_WEB_HOST/api/circle_invite\\.php\\?t=([a-fA-F0-9]{64})", RegexOption.IGNORE_CASE)
    )
    patterns.forEach { pattern ->
        pattern.find(value)?.groupValues?.getOrNull(1)?.lowercase()?.let { return it }
    }
    val token = value.lowercase()
    return token.takeIf { it.matches(CIRCLE_INVITE_TOKEN_PATTERN.toRegex()) }
}

internal fun circleQrBitmap(content: String, size: Int = 720): ImageBitmap {
    val writer = QRCodeWriter()
    val matrix = writer.encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(
            com.google.zxing.EncodeHintType.MARGIN to 1,
            com.google.zxing.EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"
        )
    )
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
    }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap.asImageBitmap()
}

internal fun decodeCircleInviteFromBitmap(bitmap: Bitmap): String? {
    val width = bitmap.width
    val height = bitmap.height
    if (width < 16 || height < 16) return null
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    return try {
        val source = RGBLuminanceSource(width, height, pixels)
        val reader = QRCodeReader()
        val result = reader.decode(
            BinaryBitmap(HybridBinarizer(source)),
            mapOf(DecodeHintType.TRY_HARDER to true)
        )
        parseCircleInviteToken(result.text)
    } catch (_: Exception) {
        null
    }
}
