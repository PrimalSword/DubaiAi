package com.orbistrade.dubaiai.lab

import com.orbistrade.dubaiai.core.IndicatorSnapshot
import com.orbistrade.dubaiai.history.SignalHistoryItem
import kotlin.math.abs

object MarketLabEngine {
    fun inferMode(ocr: String, selected: MarketMode): MarketMode = when {
        ocr.contains("OTC", ignoreCase = true) -> MarketMode.OTC
        else -> selected
    }

    fun extractAsset(ocr: String): String = ASSET.find(ocr)?.value
        ?.replace(Regex("\\s+"), " ")?.trim().orEmpty().ifBlank { "NÃO IDENTIFICADO" }

    fun extractPayout(ocr: String): Double? = PAYOUT.findAll(ocr).mapNotNull {
        it.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
    }.filter { it in 1.0..100.0 }.maxOrNull()

    fun extractExpirySeconds(ocr: String): Int? {
        val match = TIME.find(ocr) ?: return null
        val parts = match.value.split(':').mapNotNull(String::toIntOrNull)
        return when (parts.size) { 2 -> parts[0] * 60 + parts[1]; 3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]; else -> null }
    }

    fun regime(indicators: IndicatorSnapshot, visualConfidence: Double): MarketRegime {
        if (visualConfidence < 0.45 || indicators.candleCount < 12) return MarketRegime.LOW_QUALITY
        if (indicators.lateral) return MarketRegime.LATERAL
        val atr = indicators.atr14 ?: return MarketRegime.LOW_QUALITY
        val close = indicators.lastClose ?: return MarketRegime.LOW_QUALITY
        val ratio = if (close == 0.0) 0.0 else abs(atr / close)
        return when {
            indicators.volatility == "ALTA" && indicators.trend in setOf("ALTA", "BAIXA") -> MarketRegime.EXPANSION
            indicators.trend in setOf("ALTA", "BAIXA") && ratio > 0.02 -> MarketRegime.STRONG_TREND
            indicators.trend in setOf("ALTA", "BAIXA") -> MarketRegime.WEAK_TREND
            indicators.volatility == "BAIXA" -> MarketRegime.COMPRESSION
            indicators.trend == "NEUTRA" && indicators.volatility == "ALTA" -> MarketRegime.ERRATIC
            else -> MarketRegime.REVERSAL
        }
    }

    fun evaluate(
        context: MarketContext,
        indicators: IndicatorSnapshot,
        history: List<SignalHistoryItem>,
        dailyTrades: Int,
        consecutiveLosses: Int,
        drawdownPercent: Double,
        policy: RiskPolicy = RiskPolicy()
    ): LabSnapshot {
        val regime = regime(indicators, context.visualConfidence)
        val economics = EconomicEngine.snapshot(history, context)
        val blockers = buildList {
            if (!context.screenValidated) add("Tela da corretora não validada")
            if (context.asset == "NÃO IDENTIFICADO") add("Ativo não identificado")
            if (context.payoutPercent == null) add("Payout não identificado")
            if (context.expirySeconds == null) add("Vencimento não identificado")
            if (regime in setOf(MarketRegime.LATERAL, MarketRegime.ERRATIC, MarketRegime.LOW_QUALITY)) add("Regime desfavorável: $regime")
            if (dailyTrades >= policy.maxTradesPerSession) add("Limite de operações da sessão atingido")
            if (consecutiveLosses >= policy.pauseAfterLosses) add("Pausa por sequência de perdas")
            if (drawdownPercent >= policy.maxDrawdownPercent) add("Limite de drawdown atingido")
            if (economics.sampleSize < 30) add("Amostra estatística insuficiente")
            if (economics.expectedValue != null && economics.expectedValue <= 0.0) add("Valor esperado não positivo")
        }
        return LabSnapshot(context, regime, economics, policy, blockers.isEmpty(), blockers)
    }

    private val ASSET = Regex("[A-Z]{3}\\s*/\\s*[A-Z]{3}(?:\\s*\\(OTC\\))?", RegexOption.IGNORE_CASE)
    private val PAYOUT = Regex("(\\d{1,3}(?:[.,]\\d+)?)\\s*%")
    private val TIME = Regex("\\b(?:\\d{1,2}:)?\\d{1,2}:\\d{2}\\b")
}
