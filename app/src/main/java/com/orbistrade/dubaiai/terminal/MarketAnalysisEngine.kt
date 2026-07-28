package com.orbistrade.dubaiai.terminal

import com.orbistrade.dubaiai.core.Candle
import com.orbistrade.dubaiai.core.IndicatorSnapshot
import com.orbistrade.dubaiai.strategy.SignalDirection
import kotlin.math.abs
import kotlin.math.max

object MarketAnalysisEngine {
    fun structure(candles: List<Candle>, indicators: IndicatorSnapshot): MarketStructure {
        if (candles.size < 12) return MarketStructure(description = "Menos de 12 candles estáveis")
        val window = candles.takeLast(40)
        val highs = pivots(window, high = true)
        val lows = pivots(window, high = false)
        val higherHighs = highs.zipWithNext().count { it.second > it.first }
        val lowerHighs = highs.zipWithNext().count { it.second < it.first }
        val higherLows = lows.zipWithNext().count { it.second > it.first }
        val lowerLows = lows.zipWithNext().count { it.second < it.first }
        val bias = when {
            higherHighs >= 2 && higherLows >= 2 -> StructureBias.BULLISH
            lowerHighs >= 2 && lowerLows >= 2 -> StructureBias.BEARISH
            highs.size >= 2 && lows.size >= 2 -> StructureBias.RANGE
            else -> StructureBias.UNDEFINED
        }
        val last = window.last()
        val prior = window.dropLast(1).takeLast(20)
        val previousHigh = prior.maxOfOrNull(Candle::high)
        val previousLow = prior.minOfOrNull(Candle::low)
        val breakoutUp = previousHigh != null && last.close > previousHigh
        val breakoutDown = previousLow != null && last.close < previousLow
        val totalRange = (window.maxOf(Candle::high) - window.minOf(Candle::low)).coerceAtLeast(1e-9)
        val rangePosition = ((last.close - window.minOf(Candle::low)) / totalRange).coerceIn(0.0, 1.0)
        val body = abs(last.close - last.open).coerceAtLeast(1e-9)
        val upperWick = last.high - max(last.open, last.close)
        val lowerWick = minOf(last.open, last.close) - last.low
        val rejectionUp = upperWick >= body * 1.5
        val rejectionDown = lowerWick >= body * 1.5
        val atr = indicators.atr14
        val obstacle = when {
            atr == null || atr <= 0 -> null
            bias == StructureBias.BULLISH && previousHigh != null -> (previousHigh - last.close).coerceAtLeast(0.0) / atr
            bias == StructureBias.BEARISH && previousLow != null -> (last.close - previousLow).coerceAtLeast(0.0) / atr
            else -> minOf(last.high - last.close, last.close - last.low).coerceAtLeast(0.0) / atr
        }
        val description = when (bias) {
            StructureBias.BULLISH -> "Máximas e mínimas ascendentes"
            StructureBias.BEARISH -> "Máximas e mínimas descendentes"
            StructureBias.RANGE -> "Preço contido em faixa"
            StructureBias.UNDEFINED -> "Pivôs ainda inconsistentes"
        }
        return MarketStructure(
            bias = bias,
            higherHighs = higherHighs,
            higherLows = higherLows,
            lowerHighs = lowerHighs,
            lowerLows = lowerLows,
            breakoutUp = breakoutUp,
            breakoutDown = breakoutDown,
            rejectionUp = rejectionUp,
            rejectionDown = rejectionDown,
            rangePosition = rangePosition,
            spaceToObstacleAtr = obstacle,
            description = description
        )
    }

