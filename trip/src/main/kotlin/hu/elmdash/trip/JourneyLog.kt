package hu.elmdash.trip

import hu.elmdash.obd.Pid
import hu.elmdash.obd.Telemetry
import java.util.UUID

const val KALOS_TANK_LITERS = 45.0
const val JOURNEY_DISCONNECT_MS = 180_000L

data class TankEstimate(
    val capacityLiters: Double = KALOS_TANK_LITERS,
    val fullAtMs: Long? = null,
    val consumedLiters: Double = 0.0,
    val hasGaps: Boolean = false,
    val containsEstimate: Boolean = false
) {
    init { require(capacityLiters.isFinite() && capacityLiters > 0); require(consumedLiters.isFinite() && consumedLiters >= 0) }
    val remainingLiters: Double? get() = fullAtMs?.let { (capacityLiters - consumedLiters).coerceIn(0.0, capacityLiters) }
    val percent: Double? get() = remainingLiters?.let { it * 100 / capacityLiters }
}

enum class JourneyEnd(val label: String) {
    STOPPED("Mérés leállítva"), ENGINE_OFF("Motor leállt"), ENGINE_RESTART("Új motorindítás"),
    CONNECTION_LOST("Tartós kapcsolatvesztés"), APP_RESTART("Alkalmazás megszakadt"),
    RESET("Új mérés / beállítás")
}

data class JourneyStats(
    val speedSeconds: Double = 0.0, val movingSeconds: Double = 0.0,
    val idleSeconds: Double = 0.0, val idleFuelLiters: Double = 0.0,
    val maxSpeed: Double? = null, val maxRpm: Double? = null,
    val minCoolant: Double? = null, val maxCoolant: Double? = null,
    val loadIntegral: Double = 0.0, val loadSeconds: Double = 0.0
) { val averageLoad: Double? get() = if (loadSeconds > 0) loadIntegral / loadSeconds else null }

data class JourneyRecord(
    val id: String, val startedAtMs: Long, val updatedAtMs: Long,
    val endedAtMs: Long? = null, val end: JourneyEnd? = null,
    val summary: TripSummary = TripSummary(), val stats: JourneyStats = JourneyStats(),
    val sources: Set<FuelSource> = emptySet(), val profile: String = "",
    val petrolPrice: PetrolPrice? = null
) {
    val estimatedCostHuf: Double? get() = petrolPrice?.takeIf { sources.isNotEmpty() }?.let { summary.fuelLiters * it.hufPerLiter }
    val durationSeconds: Double get() = ((endedAtMs ?: updatedAtMs) - startedAtMs).coerceAtLeast(0) / 1000.0
    val averageSpeed: Double? get() = if (stats.speedSeconds > 0) summary.distanceKm * 3600 / stats.speedSeconds else null
}

data class FullRefill(
    val id: String, val atMs: Long, val pumpedLiters: Double? = null,
    val previousConsumedLiters: Double? = null, val previousHasGaps: Boolean = false,
    val totalPaidHuf: Double? = null
) {
    val hufPerLiter: Double? get() = totalPaidHuf?.let { total -> pumpedLiters?.takeIf { it > 0 }?.let { total / it } }
}

data class JourneyLogState(
    val tank: TankEstimate = TankEstimate(), val journeys: List<JourneyRecord> = emptyList(),
    val refills: List<FullRefill> = emptyList(), val active: JourneyRecord? = null
)

/** Real observations only. Persist one snapshot for both trip and tank so a restart cannot double debit fuel. */
class JourneyLog(initial: JourneyLogState = JourneyLogState(), private val id: () -> String = { UUID.randomUUID().toString() }) {
    var state = initial; private set
    private var computer = TripComputer()
    private data class Point(val at: Long, val rpm: Double?, val speed: Double?, val rate: Double?, val load: Double?)
    private var previous: Point? = null
    private var previousTank: Point? = null
    private var lastRpmAt: Long? = null
    private var stoppedAt: Long? = null

