package hu.elmdash.obd

enum class Pid(val code: Int, val size: Int, val unit: String, val intervalMs: Long) {
    LOAD(0x04, 1, "%", 1_000), COOLANT(0x05, 1, "°C", 3_000),
    MAP(0x0B, 1, "kPa", 1_500), RPM(0x0C, 2, "rpm", 500), SPEED(0x0D, 1, "km/h", 500),
    IAT(0x0F, 1, "°C", 2_000), MAF(0x10, 2, "g/s", 700), TPS(0x11, 1, "%", 1_500),
    RUN_TIME(0x1F, 2, "s", 2_000), VOLTAGE(0x42, 2, "V", 3_000), FUEL_RATE(0x5E, 2, "l/h", 700);

    fun decode(data: List<Int>): Double? {
        if (data.size < size || data.any { it !in 0..255 }) return null
        val a = data[0].toDouble()
        val ab = if (size == 2) a * 256 + data[1] else a
        return when (this) {
            LOAD, TPS -> a * 100 / 255
            COOLANT, IAT -> a - 40
            RPM -> ab / 4
            MAF -> ab / 100
            VOLTAGE -> ab / 1000
            FUEL_RATE -> ab / 20
            else -> ab
        }
    }
}

enum class Quality { OK, UNSUPPORTED, NO_DATA, ERROR }
data class Reading(val value: Double?, val atMs: Long, val quality: Quality, val source: String = "ECU",
    val lastKnownValue: Double? = value) {
    fun fresh(nowMs: Long, maxAgeMs: Long = 5_000): Double? =
        value?.takeIf { quality == Quality.OK && nowMs - atMs in 0..maxAgeMs }
}

data class Telemetry(val readings: Map<Pid, Reading> = emptyMap(), val ecu: String? = null) {
    fun value(pid: Pid, nowMs: Long): Double? = readings[pid]?.fresh(nowMs)
    fun clearValues(nowMs: Long) = copy(readings = readings.mapValues { (_, reading) ->
        if (reading.quality == Quality.UNSUPPORTED) reading else Reading(null, nowMs, Quality.NO_DATA, reading.source, reading.lastKnownValue)
    })
    fun withLastKnown(previous: Telemetry) = copy(readings = readings.mapValues { (pid, reading) ->
        reading.copy(lastKnownValue = reading.value ?: reading.lastKnownValue ?: previous.readings[pid]?.lastKnownValue)
    })
}
