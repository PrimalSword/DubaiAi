package com.orbistrade.dubaiai.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.orbistrade.dubaiai.core.AppRuntimeState
import com.orbistrade.dubaiai.core.VisionSnapshot
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.math.max

class FrameAnalyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var analyzedFrames = 0L
    private var lastOcrText = ""
    private val openCvReady = OpenCVLoader.initLocal()

    fun analyze(bitmap: Bitmap) {
        val started = System.currentTimeMillis()
        analyzedFrames++

        if (!openCvReady) {
            AppRuntimeState.updateVision(
                VisionSnapshot(analyzedFrames = analyzedFrames, error = "OpenCV não inicializado")
            )
            return
        }

        val source = Mat()
        val gray = Mat()
        val edges = Mat()
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()

        try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(5.0, 5.0), 0.0)
            Imgproc.Canny(gray, edges, 60.0, 160.0)
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val screenArea = max(1.0, source.width().toDouble() * source.height())
            val rectangles = contours.map(Imgproc::boundingRect)
            val graph = rectangles
                .filter { it.width > source.width() * 0.35 && it.height > source.height() * 0.20 }
                .maxByOrNull { it.area() }

            val candleCount = graph?.let { graphRect ->
                rectangles.count { candidate -> isCandle(candidate, graphRect) }
            } ?: 0

            val confidence = graph?.let { (it.area() / screenArea).coerceIn(0.0, 1.0).toFloat() } ?: 0f
            AppRuntimeState.updateVision(
                VisionSnapshot(
                    graphDetected = graph != null,
                    graphConfidence = confidence,
                    candleCount = candleCount,
                    ocrText = lastOcrText,
                    processingMs = System.currentTimeMillis() - started,
                    analyzedFrames = analyzedFrames
                )
            )

            if (analyzedFrames % OCR_INTERVAL == 0L) {
                runOcr(bitmap)
            }
        } catch (error: Throwable) {
            AppRuntimeState.updateVision(
                VisionSnapshot(
                    ocrText = lastOcrText,
                    processingMs = System.currentTimeMillis() - started,
                    analyzedFrames = analyzedFrames,
                    error = error.message ?: error.javaClass.simpleName
                )
            )
        } finally {
            contours.forEach(MatOfPoint::release)
            hierarchy.release()
            edges.release()
            gray.release()
            source.release()
        }
    }

    fun close() {
        recognizer.close()
    }

    private fun runOcr(bitmap: Bitmap) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                lastOcrText = result.text.lineSequence()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .take(5)
                    .joinToString(" | ")
                AppRuntimeState.updateVision(AppRuntimeState.vision.value.copy(ocrText = lastOcrText))
            }
            .addOnFailureListener { error ->
                AppRuntimeState.updateVision(AppRuntimeState.vision.value.copy(error = "OCR: ${error.message}"))
            }
    }

    private fun isCandle(candidate: Rect, graph: Rect): Boolean {
        val insideGraph = candidate.x >= graph.x && candidate.y >= graph.y &&
            candidate.x + candidate.width <= graph.x + graph.width &&
            candidate.y + candidate.height <= graph.y + graph.height
        val narrow = candidate.width in 2..max(3, graph.width / 18)
        val vertical = candidate.height >= candidate.width * 2
        return insideGraph && narrow && vertical
    }

    companion object {
        private const val OCR_INTERVAL = 15L
    }
}