package hu.elmdash.trip

import hu.elmdash.obd.Pid
import hu.elmdash.obd.Telemetry

data class TripSummary(
    val started: Boolean = false,
    val distanceKm: Double = 0.0,
    val fuelLiters: Double = 0.0,
    val pairedDistanceKm: Double = 0.0,
    val pairedFuelLiters: Double = 0.0,
    val observedSeconds: Double = 0.0,
    val coveredSeconds: Double = 0.0,
    val hasGaps: Boolean = false,
    val fromEngineStart: Boolean = false,
    val containsEstimate: Boolean = false
) {
    val averageL100: Double? get() = if (pairedDistanceKm >= 0.1) pairedFuelLiters * 100 / pairedDistanceKm else null
    val coveragePercent: Double get() = if (observedSeconds > 0) (coveredSeconds * 100 / observedSeconds).coerceIn(0.0, 100.0) else 0.0
}

/** Monotonic clock, trapezoidal integration; missing intervals are omitted, never invented. */
class TripComputer(initial: TripSummary = TripSummary(), private val resetOnEngineRestart: Boolean = true) {
    var engineRestarted = false
        private set
    var summary = initial
        private set
    private data class Point(val time: Long, val speed: Double?, val rate: Double?)
    private var previous: Point? = null
    private var stoppedSince: Long? = null
    private var lastRuntime: Double? = null
    private var runtimeAt: Long? = null

    fun reset() {
        engineRestarted = false
        summary = TripSummary()
        previous = null
        stoppedSince = null
        lastRuntime = null
        runtimeAt = null
    }

    fun gap() {
        if (summary.started) summary = summary.copy(hasGaps = true)
        previous = null
        stoppedSince = null
    }

    fun update(data: Telemetry, fuel: FuelReading, nowMs: Long): TripSummary {
        val rpm = data.value(Pid.RPM, nowMs)
        val runtimeReading = data.readings[Pid.RUN_TIME]
        val runtime = data.value(Pid.RUN_TIME, nowMs)
        val newRuntime = runtime != null && runtimeReading?.atMs != runtimeAt
        val wrapped = lastRuntime != null && lastRuntime!! > 65_000 && runtime != null && runtime < 1_000
        val runtimeRestart = newRuntime && lastRuntime != null && runtime!! + 3 < lastRuntime!! && !wrapped
        val observedRestart = runtime == null && rpm != null && rpm > 0 && stoppedSince?.let { nowMs - it >= 8_000 } == true
        if (rpm == 0.0) { if (stoppedSince == null) stoppedSince = nowMs }
        else if (rpm == null) stoppedSince = null

        engineRestarted = runtimeRestart || observedRestart
        if (engineRestarted) {
            if (resetOnEngineRestart) summary = TripSummary()
            previous = null
        }
        if (rpm != null && rpm > 0 && !summary.started) {
            summary = summary.copy(started = true, fromEngineStart = observedRestart || (runtime != null && runtime <= 10))
        }
        if (rpm != null && rpm > 0) stoppedSince = null
        if (newRuntime) { lastRuntime = runtime; runtimeAt = runtimeReading?.atMs }
        if (!summary.started) return summary
        if (runtime != null && runtime <= 10 && summary.observedSeconds <= 10) {
            summary = summary.copy(fromEngineStart = true)
        }

        val point = Point(nowMs, data.value(Pid.SPEED, nowMs), fuel.litersPerHour)
        val prev = previous
        if (prev != null) {
            val dt = (nowMs - prev.time) / 1000.0
            if (dt > 0 && dt <= 2.0) {
                val speedOk = prev.speed != null && point.speed != null
                val fuelOk = prev.rate != null && point.rate != null
                val distance = if (speedOk) (prev.speed!! + point.speed!!) / 2 * dt / 3600 else 0.0
                val liters = if (fuelOk) (prev.rate!! + point.rate!!) / 2 * dt / 3600 else 0.0
                val paired = speedOk && fuelOk
                summary = summary.copy(
                    distanceKm = summary.distanceKm + distance,
                    fuelLiters = summary.fuelLiters + liters,
                    pairedDistanceKm = summary.pairedDistanceKm + if (paired) distance else 0.0,
                    pairedFuelLiters = summary.pairedFuelLiters + if (paired) liters else 0.0,
                    observedSeconds = summary.observedSeconds + dt,
                    coveredSeconds = summary.coveredSeconds + if (paired) dt else 0.0,
                    hasGaps = summary.hasGaps || !paired
                )
            } else if (dt > 2) {
                summary = summary.copy(hasGaps = true, observedSeconds = summary.observedSeconds + dt)
            }
        }
        summary = summary.copy(containsEstimate = summary.containsEstimate || fuel.source.estimated)
        previous = point
        return summary
    }
}
