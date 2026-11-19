package hu.elmdash.trip

enum class RangeLevel { UNKNOWN, NORMAL, GOOD, LOW, CRITICAL }

/** Uses paired fuel/distance only; missing fuel coverage is never treated as zero consumption. */
data class RangeEstimate(val km: Double?, val averageL100: Double?, val basis: String, val uncertain: Boolean) {
    val level: RangeLevel get() = when {
        km == null || !km.isFinite() || km < 0 -> RangeLevel.UNKNOWN
        km < 100 -> RangeLevel.CRITICAL
        km < 150 -> RangeLevel.LOW
        km > 250 -> RangeLevel.GOOD
        else -> RangeLevel.NORMAL
    }
    val displayKm: String? get() = km?.let { if (it > 0 && it < 10) "<10" else (kotlin.math.floor(it / 10) * 10).toLong().toString() }
    companion object {
        fun calculate(tank: TankEstimate, daily: TripSummary, history: List<JourneyRecord>): RangeEstimate {
            fun usable(s: TripSummary) = s.pairedDistanceKm.isFinite() && s.pairedDistanceKm > 0 &&
                s.pairedFuelLiters.isFinite() && s.pairedFuelLiters >= 0
            val recent = history.sortedByDescending { it.endedAtMs ?: it.updatedAtMs }.take(30).map { it.summary }.filter(::usable)
            val useDaily = usable(daily) && daily.pairedDistanceKm >= 1 && daily.pairedFuelLiters > 0
            val distance = if (useDaily) daily.pairedDistanceKm else recent.sumOf { it.pairedDistanceKm }
            val fuel = if (useDaily) daily.pairedFuelLiters else recent.sumOf { it.pairedFuelLiters }
            val average = (if (distance >= 1 && fuel > 0) fuel * 100 / distance * tank.learnedFactor else null)?.takeIf { it.isFinite() && it > 0 }
            val km = tank.remainingLiters?.let { liters -> average?.let { liters * 100 / it } }?.takeIf { it.isFinite() }
            return RangeEstimate(km, average, if (useDaily) "mai átlag" else "mentett utak átlaga",
                tank.hasGaps || if (useDaily) daily.hasGaps else recent.any { it.hasGaps })
        }
    }
}
