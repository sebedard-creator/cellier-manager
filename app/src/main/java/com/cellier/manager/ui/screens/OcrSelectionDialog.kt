package com.cellier.manager.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cellier.manager.R
import com.cellier.manager.ocr.OcrTextExtractor
import com.cellier.manager.ui.theme.WineAccent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrSelectionDialog(
    photoPath: String,
    producerFilled: Boolean,
    nameFilled: Boolean,
    vintageFilled: Boolean,
    onDismiss: () -> Unit,
    onUseProducer: (String) -> Unit,
    onUseName: (String) -> Unit,
    onUseVintage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var bitmap by remember(photoPath) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(photoPath) { mutableStateOf(true) }
    var isRecognizing by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    val selectionTooSmallText = stringResource(R.string.ocr_selection_too_small)
    val noTextDetectedText = stringResource(R.string.ocr_no_text)

    LaunchedEffect(photoPath) {
        isLoading = true
        bitmap = OcrTextExtractor.loadBitmap(photoPath)
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.ocr_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.fiche_edit_cancel)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.ocr_select_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val loadedBitmap = bitmap
                    when {
                        isLoading -> CircularProgressIndicator()
                        loadedBitmap == null -> Text(
                            text = stringResource(R.string.ocr_load_failed),
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> OcrSelectionView(
                            bitmap = loadedBitmap,
                            enabled = !isRecognizing,
                            onSelection = { bounds, viewWidth, viewHeight ->
                                val rect = OcrTextExtractor.mapRectToBitmap(
                                    viewRect = bounds,
                                    viewWidth = viewWidth,
                                    viewHeight = viewHeight,
                                    bitmapWidth = loadedBitmap.width,
                                    bitmapHeight = loadedBitmap.height
                                )
                                val cropped = rect?.let { OcrTextExtractor.cropBitmap(loadedBitmap, it) }

                                if (cropped == null) {
                                    errorText = selectionTooSmallText
                                    return@OcrSelectionView
                                }

                                scope.launch {
                                    isRecognizing = true
                                    errorText = null
                                    recognizedText = runCatching {
                                        OcrTextExtractor.recognizeText(cropped)
                                    }.getOrElse {
                                        errorText = it.localizedMessage
                                            ?: "OCR"
                                        ""
                                    }
                                    if (recognizedText.isBlank() && errorText == null) {
                                        errorText = noTextDetectedText
                                    }
                                    isRecognizing = false
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (isRecognizing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = stringResource(R.string.ocr_processing),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (errorText != null) {
                    Text(
                        text = errorText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = recognizedText,
                    onValueChange = { recognizedText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ocr_result)) },
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OcrUseButton(
                        label = stringResource(R.string.ocr_use_producer),
                        isFilled = producerFilled,
                        enabled = recognizedText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onUseProducer(recognizedText.cleanOcrField())
                            recognizedText = ""
                            errorText = null
                        }
                    )
                    OcrUseButton(
                        label = stringResource(R.string.ocr_use_name),
                        isFilled = nameFilled,
                        enabled = recognizedText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onUseName(recognizedText.cleanOcrField())
                            recognizedText = ""
                            errorText = null
                        }
                    )
                    OcrUseButton(
                        label = stringResource(R.string.ocr_use_vintage),
                        isFilled = vintageFilled,
                        enabled = recognizedText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onUseVintage(extractVintage(recognizedText) ?: recognizedText.cleanOcrField())
                            recognizedText = ""
                            errorText = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OcrUseButton(
    label: String,
    isFilled: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
        if (isFilled) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.fiche_match_perfect),
                tint = WineAccent,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun OcrSelectionView(
    bitmap: Bitmap,
    enabled: Boolean,
    onSelection: (RectF, Float, Float) -> Unit
) {
    AndroidView(
        factory = { context ->
            OcrSelectionFrame(context).apply {
                setSelectionEnabled(enabled)
                setBitmap(bitmap)
                onSelectionFinished = onSelection
            }
        },
        update = { view ->
            view.setSelectionEnabled(enabled)
            view.setBitmap(bitmap)
            view.onSelectionFinished = onSelection
        },
        modifier = Modifier.fillMaxSize()
    )
}

private class OcrSelectionFrame(context: Context) : FrameLayout(context) {
    private val imageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = false
    }
    private val overlay = SelectionOverlayView(context)

    var onSelectionFinished: ((RectF, Float, Float) -> Unit)? = null
        set(value) {
            field = value
            overlay.onSelectionFinished = { rect, width, height ->
                field?.invoke(rect, width, height)
            }
        }

    init {
        addView(
            imageView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(
            overlay,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun setBitmap(bitmap: Bitmap) {
        if (imageView.drawable == null || imageView.tag !== bitmap) {
            imageView.setImageBitmap(bitmap)
            imageView.tag = bitmap
            overlay.reset()
        }
    }

    fun setSelectionEnabled(enabled: Boolean) {
        overlay.isEnabled = enabled
    }
}

private class SelectionOverlayView(context: Context) : View(context) {
    private val path = Path()
    private val bounds = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(102, 255, 235, 59)
        style = Paint.Style.STROKE
        strokeWidth = 60f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    var onSelectionFinished: ((RectF, Float, Float) -> Unit)? = null

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                path.reset()
                path.moveTo(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                path.lineTo(event.x, event.y)
                path.computeBounds(bounds, true)
                invalidate()
                onSelectionFinished?.invoke(RectF(bounds), width.toFloat(), height.toFloat())
                parent.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun reset() {
        path.reset()
        invalidate()
    }
}

private fun String.cleanOcrField(): String =
    lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun extractVintage(text: String): String? =
    Regex("\\b(19|20)\\d{2}\\b")
        .find(text)
        ?.value
