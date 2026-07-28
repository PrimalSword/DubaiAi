package com.orbistrade.dubaiai.vision

import com.orbistrade.dubaiai.core.Candle
import kotlin.math.abs
import kotlin.math.max

/** Maintains candle identity across frames instead of rebuilding the series from scratch. */
class CandleTracker(
    private val maxHistory: Int = 240,
    private val missingFrameTolerance: Int = 4
) {
    private data class Track(var candle: Candle, var lastSeenFrame: Long, var observations: Int)
    private val tracks = linkedMapOf<Int, Track>()
    private var frame = 0L

    @Synchronized
    fun update(detected: List<Candle>, graphWidth: Int): List<Candle> {
        frame++
        val tolerance = max(2, graphWidth / 180)
        detected.sortedBy(Candle::x).forEach { candidate ->
            val key = tracks.keys.minByOrNull { abs(it - candidate.x) }
                ?.takeIf { abs(it - candidate.x) <= tolerance }
            if (key == null) {
                tracks[candidate.x] = Track(candidate, frame, 1)
            } else {
                val track = tracks.remove(key)!!
                val previous = track.candle
                track.candle = Candle(
                    open = previous.open,
                    high = maxOf(previous.high, candidate.high),
                    low = minOf(previous.low, candidate.low),
                    close = candidate.close,
                    x = candidate.x
                )
                track.lastSeenFrame = frame
                track.observations++
                tracks[candidate.x] = track
            }
        }
        tracks.entries.removeAll { frame - it.value.lastSeenFrame > missingFrameTolerance }
        while (tracks.size > maxHistory) tracks.remove(tracks.keys.first())
        return tracks.values.filter { it.observations >= 2 }.map { it.candle }.sortedBy(Candle::x)
    }

    @Synchronized fun reset() = tracks.clear()
}
