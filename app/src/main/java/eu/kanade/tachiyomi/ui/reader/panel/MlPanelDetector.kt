package eu.kanade.tachiyomi.ui.reader.panel

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.WorkerThread
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * Fallback for pages whose panels aren't separated by plain gutters.
 */
class MlPanelDetector(
    private val context: Context,
    private val preferences: ReaderPreferences,
) {

    private var session: OrtSession? = null
    private var sessionModel: PanelModel? = null
    private var isClosed = false

    val isAvailable: Boolean
        get() = preferences.panelModel.get().isDownloaded(context)

    @WorkerThread
    @Synchronized
    fun detect(page: Bitmap, isRtl: Boolean): List<Panel> {
        if (isClosed) {
            return emptyList()
        }
        val environment = OrtEnvironment.getEnvironment()
        val model = preferences.panelModel.get()
        if (model != sessionModel) {
            session?.close()
            session = null
        }
        val session = session ?: environment.createSession(model.getFile(context).path, createOptions()).also {
            session = it
            sessionModel = model
        }
        val inputName = session.inputNames.first()
        val shape = (session.inputInfo.getValue(inputName).info as TensorInfo).shape
        val height = shape[2].toInt()
        val width = shape[3].toInt()
        val candidates = OnnxTensor.createTensor(environment, page.toTensor(width, height), shape).use { input ->
            session.run(mapOf(inputName to input)).use { output ->
                var boxes: FloatBuffer? = null
                var logits: FloatBuffer? = null
                var classes = 0
                for (entry in output) {
                    val tensor = entry.value as? OnnxTensor ?: continue
                    val lastDimension = tensor.info.shape.last().toInt()
                    if (lastDimension == BOX_SIZE && boxes == null) {
                        boxes = tensor.floatBuffer
                    } else {
                        logits = tensor.floatBuffer
                        classes = lastDimension
                    }
                }
                parse(checkNotNull(boxes), checkNotNull(logits), classes, model.minScore, page.width, page.height)
            }
        }
        return PanelOrder.sort(suppressOverlaps(candidates), isRtl)
    }

    @Synchronized
    fun close() {
        isClosed = true
        session?.close()
        session = null
        sessionModel = null
    }

    private fun createOptions() = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS))
    }

    private fun Bitmap.toTensor(width: Int, height: Int): FloatBuffer {
        val scaled = Bitmap.createScaledBitmap(this, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== this) {
            scaled.recycle()
        }
        val area = width * height
        val buffer = ByteBuffer.allocateDirect(area * CHANNELS * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        for (i in 0 until area) {
            val pixel = pixels[i]
            buffer.put(i, (((pixel shr 16) and 0xFF) / 255f - MEAN_R) / STD_R)
            buffer.put(area + i, (((pixel shr 8) and 0xFF) / 255f - MEAN_G) / STD_G)
            buffer.put(area * 2 + i, ((pixel and 0xFF) / 255f - MEAN_B) / STD_B)
        }
        return buffer
    }

    /**
     * Boxes are relative (cx, cy, w, h) with a logit per class.
     */
    private fun parse(
        boxes: FloatBuffer,
        logits: FloatBuffer,
        classes: Int,
        minScore: Float,
        width: Int,
        height: Int,
    ): List<Candidate> {
        val result = ArrayList<Candidate>()
        for (i in 0 until boxes.remaining() / BOX_SIZE) {
            val score = sigmoid(logits.get(i * classes + PANEL_CLASS))
            if (score < minScore) {
                continue
            }
            val centerX = boxes.get(i * BOX_SIZE) * width
            val centerY = boxes.get(i * BOX_SIZE + 1) * height
            val halfWidth = boxes.get(i * BOX_SIZE + 2) * width / 2f
            val halfHeight = boxes.get(i * BOX_SIZE + 3) * height / 2f
            val panel = Panel(
                left = (centerX - halfWidth).toInt().coerceIn(0, width),
                top = (centerY - halfHeight).toInt().coerceIn(0, height),
                right = (centerX + halfWidth).toInt().coerceIn(0, width),
                bottom = (centerY + halfHeight).toInt().coerceIn(0, height),
            )
            if (panel.width > 0 && panel.height > 0) {
                result.add(Candidate(panel, score))
            }
        }
        return result
    }

    // The model may find the same panel several times
    private fun suppressOverlaps(candidates: List<Candidate>): List<Panel> {
        val result = ArrayList<Panel>()
        for (candidate in candidates.sortedByDescending { it.score }) {
            if (result.none { it.overlapWith(candidate.panel) > MAX_OVERLAP }) {
                result.add(candidate.panel)
            }
        }
        return result
    }

    private fun Panel.overlapWith(other: Panel): Float {
        val width = minOf(right, other.right) - maxOf(left, other.left)
        val height = minOf(bottom, other.bottom) - maxOf(top, other.top)
        if (width <= 0 || height <= 0) {
            return 0f
        }
        val intersection = width.toFloat() * height
        return intersection / (this.width.toFloat() * this.height + other.width.toFloat() * other.height - intersection)
    }

    private fun sigmoid(x: Float) = 1f / (1f + exp(-x))

    private class Candidate(
        val panel: Panel,
        val score: Float,
    )

    private companion object {

        const val MAX_THREADS = 4
        const val CHANNELS = 3
        const val BOX_SIZE = 4
        const val PANEL_CLASS = 1
        const val MAX_OVERLAP = 0.5f

        // ImageNet normalization the models were trained with
        const val MEAN_R = 0.485f
        const val MEAN_G = 0.456f
        const val MEAN_B = 0.406f
        const val STD_R = 0.229f
        const val STD_G = 0.224f
        const val STD_B = 0.225f
    }
}
