package com.orbistrade.dubaiai.strategy

import com.orbistrade.dubaiai.core.IndicatorSnapshot
import kotlin.math.abs

enum class SignalDirection { CALL, PUT, WAIT }

data class StrategySignal(
    val direction: SignalDirection = SignalDirection.WAIT,
    val score: Int = 0,
    val confidence: String = "SEM SINAL",
    val reason: String = "Aguardando dados suficientes",
    val timestamp: Long = System.currentTimeMillis()
)

object DubaiStrategyEngine {
    fun evaluate(indicators: IndicatorSnapshot): StrategySignal {
        val ema12 = indicators.ema12 ?: return StrategySignal()
        val ema60 = indicators.ema60 ?: return StrategySignal()
        val upper = indicators.bollingerUpper ?: return StrategySignal()
        val lower = indicators.bollingerLower ?: return StrategySignal()
        val atr = indicators.atr14 ?: return StrategySignal()
        val close = indicators.lastClose ?: return StrategySignal()

        if (indicators.lateral) {
            return StrategySignal(
                direction = SignalDirection.WAIT,
                score = 15,
                confidence = "BAIXA",
                reason = "Mercado lateral: entrada bloqueada"
            )
        }

        val longTrend = close > ema60 && ema12 > ema60
        val shortTrend = close < ema60 && ema12 < ema60
        val upperBreakout = close > upper
        val lowerBreakout = close < lower
        val emaSeparation = abs(ema12 - ema60) / atr.coerceAtLeast(0.000001)

        var score = 0
        val reasons = mutableListOf<String>()
        val direction = when {
            longTrend && upperBreakout -> {
                score += 40
                reasons += "rompimento superior"
                SignalDirection.CALL
            }
            shortTrend && lowerBreakout -> {
                score += 40
                reasons += "rompimento inferior"
                SignalDirection.PUT
            }
            else -> SignalDirection.WAIT
        }

        if (direction == SignalDirection.WAIT) {
            return StrategySignal(
                direction = direction,
                score = 25,
                confidence = "BAIXA",
                reason = "Sem confluência entre EMA 60 e Bollinger"
            )
        }

        score += 25
        reasons += "EMA 60 confirma tendência"

        if (indicators.volatility == "ALTA") {
            score += 15
            reasons += "volatilidade favorável"
        } else if (indicators.volatility == "MÉDIA") {
            score += 10
        }

        when {
            emaSeparation >= 1.0 -> score += 20
            emaSeparation >= 0.5 -> score += 10
        }

        if (indicators.candleCount < 60) {
            score -= 15
            reasons += "EMA 60 ainda em maturação (${indicators.candleCount}/60)"
        }

        score = score.coerceIn(0, 100)
        val confidence = when {
            score >= 80 -> "ALTA"
            score >= 60 -> "MÉDIA"
            else -> "BAIXA"
        }

        return StrategySignal(direction, score, confidence, reasons.joinToString(" · "))
    }
}
