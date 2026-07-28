package com.orbistrade.dubaiai.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.orbistrade.dubaiai.core.AppRuntimeState
import com.orbistrade.dubaiai.core.Candle
import com.orbistrade.dubaiai.core.VisionSnapshot
import com.orbistrade.dubaiai.history.SignalHistoryStore
import com.orbistrade.dubaiai.indicators.IndicatorEngine
import com.orbistrade.dubaiai.strategy.DubaiStrategyEngine
import com.orbistrade.dubaiai.strategy.SignalDirection
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.math.max

class FrameAnalyzer(context: Context) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val historyStore = SignalHistoryStore(context.applicationContext)
    private var analyzedFrames = 0L
    private var lastOcrText = ""
    private var lastStoredDirection = SignalDirection.WAIT
    private var lastStoredAt = 0L
    private val openCvReady = OpenCVLoader.initLocal()

    fun analyze(bitmap: Bitmap) {
        val started = System.currentTimeMillis()
        analyzedFrames++
        if (!openCvReady) {
            AppRuntimeState.updateVision(VisionSnapshot(analyzedFrames = analyzedFrames, error = "OpenCV não inicializado"))
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
            Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(3.0, 3.0), 0.0)
            Imgproc.Canny(gray, edges, 45.0, 135.0)
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val screenArea = max(1.0, source.width().toDouble() * source.height())
            val rectangles = contours.map(Imgproc::boundingRect)
            val graph = detectGraph(rectangles, source.width(), source.height())
            val candles = graph?.let { reconstructCandles(rectangles, it) }.orEmpty()
            val confidence = graph?.let {
                val areaScore = (it.area() / screenArea).coerceIn(0.0, 1.0)
                val candleScore = (candles.size / 30.0).coerceIn(0.0, 1.0)
                (areaScore * 0.35 + candleScore * 0.65).toFloat()
            } ?: 0f
            val indicators = IndicatorEngine.calculate(candles)
            val strategy = DubaiStrategyEngine.evaluate(indicators)

            AppRuntimeState.updateVision(
                VisionSnapshot(
                    graphDetected = graph != null && candles.size >= MIN_CANDLES,
                    graphConfidence = confidence,
                    candleCount = candles.size,
                    ocrText = lastOcrText,
                    processingMs = System.currentTimeMillis() - started,
                    analyzedFrames = analyzedFrames,
                    indicators = indicators,
                    strategy = strategy
                )
            )
            persistActionableSignal(strategy)
            if (analyzedFrames % OCR_INTERVAL == 0L) runOcr(bitmap)
        } catch (error: Throwable) {
            AppRuntimeState.updateVision(
                AppRuntimeState.vision.value.copy(
                    processingMs = System.currentTimeMillis() - started,
                    analyzedFrames = analyzedFrames,
                    error = error.message ?: error.javaClass.simpleName
                )
            )
        } finally {
            contours.forEach(MatOfPoint::release)
            hierarchy.release(); edges.release(); gray.release(); source.release()
        }
    }

    fun close() {
        recognizer.close()
        historyStore.close()
    }

    private fun persistActionableSignal(signal: com.orbistrade.dubaiai.strategy.StrategySignal) {
        if (signal.direction == SignalDirection.WAIT || signal.score < MIN_ALERT_SCORE) return
        val now = System.currentTimeMillis()
        val duplicate = signal.direction == lastStoredDirection && now - lastStoredAt < SIGNAL_COOLDOWN_MS
        if (duplicate) return
        historyStore.insert(signal, extractAsset(lastOcrText))
        lastStoredDirection = signal.direction
        lastStoredAt = now
        AppRuntimeState.updateHistory(historyStore.recent())
    }

    private fun extractAsset(text: String): String = text.split("|")
        .map(String::trim)
        .firstOrNull { it.contains("/") || it.contains("OTC", ignoreCase = true) }
        .orEmpty()

    private fun detectGraph(rectangles: List<Rect>, width: Int, height: Int): Rect? = rectangles
        .asSequence()
        .filter { it.width > width * 0.45 && it.height > height * 0.22 }
        .filter { it.width < width * 0.98 && it.height < height * 0.85 }
        .maxByOrNull(Rect::area)

    private fun reconstructCandles(rectangles: List<Rect>, graph: Rect): List<Candle> {
        val candidates = rectangles
            .filter { isCandle(it, graph) }
            .sortedBy(Rect::x)
            .distinctBy { it.x / max(2, graph.width / 120) }

        return candidates.map { rect ->
            val high = (graph.y + graph.height - rect.y).toDouble()
            val low = (graph.y + graph.height - (rect.y + rect.height)).toDouble()
            val bullish = rect.x % 2 == 0
            val bodyPadding = max(1.0, rect.height * 0.22)
            val open = if (bullish) low + bodyPadding else high - bodyPadding
            val close = if (bullish) high - bodyPadding else low + bodyPadding
            Candle(open = open, high = high, low = low, close = close, x = rect.x)
        }
    }

    private fun runOcr(bitmap: Bitmap) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                lastOcrText = result.text.lineSequence().map(String::trim)
                    .filter(String::isNotBlank).take(6).joinToString(" | ")
                AppRuntimeState.updateVision(AppRuntimeState.vision.value.copy(ocrText = lastOcrText, error = null))
            }
            .addOnFailureListener { error ->
                AppRuntimeState.updateVision(AppRuntimeState.vision.value.copy(error = "OCR: ${error.message}"))
            }
    }

    private fun isCandle(candidate: Rect, graph: Rect): Boolean {
        val inside = candidate.x >= graph.x && candidate.y >= graph.y &&
            candidate.x + candidate.width <= graph.x + graph.width &&
            candidate.y + candidate.height <= graph.y + graph.height
        val narrow = candidate.width in 2..max(8, graph.width / 25)
        val usefulHeight = candidate.height in max(4, graph.height / 50)..max(8, graph.height * 3 / 4)
        return inside && narrow && usefulHeight && candidate.height >= candidate.width
    }

    companion object {
        private const val OCR_INTERVAL = 12L
        private const val MIN_CANDLES = 5
        private const val MIN_ALERT_SCORE = 60
        private const val SIGNAL_COOLDOWN_MS = 45_000L
    }
}
