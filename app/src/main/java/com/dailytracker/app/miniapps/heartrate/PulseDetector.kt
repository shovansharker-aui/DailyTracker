package com.dailytracker.app.miniapps.heartrate

/**
 * Turns a stream of camera-brightness samples into a beats-per-minute estimate.
 *
 * With a fingertip pressed over the lens and the flash on, each heartbeat pushes a
 * little more blood through the fingertip, which briefly changes how much light the
 * sensor sees (photoplethysmography, the same principle behind a pulse oximeter's
 * red LED). This class detrends the raw signal against its own recent average, finds
 * local peaks with a minimum spacing so noise can't be double-counted, and reports
 * the smoothed rate once enough peaks are seen.
 */
class PulseDetector(
    private val minBpm: Int = 40,
    private val maxBpm: Int = 200,
    private val trendWindowMs: Long = 3000L,
    private val resultWindowMs: Long = 8000L
) {
    private data class Sample(val timestampMs: Long, val value: Double)

    private val recentSamples = ArrayDeque<Sample>()
    private val peakTimestamps = ArrayDeque<Long>()

    private var prevValue: Double? = null
    private var prevPrevValue: Double? = null
    private var prevDetrended: Double = 0.0
    private var prevPrevDetrended: Double = 0.0
    private var lastAcceptedPeakMs: Long = -1L

    private val minPeakIntervalMs = 60_000L / maxBpm
    private val maxPeakIntervalMs = 60_000L / minBpm

    private var smoothedBpm: Double? = null

    val bpm: Int? get() = smoothedBpm?.let { Math.round(it).toInt() }
    val hasEnoughData: Boolean get() = peakTimestamps.size >= 3

    fun reset() {
        recentSamples.clear()
        peakTimestamps.clear()
        prevValue = null
        prevPrevValue = null
        prevDetrended = 0.0
        prevPrevDetrended = 0.0
        lastAcceptedPeakMs = -1L
        smoothedBpm = null
    }

    /** Feed one brightness sample. Returns true if this sample was accepted as a new beat. */
    fun addSample(timestampMs: Long, value: Double): Boolean {
        recentSamples.addLast(Sample(timestampMs, value))
        while (recentSamples.isNotEmpty() && timestampMs - recentSamples.first().timestampMs > trendWindowMs) {
            recentSamples.removeFirst()
        }
        while (peakTimestamps.isNotEmpty() && timestampMs - peakTimestamps.first() > resultWindowMs) {
            peakTimestamps.removeFirst()
        }

        if (recentSamples.size < 5) {
            prevPrevValue = prevValue
            prevValue = value
            return false
        }

        val baseline = recentSamples.sumOf { it.value } / recentSamples.size
        val detrended = value - baseline

        val amplitude = run {
            val values = recentSamples.map { it.value }
            (values.max() - values.min()).coerceAtLeast(0.0001)
        }
        val prominenceThreshold = amplitude * 0.12

        var accepted = false
        val p1 = prevValue
        val p2 = prevPrevValue
        if (p1 != null && p2 != null) {
            // Local maximum: value rose into prevValue, then fell into the current value.
            val isLocalMax = prevPrevDetrended < prevDetrended && prevDetrended > detrended
            if (isLocalMax && prevDetrended > prominenceThreshold) {
                val candidateTimeMs = timestampMs // approx; samples arrive close together
                val sinceLast = if (lastAcceptedPeakMs < 0) Long.MAX_VALUE else candidateTimeMs - lastAcceptedPeakMs
                if (sinceLast >= minPeakIntervalMs) {
                    peakTimestamps.addLast(candidateTimeMs)
                    lastAcceptedPeakMs = candidateTimeMs
                    accepted = true
                }
            }
        }

        prevPrevValue = prevValue
        prevValue = value
        prevPrevDetrended = prevDetrended
        prevDetrended = detrended

        if (accepted) updateBpmEstimate()
        return accepted
    }

    private fun updateBpmEstimate() {
        if (peakTimestamps.size < 3) return
        val intervals = mutableListOf<Long>()
        val list = peakTimestamps.toList()
        for (i in 1 until list.size) {
            val interval = list[i] - list[i - 1]
            if (interval in minPeakIntervalMs..maxPeakIntervalMs) intervals.add(interval)
        }
        if (intervals.isEmpty()) return
        val avgIntervalMs = intervals.average()
        val instantBpm = 60_000.0 / avgIntervalMs
        smoothedBpm = smoothedBpm?.let { it * 0.7 + instantBpm * 0.3 } ?: instantBpm
    }
}
