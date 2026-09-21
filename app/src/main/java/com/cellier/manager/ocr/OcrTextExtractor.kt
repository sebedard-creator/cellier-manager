package com.cellier.manager.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrTextExtractor {

    suspend fun loadBitmap(photoPath: String): Bitmap? = withContext(Dispatchers.IO) {
        val decoded = decodeSampledBitmap(photoPath, maxDimension = 2400) ?: return@withContext null
        applyExifRotation(photoPath, decoded)
    }

    fun mapRectToBitmap(
        viewRect: RectF,
        viewWidth: Float,
        viewHeight: Float,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): Rect? {
        if (viewWidth <= 0f || viewHeight <= 0f || bitmapWidth <= 0 || bitmapHeight <= 0) {
            return null
        }

        val scale = minOf(
            viewWidth / bitmapWidth.toFloat(),
            viewHeight / bitmapHeight.toFloat()
        )
        val offsetX = (viewWidth - bitmapWidth * scale) / 2f
        val offsetY = (viewHeight - bitmapHeight * scale) / 2f

        val left = ((viewRect.left - offsetX) / scale).toInt().coerceIn(0, bitmapWidth)
        val top = ((viewRect.top - offsetY) / scale).toInt().coerceIn(0, bitmapHeight)
        val right = ((viewRect.right - offsetX) / scale).toInt().coerceIn(0, bitmapWidth)
        val bottom = ((viewRect.bottom - offsetY) / scale).toInt().coerceIn(0, bitmapHeight)

        if (right - left < 12 || bottom - top < 12) return null
        return Rect(left, top, right, bottom)
    }

    fun cropBitmap(originalBitmap: Bitmap, selectionRect: Rect): Bitmap? {
        if (selectionRect.width() <= 0 || selectionRect.height() <= 0) return null
        return Bitmap.createBitmap(
            originalBitmap,
            selectionRect.left,
            selectionRect.top,
            selectionRect.width(),
            selectionRect.height()
        )
    }

    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bitmap, 0)
        val task = recognizer.process(image)

        task.addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result.text.trim())
        }
        task.addOnFailureListener { error ->
            if (cont.isActive) cont.resumeWithException(error)
        }
        task.addOnCanceledListener {
            cont.cancel()
        }
        task.addOnCompleteListener {
            runCatching { recognizer.close() }
        }
        cont.invokeOnCancellation {
            runCatching { recognizer.close() }
        }
    }

    private fun decodeSampledBitmap(photoPath: String, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photoPath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension ||
            bounds.outHeight / sampleSize > maxDimension
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(photoPath, options)
    }

    private fun applyExifRotation(photoPath: String, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(photoPath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
