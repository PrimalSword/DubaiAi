package com.orbistrade.dubaiai.terminal

import com.orbistrade.dubaiai.core.Candle
import com.orbistrade.dubaiai.core.IndicatorSnapshot
import com.orbistrade.dubaiai.lab.MarketMode
import com.orbistrade.dubaiai.strategy.SignalDirection

enum class CaptureTimeframe(val label: String, val staleAfterMs: Long) {
    CONTEXT_15M("Contexto 15m", 45 * 60_000L),
    STRUCTURE_5M("Estrutura 5m", 20 * 60_000L),
    ENTRY_1M("Entrada 1m", 5 * 60_000L)
}

enum class OperatingMode { OBSERVER, ASSISTED, BLIND }
enum class Playbook { NONE, TREND_PULLBACK, BREAKOUT_EXPANSION, MEAN_REVERSION, EXTREME_REVERSAL }
enum class SetupStage { CONTEXT, FORMING, ARMED, VALID, MISSED, INVALIDATED }
enum class TerminalRegime { STRONG_TREND, WEAK_TREND, LATERAL, COMPRESSION, EXPANSION, REVERSAL, ERRATIC, LOW_QUALITY }
enum class StructureBias { BULLISH, BEARISH, RANGE, UNDEFINED }
enum class JournalOutcome { PENDING, WIN, LOSS, DRAW, INVALIDATED }
enum class ExecutionChoice { NONE, HUMAN_CALL, HUMAN_PUT, SYSTEM_FOLLOWED, SYSTEM_IGNORED }

data class TerminalSettings(
    val marketMode: MarketMode = MarketMode.OTC,
    val captureTimeframe: CaptureTimeframe = CaptureTimeframe.ENTRY_1M,
    val operatingMode: OperatingMode = OperatingMode.ASSISTED,
    val virtualBankroll: Double = 10_000.0,
    val riskPerTradePercent: Double = 0.25,
    val dailyStopPercent: Double = 1.0,
    val dailyTargetPercent: Double = 1.5,
    val maxTradesPerSession: Int = 6,
    val pauseAfterLosses: Int = 3,
    val minimumPayoutPercent: Double = 78.0,
    val minimumSample: Int = 30,
    val minimumEntryQuality: Int = 62,
    val minimumEdgePoints: Double = 1.5
)

data class SessionState(
    val startedAt: Long = System.currentTimeMillis(),
    val trades: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val consecutiveLosses: Int = 0,
    val pnlUnits: Double = 0.0,
    val peakPnlUnits: Double = 0.0,
    val drawdownUnits: Double = 0.0,
    val locked: Boolean = false,
    val lockReason: String? = null
)

data class MarketStructure(
    val bias: StructureBias = StructureBias.UNDEFINED,
    val higherHighs: Int = 0,
    val higherLows: Int = 0,
    val lowerHighs: Int = 0,
    val lowerLows: Int = 0,
    val breakoutUp: Boolean = false,
    val breakoutDown: Boolean = false,
    val rejectionUp: Boolean = false,
    val rejectionDown: Boolean = false,
    val rangePosition: Double? = null,
    val spaceToObstacleAtr: Double? = null,
    val description: String = "Estrutura ainda não definida"
)

data class EntryQuality(
    val score: Int = 0,
    val extensionAtr: Double? = null,
    val candleSizeAtr: Double? = null,
    val distanceFromEmaAtr: Double? = null,
    val spaceToObstacleAtr: Double? = null,
    val lateEntry: Boolean = false,
    val positives: List<String> = emptyList(),
    val negatives: List<String> = listOf("Dados insuficientes")
)

data class ProbabilitySnapshot(
    val sampleSize: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val posteriorProbability: Double? = null,
    val lowerBound: Double? = null,
    val breakEven: Double? = null,
    val expectedValue: Double? = null,
    val edgePoints: Double? = null,
    val label: String = "SEM AMOSTRA"
)

data class TimeframeSnapshot(
    val timeframe: CaptureTimeframe,
    val timestamp: Long,
    val candles: List<Candle>,
    val indicators: IndicatorSnapshot,
    val structure: MarketStructure,
    val regime: TerminalRegime,
    val visualConfidence: Double
) {
    fun isFresh(now: Long = System.currentTimeMillis()): Boolean = now - timestamp <= timeframe.staleAfterMs
}

data class TerminalDecision(
    val timestamp: Long = System.currentTimeMillis(),
    val screenValidated: Boolean = false,
    val asset: String = "NÃO IDENTIFICADO",
    val payoutPercent: Double? = null,
    val expirySeconds: Int? = null,
    val regime: TerminalRegime = TerminalRegime.LOW_QUALITY,
    val structure: MarketStructure = MarketStructure(),
    val playbook: Playbook = Playbook.NONE,
    val stage: SetupStage = SetupStage.CONTEXT,
    val direction: SignalDirection = SignalDirection.WAIT,
    val confluenceScore: Int = 0,
    val entryQuality: EntryQuality = EntryQuality(),
    val probability: ProbabilitySnapshot = ProbabilitySnapshot(),
    val operationAllowed: Boolean = false,
    val blockers: List<String> = listOf("Aguardando gráfico válido"),
    val reasons: List<String> = emptyList(),
    val nextCondition: String = "Validar a tela e formar contexto",
    val avoidedLossCandidate: Boolean = false
)

data class TerminalSnapshot(
    val settings: TerminalSettings = TerminalSettings(),
    val session: SessionState = SessionState(),
    val decision: TerminalDecision = TerminalDecision(),
    val contextMemory: TimeframeSnapshot? = null,
    val structureMemory: TimeframeSnapshot? = null,
    val entryMemory: TimeframeSnapshot? = null,
    val analyzedFrames: Long = 0L,
    val lastOcrText: String = "",
    val processingMs: Long = 0L,
    val error: String? = null
)
