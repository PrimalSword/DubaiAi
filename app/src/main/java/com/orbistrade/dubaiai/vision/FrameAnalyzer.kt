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
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

class FrameAnalyzer {
    private data class ColoredRect(val rect: Rect, val bullish: Boolean)

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

        val source = Mat()
        val rgb = Mat()
        val hsv = Mat()
        val gray = Mat()
        val edges = Mat()
        val greenMask = Mat()
        val redLowMask = Mat()
        val redHighMask = Mat()
        val redMask = Mat()
        val edgeContours = mutableListOf<MatOfPoint>()
        val greenContours = mutableListOf<MatOfPoint>()
        val redContours = mutableListOf<MatOfPoint>()
        val edgeHierarchy = Mat()
        val greenHierarchy = Mat()
        val redHierarchy = Mat()

        try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.cvtColor(source, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            Imgproc.cvtColor(source, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
            Imgproc.Canny(gray, edges, 35.0, 125.0)
            Imgproc.findContours(edges, edgeContours, edgeHierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            // Quotex uses saturated green/red candle bodies. Text and ordinary UI strokes
            // usually fail this chromatic gate, preventing non-chart screens from becoming candles.
            Core.inRange(hsv, Scalar(32.0, 70.0, 55.0), Scalar(100.0, 255.0, 255.0), greenMask)
            Core.inRange(hsv, Scalar(0.0, 75.0, 55.0), Scalar(14.0, 255.0, 255.0), redLowMask)
            Core.inRange(hsv, Scalar(165.0, 75.0, 55.0), Scalar(180.0, 255.0, 255.0), redHighMask)
            Core.bitwise_or(redLowMask, redHighMask, redMask)

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 3.0))
            Imgproc.morphologyEx(greenMask, greenMask, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.morphologyEx(redMask, redMask, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            Imgproc.findContours(greenMask, greenContours, greenHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            Imgproc.findContours(redMask, redContours, redHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            val screenArea = max(1.0, source.width().toDouble() * source.height())
            val edgeRectangles = edgeContours.map(Imgproc::boundingRect)
            val coloredRectangles = buildList {
                greenContours.mapTo(this) { ColoredRect(Imgproc.boundingRect(it), bullish = true) }
                redContours.mapTo(this) { ColoredRect(Imgproc.boundingRect(it), bullish = false) }
            }

            val detected = detectGraph(edgeRectangles, coloredRectangles, source.width(), source.height())
            val graph = when {
                detected != null -> detected.also { lastGraph = it; graphMisses = 0 }
                lastGraph != null && graphMisses < GRAPH_GRACE_FRAMES &&
                    hasCurrentCandleEvidence(coloredRectangles, lastGraph!!) -> lastGraph.also { graphMisses++ }
                else -> null.also { lastGraph = null; graphMisses = 0 }
            }

            val candles = graph?.let { reconstructCandles(coloredRectangles, it) }.orEmpty()
            val graphIsCurrent = graph != null && hasCurrentCandleEvidence(coloredRectangles, graph)
            val confidence = if (graphIsCurrent) {
                val areaScore = (graph!!.area() / screenArea).coerceIn(0.0, 1.0)
                val candleScore = (candles.size / 30.0).coerceIn(0.0, 1.0)
                (areaScore * 0.25 + candleScore * 0.75).toFloat()
            } else 0f

            val indicators = IndicatorEngine.calculate(candles)
            val strategy = DubaiStrategyEngine.evaluate(indicators)

            AppRuntimeState.updateVision(
                VisionSnapshot(
                    graphDetected = graphIsCurrent && candles.size >= MIN_CANDLES,
                    graphConfidence = confidence,
                    candleCount = candles.size,
                    ocrText = lastOcrText,
                    processingMs = System.currentTimeMillis() - started,
                    analyzedFrames = analyzedFrames,
                    indicators = indicators,
                    strategy = strategy
                )
            )
            if (graphIsCurrent) persistActionableSignal(strategy)
            if (analyzedFrames % OCR_INTERVAL == 0L) runOcr(bitmap)
        } catch (error: Throwable) {
            AppRuntimeState.updateVision(
                AppRuntimeState.vision.value.copy(
                    graphDetected = false,
                    graphConfidence = 0f,
                    candleCount = 0,
                    processingMs = System.currentTimeMillis() - started,
                    analyzedFrames = analyzedFrames,
                    error = error.message ?: error.javaClass.simpleName
                )
            )
        } finally {
            edgeContours.forEach(MatOfPoint::release)
            greenContours.forEach(MatOfPoint::release)
            redContours.forEach(MatOfPoint::release)
            edgeHierarchy.release(); greenHierarchy.release(); redHierarchy.release()
            redMask.release(); redHighMask.release(); redLowMask.release(); greenMask.release()
            edges.release(); gray.release(); hsv.release(); rgb.release(); source.release()
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

    private fun detectGraph(
        edgeRectangles: List<Rect>,
        coloredRectangles: List<ColoredRect>,
        width: Int,
        height: Int
    ): Rect? = edgeRectangles.asSequence()
        .filter { it.width > width * 0.34 && it.height > height * 0.16 }
        .filter { it.width < width * 0.99 && it.height < height * 0.88 }
        .filter { it.y < height * 0.78 }
        .filter { hasCurrentCandleEvidence(coloredRectangles, it) }
        .maxByOrNull { rect ->
            val evidence = coloredRectangles.count { isCandle(it.rect, rect) }
            rect.area() + evidence * width * height * 0.003
        }

    private fun hasCurrentCandleEvidence(coloredRectangles: List<ColoredRect>, graph: Rect): Boolean {
        val candidates = coloredRectangles.filter { isCandle(it.rect, graph) }
        if (candidates.size < MIN_COLOR_CANDLES) return false
        val minX = candidates.minOf { it.rect.x }
        val maxX = candidates.maxOf { it.rect.x + it.rect.width }
        return maxX - minX >= graph.width * MIN_HORIZONTAL_COVERAGE
    }

    private fun reconstructCandles(rectangles: List<ColoredRect>, graph: Rect): List<Candle> {
        val candidates = rectangles.filter { isCandle(it.rect, graph) }
            .sortedBy { it.rect.x }
            .distinctBy { it.rect.x / max(1, graph.width / 160) }

        return candidates.map { colored ->
            val rect = colored.rect
            val high = (graph.y + graph.height - rect.y).toDouble()
            val low = (graph.y + graph.height - (rect.y + rect.height)).toDouble()
            val bodyPadding = max(1.0, rect.height * 0.18)
            val open = if (colored.bullish) low + bodyPadding else high - bodyPadding
            val close = if (colored.bullish) high - bodyPadding else low + bodyPadding
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
        val narrow = candidate.width in 1..max(12, graph.width / 18)
        val usefulHeight = candidate.height in max(3, graph.height / 120)..max(8, graph.height * 4 / 5)
        val bodyShape = candidate.height >= max(2, candidate.width / 2)
        return inside && narrow && usefulHeight && bodyShape
    }

    companion object {
        private const val OCR_INTERVAL = 12L
        private const val MIN_CANDLES = 5
        private const val MIN_COLOR_CANDLES = 7
        private const val MIN_HORIZONTAL_COVERAGE = 0.22
        private const val MIN_ALERT_SCORE = 60
        private const val SIGNAL_COOLDOWN_MS = 45_000L
        private const val GRAPH_GRACE_FRAMES = 3
    }
}
