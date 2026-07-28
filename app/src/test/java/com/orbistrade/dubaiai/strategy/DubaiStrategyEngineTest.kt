package com.orbistrade.dubaiai.strategy

import com.orbistrade.dubaiai.core.IndicatorSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DubaiStrategyEngineTest {
    @Test
    fun `returns call when trend and upper breakout align`() {
        val signal = DubaiStrategyEngine.evaluate(
            IndicatorSnapshot(
                lastClose = 110.0,
                ema12 = 105.0,
                ema60 = 100.0,
                bollingerUpper = 108.0,
                bollingerLower = 92.0,
                atr14 = 4.0,
                trend = "ALTA",
                volatility = "ALTA",
                lateral = false
            )
        )
        assertEquals(SignalDirection.CALL, signal.direction)
        assertTrue(signal.score >= 80)
    }

    @Test
    fun `returns put when trend and lower breakout align`() {
        val signal = DubaiStrategyEngine.evaluate(
            IndicatorSnapshot(
                lastClose = 88.0,
                ema12 = 94.0,
                ema60 = 100.0,
                bollingerUpper = 108.0,
                bollingerLower = 90.0,
                atr14 = 4.0,
                trend = "BAIXA",
                volatility = "ALTA",
                lateral = false
            )
        )
        assertEquals(SignalDirection.PUT, signal.direction)
        assertTrue(signal.score >= 80)
    }

    @Test
    fun `blocks entries in lateral market`() {
        val signal = DubaiStrategyEngine.evaluate(
            IndicatorSnapshot(
                lastClose = 110.0,
                ema12 = 105.0,
                ema60 = 100.0,
                bollingerUpper = 108.0,
                bollingerLower = 92.0,
                atr14 = 4.0,
                lateral = true
            )
        )
        assertEquals(SignalDirection.WAIT, signal.direction)
    }
}