    init {
        // A checkpoint contains totals, never enough evidence to bridge a dead process or reboot.
        initial.active?.let {
            state = initial.copy(active = null, tank = initial.tank.copy(hasGaps = initial.tank.fullAtMs != null || initial.tank.hasGaps),
                journeys = (listOf(it.copy(endedAtMs = it.updatedAtMs, end = JourneyEnd.APP_RESTART,
                    summary = it.summary.copy(hasGaps = true))) + initial.journeys.filterNot { j -> j.id == it.id }).take(200))
        }
    }

    /** Explicit user-requested backfill: only missing prices, never rewrite a known historical price. */
    fun backfillMissingPrices(price: PetrolPrice) {
        if (price.cached) return
        state = state.copy(journeys = state.journeys.map { record ->
            if (record.petrolPrice == null) record.copy(petrolPrice = price.copy(appliedAfterStart = true)) else record
        })
    }

    fun attachPetrolPrice(journeyId: String, price: PetrolPrice) {
        val active = state.active ?: return
        if (active.id == journeyId && active.petrolPrice == null) state = state.copy(active = active.copy(petrolPrice = price))
    }

    fun fullTank(atMs: Long, pumpedLiters: Double? = null, totalPaidHuf: Double? = null) {
        require(pumpedLiters == null || (pumpedLiters.isFinite() && pumpedLiters > 0 && pumpedLiters <= state.tank.capacityLiters))
        require(totalPaidHuf == null || (totalPaidHuf.isFinite() && totalPaidHuf > 0 && pumpedLiters != null && totalPaidHuf / pumpedLiters in 200.0..2000.0))
        val old = state.tank
        val refill = FullRefill(id(), atMs, pumpedLiters, old.fullAtMs?.let { old.consumedLiters }, old.hasGaps, totalPaidHuf)
        state = state.copy(tank = TankEstimate(old.capacityLiters, atMs), refills = (listOf(refill) + state.refills).take(100))
        previousTank = null // Do not debit the part of an interval before the new full-tank anchor.
    }

    private fun latestRefillPrice(): PetrolPrice? = state.refills.firstOrNull { it.hufPerLiter != null }?.let {
        PetrolPrice(it.hufPerLiter!!, it.atMs, sourceUrl = PetrolPrice.MANUAL_SOURCE)
    }

    fun update(data: Telemetry, fuel: FuelReading, now: Long, wallMs: Long, profile: String) {
        fun v(pid: Pid) = data.value(pid, now)?.takeIf { it.isFinite() }
        val point = Point(now, v(Pid.RPM), v(Pid.SPEED), fuel.litersPerHour?.takeIf { it.isFinite() && it >= 0 }, v(Pid.LOAD))
        updateTank(point, fuel.source)
        val rpm = point.rpm
        if (rpm != null) lastRpmAt = now
        if (state.active == null) {
            if (rpm == null || rpm <= 0) { previous = null; return }
            computer = TripComputer()
            state = state.copy(active = JourneyRecord(id(), wallMs, wallMs, profile = profile, petrolPrice = latestRefillPrice()))
            previous = null; stoppedAt = null
        }
        if (rpm == null && checkTimeout(now, wallMs)) return
        val summary = computer.update(data, fuel, now)
        if (computer.engineRestarted) {
            // The old active record still holds its previous summary; archive it before replacing it.
            archive(JourneyEnd.ENGINE_RESTART, wallMs)
            state = state.copy(active = JourneyRecord(id(), wallMs, wallMs, profile = profile, petrolPrice = latestRefillPrice()))
            previous = null; stoppedAt = null
        }
        val record = state.active!!
        var stats = record.stats
        val dt = previous?.let { (now - it.at) / 1000.0 } ?: 0.0
        val prev = previous
        if (prev != null && dt > 0 && dt <= 2) {
            val speedOk = prev.speed != null && point.speed != null
            val idle = speedOk && prev.speed!! < 1 && point.speed!! < 1 && (prev.rpm ?: 0.0) > 0 && (rpm ?: 0.0) > 0
            val moving = speedOk && (prev.speed!! + point.speed!!) / 2 >= 1
            val loadOk = prev.load != null && point.load != null
            stats = stats.copy(
                speedSeconds = stats.speedSeconds + if (speedOk) dt else 0.0,
                movingSeconds = stats.movingSeconds + if (moving) dt else 0.0,
                idleSeconds = stats.idleSeconds + if (idle) dt else 0.0,
                idleFuelLiters = stats.idleFuelLiters + if (idle && prev.rate != null && point.rate != null) (prev.rate + point.rate) / 2 * dt / 3600 else 0.0,
                loadIntegral = stats.loadIntegral + if (loadOk) (prev.load!! + point.load!!) / 2 * dt else 0.0,
                loadSeconds = stats.loadSeconds + if (loadOk) dt else 0.0)
        }
        fun maximum(a: Double?, b: Double?) = if (a == null) b else if (b == null) a else maxOf(a, b)
        fun minimum(a: Double?, b: Double?) = if (a == null) b else if (b == null) a else minOf(a, b)
        stats = stats.copy(maxSpeed = maximum(stats.maxSpeed, point.speed), maxRpm = maximum(stats.maxRpm, rpm),
            minCoolant = minimum(stats.minCoolant, v(Pid.COOLANT)), maxCoolant = maximum(stats.maxCoolant, v(Pid.COOLANT)))
        state = state.copy(active = record.copy(updatedAtMs = wallMs, summary = summary, stats = stats,
            sources = record.sources + if (fuel.source in setOf(FuelSource.ECU, FuelSource.MAF_ESTIMATE, FuelSource.MAP_ESTIMATE)) setOf(fuel.source) else emptySet()))
        previous = point
        if (rpm == 0.0) {
            if (stoppedAt == null) stoppedAt = now
            if (now - stoppedAt!! >= 8_000) finish(JourneyEnd.ENGINE_OFF, wallMs)
        } else stoppedAt = null
    }

