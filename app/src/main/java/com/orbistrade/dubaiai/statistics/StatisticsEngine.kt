package com.orbistrade.dubaiai.statistics

import com.orbistrade.dubaiai.history.SignalHistoryItem
import java.util.Calendar

data class HourStat(val hour: Int, val total: Int, val wins: Int, val losses: Int) {
    val winRate: Double get() = if (wins + losses == 0) 0.0 else wins * 100.0 / (wins + losses)
}

data class StatisticsSnapshot(
    val totalSignals: Int = 0,
    val calls: Int = 0,
    val puts: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val pending: Int = 0,
    val averageScore: Double = 0.0,
    val winRate: Double = 0.0,
    val bestHour: Int? = null,
    val hourly: List<HourStat> = emptyList()
)

object StatisticsEngine {
    fun calculate(items: List<SignalHistoryItem>): StatisticsSnapshot {
        if (items.isEmpty()) return StatisticsSnapshot()
        val wins = items.count { it.outcome == "WIN" }
        val losses = items.count { it.outcome == "LOSS" }
        val marked = wins + losses
        val byHour = items.groupBy { hourOf(it.timestamp) }.map { (hour, rows) ->
            HourStat(hour, rows.size, rows.count { it.outcome == "WIN" }, rows.count { it.outcome == "LOSS" })
        }.sortedBy { it.hour }
        val best = byHour.filter { it.wins + it.losses >= 2 }.maxByOrNull { it.winRate }?.hour
        return StatisticsSnapshot(
            totalSignals = items.size,
            calls = items.count { it.direction == "CALL" },
            puts = items.count { it.direction == "PUT" },
            wins = wins,
            losses = losses,
            pending = items.size - marked,
            averageScore = items.map { it.score }.average(),
            winRate = if (marked == 0) 0.0 else wins * 100.0 / marked,
            bestHour = best,
            hourly = byHour
        )
    }

    private fun hourOf(timestamp: Long): Int = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)
}
