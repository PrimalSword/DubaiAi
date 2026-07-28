package com.orbistrade.dubaiai.lab

import com.orbistrade.dubaiai.history.SignalHistoryItem

data class ValidationWindow(
    val trainingSize: Int,
    val validationSize: Int,
    val testSize: Int,
    val trainingWinRate: Double?,
    val validationWinRate: Double?,
    val testWinRate: Double?,
    val stable: Boolean
)

object WalkForwardEngine {
    fun evaluate(items: List<SignalHistoryItem>): ValidationWindow {
        val resolved = items.filter { it.outcome == "WIN" || it.outcome == "LOSS" }.sortedBy(SignalHistoryItem::timestamp)
        if (resolved.size < 30) return ValidationWindow(resolved.size,0,0,rate(resolved),null,null,false)
        val trainEnd = (resolved.size * 0.60).toInt().coerceAtLeast(1)
        val validationEnd = (resolved.size * 0.80).toInt().coerceAtLeast(trainEnd + 1)
        val train = resolved.subList(0,trainEnd)
        val validation = resolved.subList(trainEnd,validationEnd)
        val test = resolved.subList(validationEnd,resolved.size)
        val rates = listOfNotNull(rate(train),rate(validation),rate(test))
        val stable = rates.size == 3 && rates.maxOrNull()!! - rates.minOrNull()!! <= 0.12
        return ValidationWindow(train.size,validation.size,test.size,rate(train),rate(validation),rate(test),stable)
    }

    private fun rate(items: List<SignalHistoryItem>): Double? = if(items.isEmpty()) null else items.count{it.outcome=="WIN"}.toDouble()/items.size
}
