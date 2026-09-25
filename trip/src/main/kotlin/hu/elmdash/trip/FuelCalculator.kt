package hu.elmdash.trip

import hu.elmdash.obd.Pid
import hu.elmdash.obd.Quality
import hu.elmdash.obd.Telemetry

enum class FuelProfile(val label: String, val afr: Double?, val densityGramsPerLiter: Double,
    val displacementLiters: Double? = null) {
    KALOS_12("Kalos 1.2 benzines • MAP-becsléssel", 14.7, 745.0, 1.15),
    ECU_ONLY("Csak ECU-adat / dízel", null, 745.0),
    PETROL("Benzin (14,7 AFR) • ECU / MAF", 14.7, 745.0),
    E10("Benzin E10 (14,1 AFR) • ECU / MAF", 14.1, 750.0)
}

data class FuelSettings(val profile: FuelProfile = FuelProfile.ECU_ONLY, val correction: Double = 1.0,
    val volumetricEfficiency: Double = 0.80) {
    init {
        require(correction.isFinite() && correction in 0.5..2.0)
        require(volumetricEfficiency.isFinite() && volumetricEfficiency in 0.4..1.2)
    }
}
enum class FuelSource(val label: String, val estimated: Boolean = false) {
    ECU("ECU • 015E"), MAF_ESTIMATE("MAF-becslés", true),
    MAP_ESTIMATE("MAP-becslés • kalibrálandó", true),
    ENGINE_OFF("Motor áll"), UNAVAILABLE("Nincs fogyasztásadat")
}
data class FuelReading(val litersPerHour: Double?, val litersPer100Km: Double?, val source: FuelSource,
    val unavailableReason: String? = null)

object FuelCalculator {
    fun calculate(data: Telemetry, nowMs: Long, settings: FuelSettings): FuelReading {
        fun value(pid: Pid) = data.value(pid, nowMs)?.takeIf { it.isFinite() }
        val rpm = value(Pid.RPM)
        val ecuRate = value(Pid.FUEL_RATE)?.takeIf { it >= 0 }
        val maf = value(Pid.MAF)?.takeIf { it > 0 }
        val map = value(Pid.MAP)?.takeIf { it in 1.0..120.0 } // Naturally aspirated Kalos only.
        val iat = value(Pid.IAT)?.takeIf { it in -40.0..150.0 }
        val afr = settings.profile.afr
        val displacement = settings.profile.displacementLiters
        val source: FuelSource
        val rawRate: Double?
        when {
            rpm == 0.0 -> { source = FuelSource.ENGINE_OFF; rawRate = 0.0 }
            ecuRate != null -> { source = FuelSource.ECU; rawRate = ecuRate }
            maf != null && afr != null && rpm != null && rpm > 0 -> {
                source = FuelSource.MAF_ESTIMATE
                rawRate = maf * 3600 / (afr * settings.profile.densityGramsPerLiter)
            }
            displacement != null && afr != null && rpm != null && rpm in 1.0..8_000.0 && map != null && iat != null -> {
                // Four-stroke speed density: density [kg/m³] × displacement [m³] × cycles/s × VE.
                // IAT is measured; the fixed VE is an explicit, uncalibrated assumption, not ECU fuel flow.
                val airDensity = map * 1000 / (287.05 * (iat + 273.15))
                val gramsPerSecond = airDensity * (displacement / 1000) * (rpm / 120) * settings.volumetricEfficiency * 1000
                source = FuelSource.MAP_ESTIMATE
                rawRate = gramsPerSecond * 3600 / (afr * settings.profile.densityGramsPerLiter)
            }
            else -> { source = FuelSource.UNAVAILABLE; rawRate = null }
        }
        val rate = rawRate?.times(settings.correction)
        val speed = value(Pid.SPEED)
        val per100 = if (speed != null && speed >= 5 && rate != null) rate * 100 / speed else null
        val reason = if (rate != null) null else when {
            afr == null -> "Nincs ECU-fogyasztásadat • válassz benzines / Kalos profilt az Út / nap lapon"
            displacement == null && maf == null -> "Nincs MAF-adat • a Kalos profil MAP + IAT alapján becsül"
            rpm == null -> "A fogyasztáshoz friss fordulatszám kell"
            map == null -> missing(data, Pid.MAP, "MAP")
            iat == null -> missing(data, Pid.IAT, "beszívott levegő hőmérséklet (IAT)")
            else -> "A fogyasztás bemenő adata tartományon kívül van"
        }
        return FuelReading(rate, per100, source, reason)
    }
    private fun missing(data: Telemetry, pid: Pid, label: String) =
        if (data.readings[pid]?.quality == Quality.UNSUPPORTED) "Az ECU nem ad $label adatot • nincs becslés"
        else "A fogyasztáshoz friss $label adat kell"
}
