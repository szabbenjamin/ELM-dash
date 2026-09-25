package hu.elmdash.trip

import hu.elmdash.obd.Pid
import hu.elmdash.obd.Telemetry
import kotlin.math.abs

/** Approximate Kalos 1.2 SOHC five-speed profile, not an ECU shift recommendation. */
object Kalos12Profile {
    val gearRatios = listOf(3.416, 1.950, 1.280, 0.971, 0.757)
    const val finalDrive = 4.105
    // Both handbook sizes (175/70 R13, 185/60 R14) have approximately this circumference.
    const val wheelCircumferenceM = 1.81
    fun rpmPerKmh(gear: Int) = gearRatios[gear - 1] * finalDrive * 1000 / (60 * wheelCircumferenceM)
}

enum class DriveCue(val label: String) {
    NO_DATA("Nincs friss adat"), STOPPED("Motor áll"), IDLE("Álló helyzet"),
    COLD("Melegedő motor"), STEADY("Nincs váltási javaslat"),
    EFFICIENT("Kedvező tartomány"), HIGH_LOAD("Nagy terhelés"),
    LOW_RPM_LOAD("Alacsony fordulat, nagy terhelés"),
    UPSHIFT("Felváltás javasolt, ha a forgalom engedi"),
    DOWNSHIFT("Visszaváltás javasolt"), HIGH_RPM("Magas fordulatszám"),
    HOT("Magas vízhőfok")
}
enum class CoolantBand(val label: String) {
    NO_DATA("Nincs friss adat"), COLD("Hideg"), WARMING("Melegszik"),
    NORMAL("Üzemi tartomány"), WARM("Emelkedett"), HOT("Magas vízhőfok")
}
data class DrivingAdvice(
    val cue: DriveCue = DriveCue.NO_DATA,
    val coolant: CoolantBand = CoolantBand.NO_DATA,
    val estimatedGear: Int? = null
)

/** Stable ratio and dwell time suppress clutch/shift transients and threshold chatter. */
class DrivingAdvisor {
    private var candidateGear: Int? = null
    private var gearSince = 0L
    private var candidateCue = DriveCue.NO_DATA
    private var cueSince = 0L
    private var shownCue = DriveCue.NO_DATA
    private var coolantBand = CoolantBand.NO_DATA

    fun reset() {
        candidateGear = null; gearSince = 0; candidateCue = DriveCue.NO_DATA
        cueSince = 0; shownCue = DriveCue.NO_DATA; coolantBand = CoolantBand.NO_DATA
    }

    fun update(telemetry: Telemetry, nowMs: Long): DrivingAdvice {
        fun fresh(pid: Pid, maxAge: Long) = telemetry.readings[pid]?.fresh(nowMs, maxAge)
        val rpm = fresh(Pid.RPM, 1_500)
        val speed = fresh(Pid.SPEED, 1_500)
        val load = fresh(Pid.LOAD, 2_500)
        val tps = fresh(Pid.TPS, 3_000)
        val coolant = fresh(Pid.COOLANT, 5_000)
        coolantBand = temperatureBand(coolant)

        val gearCandidate = if (rpm != null && rpm >= 1_000 && speed != null && speed >= 12) {
            val ratio = rpm / speed
            (1..5).minBy { abs(ratio / Kalos12Profile.rpmPerKmh(it) - 1) }
                .takeIf { abs(ratio / Kalos12Profile.rpmPerKmh(it) - 1) <= 0.08 }
        } else null
        if (gearCandidate != candidateGear) { candidateGear = gearCandidate; gearSince = nowMs }
        val gear = candidateGear?.takeIf { nowMs - gearSince >= 3_000 }

        val ready = rpm != null && speed != null && load != null && tps != null && coolant != null
        val nextRpm = if (rpm != null && gear != null && gear < 5)
            rpm * Kalos12Profile.gearRatios[gear] / Kalos12Profile.gearRatios[gear - 1] else null
        val lowerRpm = if (rpm != null && gear != null && gear > 1)
            rpm * Kalos12Profile.gearRatios[gear - 2] / Kalos12Profile.gearRatios[gear - 1] else null

        val cue = when {
            rpm == null -> DriveCue.NO_DATA
            rpm < 100 -> DriveCue.STOPPED
            coolantBand == CoolantBand.HOT -> DriveCue.HOT
            speed != null && speed < 8 -> if (rpm >= 4_500) DriveCue.HIGH_RPM else DriveCue.IDLE
            !ready -> DriveCue.NO_DATA
            coolantBand in setOf(CoolantBand.COLD, CoolantBand.WARMING) -> DriveCue.COLD
            rpm >= 4_500 -> DriveCue.HIGH_RPM
            // Closed throttle / coasting must not trigger downshifts or economy upshifts.
            tps!! < 8 -> DriveCue.STEADY
            rpm < (if (shownCue == DriveCue.DOWNSHIFT) 2_150 else 2_000) &&
                load!! >= (if (shownCue == DriveCue.DOWNSHIFT) 62 else 70) ->
                if (lowerRpm != null && lowerRpm < 4_000) DriveCue.DOWNSHIFT else DriveCue.LOW_RPM_LOAD
            load!! >= 70 -> DriveCue.HIGH_LOAD
            rpm >= (if (shownCue == DriveCue.UPSHIFT) 2_850 else 3_000) &&
                load <= (if (shownCue == DriveCue.UPSHIFT) 55 else 45) && speed!! >= 20 &&
                nextRpm != null && nextRpm >= 1_900 -> DriveCue.UPSHIFT
            rpm in 2_000.0..3_000.0 && load < 70 -> DriveCue.EFFICIENT
            rpm > 3_000 -> DriveCue.HIGH_RPM
            else -> DriveCue.STEADY
        }

        if (cue != candidateCue) { candidateCue = cue; cueSince = nowMs }
        val urgentOrInvalid = cue in setOf(DriveCue.NO_DATA, DriveCue.STOPPED, DriveCue.IDLE, DriveCue.HOT, DriveCue.COLD)
        // Never retain an old arrow when its required data / gear / load condition is gone.
        val oldArrowInvalid = shownCue in setOf(DriveCue.UPSHIFT, DriveCue.DOWNSHIFT) && cue != shownCue
        if (urgentOrInvalid) shownCue = cue
        else if (nowMs - cueSince >= 2_000) shownCue = cue
        else if (oldArrowInvalid || shownCue in setOf(DriveCue.NO_DATA, DriveCue.IDLE, DriveCue.STOPPED, DriveCue.COLD, DriveCue.HOT))
            shownCue = DriveCue.STEADY

        return DrivingAdvice(shownCue, coolantBand, gear)
    }

    private fun temperatureBand(c: Double?): CoolantBand = when {
        c == null -> CoolantBand.NO_DATA
        c >= 110 || (coolantBand == CoolantBand.HOT && c >= 107) -> CoolantBand.HOT
        c >= 103 || (coolantBand == CoolantBand.WARM && c >= 100) -> CoolantBand.WARM
        c < 60 || (coolantBand == CoolantBand.COLD && c < 63) -> CoolantBand.COLD
        c < 80 || (coolantBand == CoolantBand.WARMING && c < 82) -> CoolantBand.WARMING
        else -> CoolantBand.NORMAL
    }
}
