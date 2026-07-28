package com.orbistrade.dubaiai.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppRuntimeState {
    private val _overlayRunning = MutableStateFlow(false)
    val overlayRunning = _overlayRunning.asStateFlow()

    private val _captureRunning = MutableStateFlow(false)
    val captureRunning = _captureRunning.asStateFlow()

    private val _capturedFrames = MutableStateFlow(0L)
    val capturedFrames = _capturedFrames.asStateFlow()

    fun setOverlayRunning(value: Boolean) {
        _overlayRunning.value = value
    }

    fun setCaptureRunning(value: Boolean) {
        _captureRunning.value = value
    }

    fun registerFrame() {
        _capturedFrames.value += 1
    }
}
