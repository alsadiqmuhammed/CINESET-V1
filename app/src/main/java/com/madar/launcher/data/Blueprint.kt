package com.madar.launcher.data

import android.graphics.Bitmap
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/** A shareable template: a whole mode setup, or the skeleton of a project stack. Never personal data. */
@Serializable
sealed interface Blueprint {

    @Serializable @SerialName("modes")
    data class Modes(
        val focusStart: Int,
        val focusEnd: Int,
        val sanctuaryStart: Int,
        val sanctuaryEnd: Int,
        val digestTimes: List<Int>,
        val pomodoroMinutes: Int,
        val frictionEnabled: Boolean,
        /** Reclassifications by package name, so the receiver's apps sort the same way. */
        val kinds: Map<String, AppKind> = emptyMap(),
    ) : Blueprint

    @Serializable @SerialName("stack")
    data class StackPlan(val title: String, val items: List<PlanItem>) : Blueprint

    @Serializable
    sealed interface PlanItem {
        @Serializable @SerialName("app") data class App(val packageName: String, val label: String) : PlanItem
        @Serializable @SerialName("link") data class Link(val url: String, val title: String) : PlanItem
        @Serializable @SerialName("note") data class Note(val text: String) : PlanItem
    }
}

object BlueprintCodec {

    const val PREFIX = "MADAR1:"

    /** Byte-mode QR tops out near 2.9 KB; leave headroom for low-end scanners. */
    const val MAX_LENGTH = 2_300

    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "t"; encodeDefaults = false }

    fun encode(bp: Blueprint): String {
        val raw = json.encodeToString(Blueprint.serializer(), bp).toByteArray()
        val deflater = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(raw); finish() }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return PREFIX + Base64.encodeToString(out.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun decode(text: String): Blueprint? = runCatching {
        val body = text.trim().let { t -> if (t.startsWith(PREFIX, ignoreCase = true)) t.substring(PREFIX.length) else return null }
        val packed = Base64.decode(body, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val inflater = Inflater().apply { setInput(packed) }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inflater.end()
        json.decodeFromString(Blueprint.serializer(), out.toString(Charsets.UTF_8.name()))
    }.getOrNull()

    fun qr(text: String, size: Int = 0): BitMatrix =
        QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 0),
        )

    /** Reads a Madar blueprint out of a photo or screenshot of its QR code. */
    fun fromImage(bitmap: Bitmap): Blueprint? = runCatching {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        // Screenshots of older Madar builds (or dark-mode QR apps) may be inverted; try both.
        val result = runCatching { QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints) }
            .getOrElse { QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source.invert())), hints) }
        decode(result.text)
    }.getOrNull()
}

/** Builds and applies blueprints against the live repositories. */
fun Settings.toBlueprint() = Blueprint.Modes(
    focusStart, focusEnd, sanctuaryStart, sanctuaryEnd, digestTimes, pomodoroMinutes, frictionEnabled, kindOverrides,
)

fun Settings.apply(bp: Blueprint.Modes) = copy(
    focusStart = bp.focusStart,
    focusEnd = bp.focusEnd,
    sanctuaryStart = bp.sanctuaryStart,
    sanctuaryEnd = bp.sanctuaryEnd,
    digestTimes = bp.digestTimes,
    pomodoroMinutes = bp.pomodoroMinutes,
    frictionEnabled = bp.frictionEnabled,
    kindOverrides = kindOverrides + bp.kinds,
)

/** Contacts and files are personal; only apps, links and notes travel in a template. */
fun Stack.toBlueprint() = Blueprint.StackPlan(
    title = title,
    items = items.mapNotNull {
        when (it) {
            is StackItem.App -> Blueprint.PlanItem.App(it.component.substringBefore('/'), it.label)
            is StackItem.Link -> Blueprint.PlanItem.Link(it.url, it.title)
            is StackItem.Note -> Blueprint.PlanItem.Note(it.text)
            else -> null
        }
    },
)
