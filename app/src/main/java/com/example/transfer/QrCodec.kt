package com.example.transfer

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.model.QrConnectionPayload
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject

object QrCodec {

    fun encodePayload(payload: QrConnectionPayload): String {
        return JSONObject().apply {
            put("app", "FlashShare")
            put("ssid", payload.ssid)
            put("pass", payload.passphrase)
            put("ip", payload.hostIp)
            put("port", payload.port)
            put("dev", payload.deviceName)
            put("mode", payload.mode)
            put("ghz5", payload.is5Ghz)
        }.toString()
    }

    fun decodePayload(rawText: String): QrConnectionPayload? {
        return try {
            val trimmed = rawText.trim()
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                val withoutScheme = trimmed.substringAfter("://")
                val hostPort = withoutScheme.substringBefore("/")
                val host = hostPort.substringBefore(":")
                val port = hostPort.substringAfter(":", "8080").toIntOrNull() ?: 8080
                return QrConnectionPayload(
                    ssid = "",
                    passphrase = "",
                    hostIp = host,
                    port = port,
                    deviceName = "FlashShare Peer",
                    mode = "WEB"
                )
            }
            val json = JSONObject(trimmed)
            val ip = json.optString("ip", "")
            val port = json.optInt("port", 8988)
            if (ip.isBlank() && json.optString("ssid", "").isBlank()) return null
            QrConnectionPayload(
                ssid = json.optString("ssid", ""),
                passphrase = json.optString("pass", ""),
                hostIp = ip.ifBlank { "192.168.43.1" },
                port = port,
                deviceName = json.optString("dev", "FlashShare Sender"),
                mode = json.optString("mode", "P2P"),
                is5Ghz = json.optBoolean("ghz5", false)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val offset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        }
    }

    fun decodeFromYuv(
        yuvBytes: ByteArray,
        width: Int,
        height: Int
    ): String? {
        return try {
            val source = PlanarYUVLuminanceSource(
                yuvBytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader().apply {
                setHints(
                    mapOf(
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                        DecodeHintType.TRY_HARDER to true
                    )
                )
            }
            reader.decodeWithState(binaryBitmap).text
        } catch (_: Exception) {
            null
        }
    }
}