    private fun updateTank(p: Point, source: FuelSource) {
        val old = state.tank
        if (old.fullAtMs != null) {
            val prev = previousTank
            val dt = prev?.let { (p.at - it.at) / 1000.0 } ?: 0.0
            val valid = prev != null && dt > 0 && dt <= 2 && prev.rate != null && p.rate != null
            val amount = if (valid) (prev!!.rate!! + p.rate!!) / 2 * dt / 3600 else 0.0
            val gap = (p.rpm ?: prev?.rpm ?: 0.0) > 0 && p.rate == null || prev != null && (dt > 2 || dt < 0)
            state = state.copy(tank = old.copy(consumedLiters = old.consumedLiters + amount,
                hasGaps = old.hasGaps || gap, containsEstimate = old.containsEstimate || (amount > 0 && source.estimated)))
        }
        previousTank = p
    }

    /** Also runs after finite connection retries end, without integrating stale samples. */
    fun checkTimeout(now: Long, wallMs: Long): Boolean {
        if (state.active != null && lastRpmAt?.let { now - it >= JOURNEY_DISCONNECT_MS } == true) {
            finish(JourneyEnd.CONNECTION_LOST, wallMs)
            return true
        }
        return false
    }

    fun gap() {
        computer.gap()
        if (state.active != null) state = state.copy(active = state.active!!.copy(summary = computer.summary.copy(hasGaps = true)))
        if (state.tank.fullAtMs != null && (previousTank?.rpm ?: 0.0) > 0)
            state = state.copy(tank = state.tank.copy(hasGaps = true))
        previous = null; previousTank = null
    }
    fun finish(reason: JourneyEnd, wallMs: Long) {
        if (reason !in setOf(JourneyEnd.ENGINE_OFF, JourneyEnd.RESET)) gap()
        archive(reason, wallMs)
        computer = TripComputer(); previous = null; stoppedAt = null; lastRpmAt = null
    }
    private fun archive(reason: JourneyEnd, wallMs: Long) {
        val record = state.active ?: return
        val ended = record.copy(endedAtMs = wallMs.coerceAtLeast(record.startedAtMs), updatedAtMs = wallMs, end = reason)
        state = state.copy(active = null, journeys = (listOf(ended) + state.journeys.filterNot { it.id == record.id }).take(200))
    }
}
