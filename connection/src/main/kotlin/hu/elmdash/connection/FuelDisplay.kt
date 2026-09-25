package hu.elmdash.connection

import hu.elmdash.trip.FuelReading
import hu.elmdash.trip.FuelSource

/** Last display value is presentation state only, never an input to trip integration. */
data class FuelDisplay(
    val value: Double? = null,
    val unit: String = "l/100 km",
    val fresh: Boolean = false,
    val source: FuelSource = FuelSource.UNAVAILABLE
) {
    fun update(reading: FuelReading, speed: Double?): FuelDisplay {
        // A valid stop is not missing data: per-distance consumption is undefined here.
        if (speed != null && speed.isFinite() && speed in 0.0..<5.0)
            return FuelDisplay(source = reading.source)
        // Missing observations retain the last number in grey, never switch to an hourly unit.
        if (speed == null || !speed.isFinite() || speed < 0 || reading.litersPerHour == null)
            return copy(fresh = false)
        val per100 = reading.litersPer100Km?.takeIf { it.isFinite() && it >= 0 }
            ?: return copy(fresh = false)
        return FuelDisplay(per100, "l/100 km", true, reading.source)
    }
}