    fun regime(candles: List<Candle>, indicators: IndicatorSnapshot, structure: MarketStructure, visualConfidence: Double): TerminalRegime {
        if (visualConfidence < 0.55 || candles.size < 18) return TerminalRegime.LOW_QUALITY
        val atr = indicators.atr14 ?: return TerminalRegime.LOW_QUALITY
        val bbWidth = if (indicators.bollingerUpper != null && indicators.bollingerLower != null) {
            indicators.bollingerUpper - indicators.bollingerLower
        } else null
        val ranges = candles.takeLast(12).map { it.high - it.low }
        val recentRange = ranges.takeLast(4).average()
        val priorRange = ranges.dropLast(4).takeLast(8).average().coerceAtLeast(1e-9)
        val expansionRatio = recentRange / priorRange
        val emaGapAtr = if (indicators.ema12 != null && indicators.ema60 != null) abs(indicators.ema12 - indicators.ema60) / atr.coerceAtLeast(1e-9) else 0.0
        return when {
            structure.breakoutUp || structure.breakoutDown || expansionRatio >= 1.55 -> TerminalRegime.EXPANSION
            indicators.lateral && bbWidth != null && bbWidth / atr < 2.6 -> TerminalRegime.COMPRESSION
            indicators.lateral || structure.bias == StructureBias.RANGE -> TerminalRegime.LATERAL
            (structure.rejectionUp && structure.bias == StructureBias.BULLISH) ||
                (structure.rejectionDown && structure.bias == StructureBias.BEARISH) -> TerminalRegime.REVERSAL
            emaGapAtr >= 1.1 && structure.bias in setOf(StructureBias.BULLISH, StructureBias.BEARISH) -> TerminalRegime.STRONG_TREND
            emaGapAtr >= 0.35 && structure.bias in setOf(StructureBias.BULLISH, StructureBias.BEARISH) -> TerminalRegime.WEAK_TREND
            expansionRatio > 2.4 || ranges.maxOrNull()!! > atr * 3.2 -> TerminalRegime.ERRATIC
            else -> TerminalRegime.LOW_QUALITY
        }
    }

    fun playbook(regime: TerminalRegime, structure: MarketStructure): Playbook = when (regime) {
        TerminalRegime.STRONG_TREND, TerminalRegime.WEAK_TREND -> Playbook.TREND_PULLBACK
        TerminalRegime.EXPANSION -> Playbook.BREAKOUT_EXPANSION
        TerminalRegime.LATERAL, TerminalRegime.COMPRESSION -> Playbook.MEAN_REVERSION
        TerminalRegime.REVERSAL -> Playbook.EXTREME_REVERSAL
        else -> Playbook.NONE
    }

    fun candidateDirection(playbook: Playbook, structure: MarketStructure, indicators: IndicatorSnapshot): SignalDirection = when (playbook) {
        Playbook.TREND_PULLBACK -> when (structure.bias) {
            StructureBias.BULLISH -> SignalDirection.CALL
            StructureBias.BEARISH -> SignalDirection.PUT
            else -> SignalDirection.WAIT
        }
        Playbook.BREAKOUT_EXPANSION -> when {
            structure.breakoutUp -> SignalDirection.CALL
            structure.breakoutDown -> SignalDirection.PUT
            else -> SignalDirection.WAIT
        }
        Playbook.MEAN_REVERSION -> when {
            (structure.rangePosition ?: 0.5) <= 0.18 -> SignalDirection.CALL
            (structure.rangePosition ?: 0.5) >= 0.82 -> SignalDirection.PUT
            else -> SignalDirection.WAIT
        }
        Playbook.EXTREME_REVERSAL -> when {
            structure.rejectionDown -> SignalDirection.CALL
            structure.rejectionUp -> SignalDirection.PUT
            else -> SignalDirection.WAIT
        }
        Playbook.NONE -> SignalDirection.WAIT
    }

