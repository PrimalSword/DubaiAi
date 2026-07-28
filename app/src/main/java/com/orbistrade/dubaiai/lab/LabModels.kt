package com.orbistrade.dubaiai.lab

enum class MarketMode { OPEN_MARKET, OTC }
enum class MarketRegime { STRONG_TREND, WEAK_TREND, LATERAL, COMPRESSION, EXPANSION, REVERSAL, ERRATIC, LOW_QUALITY }
enum class SignalOutcome { PENDING, WIN, LOSS, DRAW, INVALIDATED }

data class MarketContext(
    val mode: MarketMode = MarketMode.OTC,
    val asset: String = "NÃO IDENTIFICADO",
    val payoutPercent: Double? = null,
    val expirySeconds: Int? = null,
    val currentPrice: Double? = null,
    val screenValidated: Boolean = false,
    val visualConfidence: Double = 0.0
)

data class EconomicSnapshot(
    val breakEvenRate: Double? = null,
    val calibratedProbability: Double? = null,
    val expectedValue: Double? = null,
    val edgePercentagePoints: Double? = null,
    val sampleSize: Int = 0,
    val confidenceLabel: String = "AMOSTRA INSUFICIENTE"
)

data class RiskPolicy(
    val riskPerTradePercent: Double = 0.25,
    val dailyStopPercent: Double = 1.0,
    val maxTradesPerSession: Int = 5,
    val pauseAfterLosses: Int = 3,
    val maxDrawdownPercent: Double = 5.0,
    val martingaleAllowed: Boolean = false
)

data class LabSnapshot(
    val context: MarketContext = MarketContext(),
    val regime: MarketRegime = MarketRegime.LOW_QUALITY,
    val economics: EconomicSnapshot = EconomicSnapshot(),
    val riskPolicy: RiskPolicy = RiskPolicy(),
    val operationAllowed: Boolean = false,
    val blockers: List<String> = listOf("Tela não validada")
)
