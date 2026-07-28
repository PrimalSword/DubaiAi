package com.orbistrade.dubaiai.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VisionSnapshot(
    val graphDetected: Boolean = false,
    val graphConfidence: Float = 0f,
    val candleCount: Int = 0,
    val ocrText: String = "",
    val processingMs: Long = 0L,
    val analyzedFrames: Long = 0L,
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

    fun setOverlayRunning(value: Boolean) { _overlayRunning.value = value }
    fun setCaptureRunning(value: Boolean) { _captureRunning.value = value }
    fun registerFrame() { _capturedFrames.value += 1 }
    fun updateVision(snapshot: VisionSnapshot) { _vision.value = snapshot }
}