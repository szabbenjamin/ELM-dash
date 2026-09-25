package hu.elmdash.connection

import hu.elmdash.elm.ElmTransport
import hu.elmdash.obd.Pid
import hu.elmdash.trip.Kalos12Profile
import kotlinx.coroutines.delay
import kotlin.math.sin

/** Demo uses the real ASCII parser, capability discovery, repository and trip pipeline. */
class DemoTransport(private val clock: () -> Long) : ElmTransport {
    private var started = 0L
    override suspend fun connect() { started = clock() }
    override fun close() = Unit
    override suspend fun exchange(command: String, timeoutMs: Long): String {
        delay(40)
        if (command == "ATZ") return "ELM327 DEMO v1.0\r>"
        if (command == "ATRV") return "14.2V\r>"
        if (command.startsWith("AT")) return "OK\r>"
        val code = command.drop(2).toInt(16)
        val t = (clock() - started) / 1000.0
        // Repeating, physically consistent gear / RPM scenes exercise the Auto guidance.
        val phase = t % 120
        val gear = when { phase < 68 -> 4; phase < 86 -> 5; phase < 100 -> 3; else -> 4 }
        val rpm = when {
            phase < 8 -> 820
            phase < 30 -> 2_400
            phase < 48 -> 1_700
            phase < 68 -> 3_200
            phase < 86 -> 3_600
            phase < 100 -> 2_600
            else -> 2_350
        } + if (phase < 8) 0 else (25 * sin(t / 3)).toInt()
        val speed = if (phase < 8) 0 else (rpm / Kalos12Profile.rpmPerKmh(gear)).toInt()
        val loadPercent = when { phase < 8 -> 14; phase in 30.0..<48.0 || phase in 86.0..<100.0 -> 82; else -> 35 }
        val data = if (code in listOf(0, 0x20, 0x40)) {
            val codes = Pid.entries.map { it.code }.toSet() + setOf(0x20, 0x40)
            val mask = (1..32).filter { code + it in codes }.fold(0L) { bits, bit -> bits or (1L shl (32 - bit)) }
            (3 downTo 0).map { ((mask shr (it * 8)) and 255).toInt() }
        } else {
            val pid = Pid.entries.firstOrNull { it.code == code } ?: return "NO DATA\r>"
            val raw = when (pid) {
                Pid.RPM -> rpm * 4
                Pid.SPEED -> speed
                Pid.LOAD -> (loadPercent * 255 / 100)
                Pid.COOLANT -> (72 + t / 2).toInt().coerceAtMost(92) + 40
                Pid.MAP -> if (speed == 0) 31 else 48
                Pid.IAT -> 30 + 40
                Pid.MAF -> if (speed == 0) 270 else 1450
                Pid.TPS -> if (speed == 0) 12 else if (loadPercent >= 70) 105 else 54
                Pid.RUN_TIME -> t.toInt() % 65536
                Pid.VOLTAGE -> 14200
                Pid.FUEL_RATE -> if (speed == 0) 15 else (speed * (6.8 + sin(t / 9) * 0.6) / 100 * 20).toInt()
            }
            if (pid.size == 1) listOf(raw) else listOf((raw shr 8) and 255, raw and 255)
        }
        return "7E8 %02X 41 %02X %s\r>".format(data.size + 2, code, data.joinToString(" ") { "%02X".format(it) })
    }
}
