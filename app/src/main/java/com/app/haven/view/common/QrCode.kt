package com.app.haven.view.common

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

// Draws the text as a QR code, always black on white because that scans most reliably.
fun qrBitmap(text: String, size: Int = 768): Bitmap {
    val matrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1)
    )
    val pixels = IntArray(size * size) { i ->
        if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

// Rebuilds only when the text changes, so regenerating the house code redraws the QR automatically.
@Composable
fun QrCodeImage(text: String, modifier: Modifier = Modifier) {
    val image = remember(text) { qrBitmap(text).asImageBitmap() }
    Image(bitmap = image, contentDescription = "Household QR code", modifier = modifier)
}

private const val QR_JOIN_PREFIX = "HAVEN-JOIN:"

// What the household QR contains. It changes whenever the invite code does.
fun joinQrPayload(inviteCode: String): String = QR_JOIN_PREFIX + inviteCode

// The invite code inside a scanned QR, or null if it isn't a Haven invite.
fun inviteCodeFromScan(raw: String): String? =
    raw.trim().takeIf { it.startsWith(QR_JOIN_PREFIX) }
        ?.removePrefix(QR_JOIN_PREFIX)?.trim()?.uppercase()
        ?.takeIf { it.isNotEmpty() }