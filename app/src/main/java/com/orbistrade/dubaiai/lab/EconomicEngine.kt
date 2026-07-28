package com.orbistrade.dubaiai.lab

import com.orbistrade.dubaiai.history.SignalHistoryItem
import kotlin.math.sqrt

object EconomicEngine {
    fun breakEvenRate(payoutPercent: Double?): Double? {
        val payout = payoutPercent?.div(100.0) ?: return null
        if (payout <= 0.0) return null
        return 1.0 / (1.0 + payout)
    }

    fun expectedValue(probability: Double?, payoutPercent: Double?): Double? {
        val p = probability ?: return null
        val payout = payoutPercent?.div(100.0) ?: return null
        return p * payout - (1.0 - p)
    }

    fun calibratedProbability(history: List<SignalHistoryItem>, mode: MarketMode): Pair<Double?, Int> {
        val resolved = history.filter {
            it.marketMode == mode.name && (it.outcome == "WIN" || it.outcome == "LOSS")
        }
        if (resolved.isEmpty()) return null to 0
        val wins = resolved.count { it.outcome == "WIN" }
        // Laplace smoothing prevents 0%/100% certainty on tiny samples.
        return (wins + 1.0) / (resolved.size + 2.0) to resolved.size
    }

    fun confidenceLabel(probability: Double?, sampleSize: Int): String = when {
        probability == null || sampleSize < 30 -> "AMOSTRA INSUFICIENTE"
        sampleSize < 100 -> "EVIDÊNCIA FRACA"
        sampleSize < 500 -> "AVALIAÇÃO PRELIMINAR"
        sampleSize < 2_000 -> "EVIDÊNCIA MODERADA"
        else -> "AMOSTRA ROBUSTA"
    }

    fun wilsonHalfWidth(probability: Double?, sampleSize: Int, z: Double = 1.96): Double? {
        val p = probability ?: return null
        if (sampleSize <= 0) return null
        val n = sampleSize.toDouble()
        return z * sqrt((p * (1.0 - p) / n) + (z * z / (4.0 * n * n))) / (1.0 + z * z / n)
    }

    fun snapshot(history: List<SignalHistoryItem>, context: MarketContext): EconomicSnapshot {
        val (probability, sample) = calibratedProbability(history, context.mode)
        val breakEven = breakEvenRate(context.payoutPercent)
        val ev = expectedValue(probability, context.payoutPercent)
        return EconomicSnapshot(
            breakEvenRate = breakEven,
            calibratedProbability = probability,
            expectedValue = ev,
            edgePercentagePoints = if (probability != null && breakEven != null) (probability - breakEven) * 100.0 else null,
            sampleSize = sample,
            confidenceLabel = confidenceLabel(probability, sample)
        )
    }
}
