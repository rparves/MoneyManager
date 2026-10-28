package com.rparves.moneymanager.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rparves.moneymanager.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** Turns a receipt image/PDF or a spoken sentence into a [Draft]: AI first if configured, on-device otherwise. */
object SmartEntry {

    suspend fun fromImage(ctx: Context, uri: Uri, isPdf: Boolean, catalog: Catalog): SmartResult {
        val bmp = withContext(Dispatchers.Default) { if (isPdf) pdfFirstPage(ctx, uri) else loadImage(ctx, uri) }
        val prefs = ctx.app().prefs
        val today = LocalDate.now()
        var warning: String? = null
        if (Llm.isConfigured(ctx)) {
            try {
                val jpeg = withContext(Dispatchers.Default) { toJpeg(bmp) }
                val reply = Llm.ask(ctx, Prompts.receipt(catalog, prefs.currency, today), jpeg)
                return SmartResult(Prompts.parse(reply), usedAi = true)
            } catch (e: Exception) {
                warning = "AI couldn't read it (${e.message}). Used on-device reading instead."
            }
        }
        val text = try {
            ocr(bmp)
        } catch (e: Exception) {
            throw IllegalStateException("Couldn't read text from the image. ${e.message.orEmpty()}".trim())
        }
        if (text.isBlank()) throw IllegalStateException("No text found. Try a sharper, well-lit photo.")
        return SmartResult(LocalParser.receipt(text, catalog, today), usedAi = false, warning = warning)
    }

    suspend fun fromSpeech(ctx: Context, spoken: String, catalog: Catalog): SmartResult {
        val prefs = ctx.app().prefs
        val today = LocalDate.now()
        var warning: String? = null
        if (Llm.isConfigured(ctx)) {
            try {
                val reply = Llm.ask(ctx, Prompts.voice(catalog, prefs.currency, today, spoken), null)
                return SmartResult(Prompts.parse(reply), usedAi = true, heard = spoken)
            } catch (e: Exception) {
                warning = "AI unavailable (${e.message}). Used on-device matching instead."
            }
        }
        return SmartResult(LocalParser.voice(spoken, catalog, today), usedAi = false, heard = spoken, warning = warning)
    }

    // ---------- images ----------
    private const val MAX_SIDE = 1600

    private fun loadImage(ctx: Context, uri: Uri): Bitmap {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val raw = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("Couldn't open that image")
        val rotation = runCatching {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        val scale = minOf(1f, MAX_SIDE.toFloat() / max(raw.width, raw.height))
        if (scale == 1f && rotation == 0f) return raw
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }

    private fun pdfFirstPage(ctx: Context, uri: Uri): Bitmap {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalStateException("Couldn't open that PDF")
        val renderer = PdfRenderer(pfd)
        try {
            if (renderer.pageCount == 0) throw IllegalStateException("The PDF has no pages")
            val page = renderer.openPage(0)
            try {
                val w = MAX_SIDE
                val h = (w.toFloat() * page.height / page.width).toInt().coerceIn(1, MAX_SIDE * 2)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp
            } finally {
                page.close()
            }
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    private fun toJpeg(b: Bitmap): ByteArray =
        ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()

    private suspend fun ocr(b: Bitmap): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(b, 0))
            .addOnSuccessListener { cont.resume(it.text) }
            .addOnFailureListener { cont.resumeWithException(it) }
            .addOnCompleteListener { recognizer.close() }
    }
}
