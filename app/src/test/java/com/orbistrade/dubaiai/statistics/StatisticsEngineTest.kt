package com.orbistrade.dubaiai.statistics

import com.orbistrade.dubaiai.history.SignalHistoryItem
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsEngineTest {
    @Test
    fun calculatesWinRateOnlyFromMarkedSignals() {
        val items = listOf(
            SignalHistoryItem(1, 1, "CALL", 80, "ALTA", "r", "EUR/USD", "WIN"),
            SignalHistoryItem(2, 2, "PUT", 70, "MÉDIA", "r", "EUR/USD", "LOSS"),
            SignalHistoryItem(3, 3, "CALL", 60, "MÉDIA", "r", "EUR/USD", null)
        )
        val result = StatisticsEngine.calculate(items)
        assertEquals(3, result.totalSignals)
        assertEquals(1, result.wins)
        assertEquals(1, result.losses)
        assertEquals(1, result.pending)
        assertEquals(50.0, result.winRate, 0.001)
    }
}
