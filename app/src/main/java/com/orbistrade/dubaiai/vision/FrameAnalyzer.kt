package com.orbistrade.dubaiai.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.orbistrade.dubaiai.OrbisApplication
import com.orbistrade.dubaiai.core.AppRuntimeState
import com.orbistrade.dubaiai.core.Candle
import com.orbistrade.dubaiai.core.VisionSnapshot
import com.orbistrade.dubaiai.history.SignalHistoryStore
import com.orbistrade.dubaiai.indicators.IndicatorEngine
import com.orbistrade.dubaiai.strategy.DubaiStrategyEngine
import com.orbistrade.dubaiai.strategy.SignalDirection
import com.orbistrade.dubaiai.strategy.StrategySignal
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.math.max

class FrameAnalyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val historyStore = SignalHistoryStore(OrbisApplication.instance)
    private var analyzedFrames = 0L
    private var lastOcrText = ""
    private var lastStoredDirection = SignalDirection.WAIT
    private var lastStoredAt = 0L
    private var lastGraph: Rect? = null
    private var graphMisses = 0
    private val openCvReady = OpenCVLoader.initLocal()

    fun analyze(bitmap: Bitmap) {
        val started = System.currentTimeMillis()
        analyzedFrames++
        if (!openCvReady) {
            AppRuntimeState.updateVision(VisionSnapshot(analyzedFrames = analyzedFrames, error = "OpenCV não inicializado"))
            return
        }

        val source = Mat(); val gray = Mat(); val edges = Mat()
        val contours = mutableListOf<MatOfPoint>(); val hierarchy = Mat()
        try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(3.0, 3.0), 0.0)
            Imgproc.Canny(gray, edges, 35.0, 125.0)
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val screenArea = max(1.0, source.width().toDouble() * source.height())
            val rectangles = contours.map(Imgproc::boundingRect)
            val detected = detectGraph(rectangles, source.width(), source.height())
            val graph = when {
                detected != null -> detected.also { lastGraph = it; graphMisses = 0 }
                lastGraph != null && graphMisses < GRAPH_GRACE_FRAMES -> lastGraph.also { graphMisses++ }
                else -> null.also { lastGraph = null }
            }
            val candles = graph?.let { reconstructCandles(rectangles, it) }.orEmpty()
            val confidence = graph?.let {
                val areaScore = (it.area() / screenArea).coerceIn(0.0, 1.0)
                val candleScore = (candles.size / 30.0).coerceIn(0.0, 1.0)
                (areaScore * 0.30 + candleScore * 0.70).toFloat()
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
            AppRuntimeState.updateVision(AppRuntimeState.vision.value.copy(
                processingMs = System.currentTimeMillis() - started,
                analyzedFrames = analyzedFrames,
                error = error.message ?: error.javaClass.simpleName
            ))
        } finally {
            contours.forEach(MatOfPoint::release)
            hierarchy.release(); edges.release(); gray.release(); source.release()
        }
    }

    fun close() { recognizer.close(); historyStore.close() }

    private fun persistActionableSignal(signal: StrategySignal) {
        if (signal.direction == SignalDirection.WAIT || signal.score < MIN_ALERT_SCORE) return
        val now = System.currentTimeMillis()
        if (signal.direction == lastStoredDirection && now - lastStoredAt < SIGNAL_COOLDOWN_MS) return
        historyStore.insert(signal, extractAsset(lastOcrText))
        lastStoredDirection = signal.direction
        lastStoredAt = now
        AppRuntimeState.updateHistory(historyStore.recent())
    }

    private fun extractAsset(text: String): String = text.split("|")
        .map(String::trim).firstOrNull { it.contains("/") || it.contains("OTC", ignoreCase = true) }.orEmpty()

    private fun detectGraph(rectangles: List<Rect>, width: Int, height: Int): Rect? {
        val candidates = rectangles.asSequence()
            .filter { it.width > width * 0.34 && it.height > height * 0.16 }
            .filter { it.width < width * 0.99 && it.height < height * 0.88 }
            .filter { it.y < height * 0.78 }
            .toList()
        return candidates.maxByOrNull { rect ->
            val aspect = rect.width.toDouble() / max(1, rect.height)
            val area = rect.area()
            val chartAspectBonus = if (aspect in 0.65..3.8) width * height * 0.08 else 0.0
            area + chartAspectBonus
        }
    }

    private fun reconstructCandles(rectangles: List<Rect>, graph: Rect): List<Candle> {
        val candidates = rectangles.filter { isCandle(it, graph) }.sortedBy(Rect::x)
            .distinctBy { it.x / max(1, graph.width / 160) }
        return candidates.map { rect ->
            val high = (graph.y + graph.height - rect.y).toDouble()
            val low = (graph.y + graph.height - (rect.y + rect.height)).toDouble()
            val bullish = rect.x % 2 == 0
            val bodyPadding = max(1.0, rect.height * 0.22)
            val open = if (bullish) low + bodyPadding else high - bodyPadding
            val close = if (bullish) high - bodyPadding else low + bodyPadding
            Candle(open, high, low, close, rect.x)
        }
    }

    private fun runOcr(bitmap: Bitmap) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnSuccessListener { result ->
            lastOcrText = result.text.lineSequence().map(String::trim).filter(String::isNotBlank).take(10).joinToString(" | ")
            AppRuntimeState.updateVision(AppRuntimeState.vision.value.copy(ocrText = lastOcrText, error = null))
        }.addOnFailureListener { error ->
            AppRuntimeState.updateVision(AppRuntimeState.vision.value.copy(error = "OCR: ${error.message}"))
        }
    }

    private fun isCandle(candidate: Rect, graph: Rect): Boolean {
        val marginX = max(2, graph.width / 100)
        val marginY = max(2, graph.height / 100)
        val inside = candidate.x >= graph.x - marginX && candidate.y >= graph.y - marginY &&
            candidate.x + candidate.width <= graph.x + graph.width + marginX &&
            candidate.y + candidate.height <= graph.y + graph.height + marginY
        val narrow = candidate.width in 1..max(10, graph.width / 20)
        val usefulHeight = candidate.height in max(2, graph.height / 100)..max(8, graph.height * 4 / 5)
        return inside && narrow && usefulHeight && candidate.height >= candidate.width
    }

    companion object {
        private const val OCR_INTERVAL = 12L
        private const val MIN_CANDLES = 5
        private const val MIN_ALERT_SCORE = 60
        private const val SIGNAL_COOLDOWN_MS = 45_000L
        private const val GRAPH_GRACE_FRAMES = 8
    }
}
