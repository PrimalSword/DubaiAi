package com.orbistrade.dubaiai.lab

import com.orbistrade.dubaiai.history.SignalHistoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomicEngineTest {
    @Test fun breakEvenForEightyEightPercentPayout() {
        assertEquals(0.5319, EconomicEngine.breakEvenRate(88.0)!!, 0.0002)
    }

    @Test fun positiveExpectedValueIsCalculated() {
        assertEquals(0.0528, EconomicEngine.expectedValue(0.56, 88.0)!!, 0.0002)
    }

    @Test fun modesNeverShareCalibrationSamples() {
        val history = listOf(
            item(1,"WIN",MarketMode.OTC), item(2,"LOSS",MarketMode.OTC),
            item(3,"WIN",MarketMode.OPEN_MARKET)
        )
        val otc = EconomicEngine.calibratedProbability(history,MarketMode.OTC)
        val open = EconomicEngine.calibratedProbability(history,MarketMode.OPEN_MARKET)
        assertEquals(2,otc.second); assertEquals(1,open.second)
        assertTrue(open.first!! > otc.first!!)
    }

    private fun item(id:Long,outcome:String,mode:MarketMode)=SignalHistoryItem(
        id=id,timestamp=id,direction="CALL",score=70,confidence="MÉDIA",reason="teste",asset="EUR/USD",
        outcome=outcome,marketMode=mode.name
    )
}
