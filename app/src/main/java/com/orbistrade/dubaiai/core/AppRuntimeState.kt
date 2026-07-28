package com.orbistrade.dubaiai.core

import com.orbistrade.dubaiai.history.SignalHistoryItem
import com.orbistrade.dubaiai.lab.LabSnapshot
import com.orbistrade.dubaiai.lab.MarketMode
import com.orbistrade.dubaiai.strategy.StrategySignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Candle(val open: Double, val high: Double, val low: Double, val close: Double, val x: Int)

data class IndicatorSnapshot(
    val candleCount: Int = 0,
    val lastClose: Double? = null,
    val ema12: Double? = null,
    val ema60: Double? = null,
    val bollingerUpper: Double? = null,
    val bollingerMiddle: Double? = null,
    val bollingerLower: Double? = null,
    val atr14: Double? = null,
    val trend: String = "SEM DADOS",
    val volatility: String = "SEM DADOS",
    val lateral: Boolean = false
)

data class VisionSnapshot(
    val graphDetected: Boolean = false,
    val graphConfidence: Float = 0f,
    val candleCount: Int = 0,
    val ocrText: String = "",
    val processingMs: Long = 0L,
    val analyzedFrames: Long = 0L,
    val indicators: IndicatorSnapshot = IndicatorSnapshot(),
    val strategy: StrategySignal = StrategySignal(),
    val lab: LabSnapshot = LabSnapshot(),
    val error: String? = null
)

object AppRuntimeState {
    private val _overlayRunning = MutableStateFlow(false)
    val overlayRunning = _overlayRunning.asStateFlow()
    private val _captureRunning = MutableStateFlow(false)
    val captureRunning = _captureRunning.asStateFlow()
    private val _capturedFrames = MutableStateFlow(0L)
    val capturedFrames = _capturedFrames.asStateFlow()
    private val _vision = MutableStateFlow(VisionSnapshot())
    val vision = _vision.asStateFlow()
    private val _history = MutableStateFlow<List<SignalHistoryItem>>(emptyList())
    val history = _history.asStateFlow()
    private val _marketMode = MutableStateFlow(MarketMode.OTC)
    val marketMode = _marketMode.asStateFlow()

    fun setOverlayRunning(value: Boolean) { _overlayRunning.value = value }
    fun setCaptureRunning(value: Boolean) { _captureRunning.value = value }
    fun registerFrame() { _capturedFrames.value += 1 }
    fun updateVision(snapshot: VisionSnapshot) { _vision.value = snapshot }
    fun updateHistory(items: List<SignalHistoryItem>) { _history.value = items }
    fun setMarketMode(mode: MarketMode) { _marketMode.value = mode }
}