    fun entryQuality(
        playbook: Playbook,
        direction: SignalDirection,
        candles: List<Candle>,
        indicators: IndicatorSnapshot,
        structure: MarketStructure
    ): EntryQuality {
        if (candles.size < 15 || direction == SignalDirection.WAIT) return EntryQuality()
        val last = candles.last()
        val atr = indicators.atr14 ?: return EntryQuality()
        val ema = indicators.ema12 ?: return EntryQuality()
        val distance = abs(last.close - ema) / atr.coerceAtLeast(1e-9)
        val candleSize = (last.high - last.low) / atr.coerceAtLeast(1e-9)
        val extension = when (direction) {
            SignalDirection.CALL -> (last.close - candles.takeLast(8).minOf(Candle::low)) / atr.coerceAtLeast(1e-9)
            SignalDirection.PUT -> (candles.takeLast(8).maxOf(Candle::high) - last.close) / atr.coerceAtLeast(1e-9)
            SignalDirection.WAIT -> 0.0
        }
        var score = 50
        val positives = mutableListOf<String>()
        val negatives = mutableListOf<String>()
        if (distance <= 0.65) { score += 16; positives += "Preço próximo da EMA 12" }
        else if (distance >= 1.5) { score -= 24; negatives += "Preço esticado da EMA (${format(distance)} ATR)" }
        if (candleSize in 0.35..1.35) { score += 12; positives += "Candle de tamanho controlado" }
        else if (candleSize > 2.0) { score -= 22; negatives += "Candle excessivo (${format(candleSize)} ATR)" }
        if ((structure.spaceToObstacleAtr ?: 2.0) >= 0.8) { score += 12; positives += "Espaço até obstáculo" }
        else { score -= 18; negatives += "Obstáculo muito próximo" }
        when (playbook) {
            Playbook.TREND_PULLBACK -> if (distance <= 0.8) score += 10 else negatives += "Pullback ainda não ocorreu"
            Playbook.BREAKOUT_EXPANSION -> if (structure.breakoutUp || structure.breakoutDown) score += 12 else score -= 15
            Playbook.MEAN_REVERSION -> if ((structure.rangePosition ?: 0.5) !in 0.25..0.75) score += 12 else score -= 18
            Playbook.EXTREME_REVERSAL -> if (structure.rejectionUp || structure.rejectionDown) score += 14 else score -= 18
            Playbook.NONE -> score = 0
        }
        val late = extension > 2.6 || distance > 1.7
        if (late) { score -= 25; negatives += "Entrada atrasada após extensão" }
        if (extension <= 1.6) positives += "Movimento ainda não saturado"
        return EntryQuality(
            score = score.coerceIn(0, 100),
            extensionAtr = extension,
            candleSizeAtr = candleSize,
            distanceFromEmaAtr = distance,
            spaceToObstacleAtr = structure.spaceToObstacleAtr,
            lateEntry = late,
            positives = positives,
            negatives = negatives.ifEmpty { listOf("Nenhum veto de entrada") }
        )
    }

    fun stage(playbook: Playbook, direction: SignalDirection, quality: EntryQuality, structure: MarketStructure): SetupStage = when {
        playbook == Playbook.NONE -> SetupStage.CONTEXT
        direction == SignalDirection.WAIT -> SetupStage.FORMING
        quality.lateEntry -> SetupStage.MISSED
        quality.score >= 72 && (structure.breakoutUp || structure.breakoutDown || structure.rejectionUp || structure.rejectionDown || playbook == Playbook.TREND_PULLBACK) -> SetupStage.VALID
        quality.score >= 58 -> SetupStage.ARMED
        quality.score >= 38 -> SetupStage.FORMING
        else -> SetupStage.INVALIDATED
    }

    private fun pivots(candles: List<Candle>, high: Boolean): List<Double> {
        if (candles.size < 5) return emptyList()
        return (2 until candles.lastIndex - 1).mapNotNull { index ->
            val value = if (high) candles[index].high else candles[index].low
            val neighbours = candles.subList(index - 2, index + 3).map { if (high) it.high else it.low }
            if ((high && value == neighbours.maxOrNull()) || (!high && value == neighbours.minOrNull())) value else null
        }.takeLast(6)
    }

    private fun format(value: Double): String = "%.2f".format(java.util.Locale.US, value)
}
