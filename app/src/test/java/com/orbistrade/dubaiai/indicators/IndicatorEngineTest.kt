package com.orbistrade.dubaiai.indicators

import com.orbistrade.dubaiai.core.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicatorEngineTest {
    @Test
    fun emaReturnsValueAfterWarmup() {
        val values = (1..20).map(Int::toDouble)
        val ema = IndicatorEngine.ema(values, 12)
        assertNotNull(ema)
        assertTrue(ema!! > 10.0)
    }

    @Test
    fun bollingerBandsAreOrdered() {
        val bands = IndicatorEngine.bollinger((1..20).map(Int::toDouble), 12, 1.5)
        assertNotNull(bands)
        assertTrue(bands!!.first > bands.second)
        assertTrue(bands.second > bands.third)
    }

    @Test
    fun atrUsesTrueRange() {
        val candles = (0..20).map { index ->
            Candle(index.toDouble(), index + 3.0, index - 1.0, index + 1.0, index)
        }
        val atr = IndicatorEngine.atr(candles, 14)
        assertEquals(4.0, atr!!, 0.0001)
    }

    @Test
    fun risingSeriesProducesUptrendWhenWarm() {
        val candles = (0..79).map { index ->
            val base = index.toDouble()
            Candle(base, base + 2.0, base - 1.0, base + 1.5, index)
        }
        val result = IndicatorEngine.calculate(candles)
        assertEquals("ALTA", result.trend)
        assertNotNull(result.ema60)
    }
}
