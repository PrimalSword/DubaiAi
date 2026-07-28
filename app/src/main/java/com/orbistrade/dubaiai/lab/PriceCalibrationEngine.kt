package com.orbistrade.dubaiai.lab

/** Linear mapping from chart Y coordinates to prices using two OCR anchor labels. */
data class PriceAnchor(val y: Double, val price: Double)

data class PriceCalibration(
    val top: PriceAnchor,
    val bottom: PriceAnchor,
    val confidence: Double
) {
    fun priceAt(y: Double): Double {
        val dy = bottom.y - top.y
        require(kotlin.math.abs(dy) > 0.000001) { "Âncoras de preço inválidas" }
        val fraction = (y - top.y) / dy
        return top.price + fraction * (bottom.price - top.price)
    }
}

object PriceCalibrationEngine {
    fun build(anchors: List<PriceAnchor>): PriceCalibration? {
        val distinct = anchors.distinctBy { it.y.toInt() }.sortedBy(PriceAnchor::y)
        if (distinct.size < 2) return null
        val top = distinct.first()
        val bottom = distinct.last()
        if (top.price == bottom.price || top.y == bottom.y) return null
        val monotonic = distinct.zipWithNext().count { (a,b) -> (b.price-a.price) * (bottom.price-top.price) > 0 }
        val confidence = (monotonic.toDouble() / (distinct.size-1)).coerceIn(0.0,1.0)
        return PriceCalibration(top,bottom,confidence)
    }
}
