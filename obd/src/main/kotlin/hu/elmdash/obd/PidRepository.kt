package hu.elmdash.obd

import hu.elmdash.elm.ElmReply
import hu.elmdash.elm.ElmSession
import hu.elmdash.elm.ObdFrame
import java.io.IOException

/** Serialized adaptive polling. Capability masks and values always come from one ECU. */
class PidRepository(private val session: ElmSession, private val clock: () -> Long) {
    var telemetry = Telemetry()
        private set
    private val nextDue = mutableMapOf<Pid, Long>()
    private val misses = mutableMapOf<Pid, Int>()
    private val supported = mutableSetOf<Int>()
    private val knownPages = mutableSetOf<Int>()
    private var lastEcuResponse = 0L

    suspend fun discover(): Telemetry {
        val first = session.read(0x00, 15_000)
        val frames = (first as? ElmReply.Data)?.frames.orEmpty().filter { it.bytes.size >= 4 }
        val chosen = frames.sortedBy { it.ecu ?: "" }.firstOrNull { 0x0C in supportedBy(0, it.bytes) }
            ?: frames.firstOrNull()
        // Some clones don't return support masks; probe the finite PID allowlist in that case.
        telemetry = Telemetry(ecu = chosen?.ecu)
        if (chosen != null) {
            knownPages += 0
            supported += supportedBy(0, chosen.bytes)
            for (base in listOf(0x20, 0x40)) {
                if (base !in supported) {
                    knownPages += listOf(0x20, 0x40).filter { it >= base }
                    break
                }
                val frame = select(session.read(base)) ?: break
                if (frame.bytes.size < 4) break
                knownPages += base
                supported += supportedBy(base, frame.bytes)
            }
        }
        val now = clock()
        lastEcuResponse = now
        telemetry = telemetry.copy(readings = Pid.entries.associateWith {
            Reading(null, now, if (knownUnsupported(it) && it != Pid.VOLTAGE) Quality.UNSUPPORTED else Quality.NO_DATA)
        })
        return telemetry
    }

    /** Poll one due PID, allowing slow K-line adapters to set the actual refresh rate. */
    suspend fun pollNext(): Telemetry {
        val now = clock()
        val pid = Pid.entries.filter { telemetry.readings[it]?.quality != Quality.UNSUPPORTED }
            .filter { (nextDue[it] ?: 0) <= now }
            .minByOrNull { nextDue[it] ?: 0 } ?: return telemetry
        var value: Double? = null
        var quality = Quality.NO_DATA
        var source = "ECU"
        if (pid != Pid.VOLTAGE || !knownUnsupported(pid)) {
            val reply = session.read(pid.code)
            val frame = select(reply)
            if (frame != null) {
                // Pin on the first valid response when capability discovery was unavailable.
                if (telemetry.ecu == null && frame.ecu != null) telemetry = telemetry.copy(ecu = frame.ecu)
                value = pid.decode(frame.bytes)
                if (value != null) lastEcuResponse = clock()
            }
            quality = when {
                value != null -> Quality.OK
                reply is ElmReply.Error -> Quality.ERROR
                else -> Quality.NO_DATA
            }
        }
        if (pid == Pid.VOLTAGE && value == null) {
            value = session.adapterVoltage()
            source = "ELM tápfeszültség"
            quality = if (value != null) Quality.OK else Quality.NO_DATA
        }
        val failures = if (value == null) (misses[pid] ?: 0) + 1 else 0
        misses[pid] = failures
        // Unsupported/transient NO DATA is re-probed slowly; stale numbers are never retained.
        nextDue[pid] = clock() + if (failures >= 3) 15_000 else pid.intervalMs
        val lastKnown = value ?: telemetry.readings[pid]?.lastKnownValue
        telemetry = telemetry.copy(readings = telemetry.readings + (pid to Reading(value, clock(), quality, source, lastKnown)))
        // ATRV alone is not evidence that the ECU is responding.
        if (clock() - lastEcuResponse > 30_000) throw IOException("Az ECU 30 másodperce nem válaszol. Gyújtás / adapter ellenőrzése.")
        return telemetry
    }

    private fun select(reply: ElmReply): ObdFrame? {
        val frames = (reply as? ElmReply.Data)?.frames ?: return null
        val ecu = telemetry.ecu
        return if (ecu == null) frames.firstOrNull() else frames.firstOrNull { it.ecu == ecu }
    }

    private fun knownUnsupported(pid: Pid) = ((pid.code - 1) / 32 * 32) in knownPages && pid.code !in supported

    companion object {
        fun supportedBy(base: Int, bytes: List<Int>): Set<Int> {
            if (bytes.size < 4) return emptySet()
            val mask = bytes.take(4).fold(0L) { acc, b -> (acc shl 8) or b.toLong() }
            return (1..32).filter { mask and (1L shl (32 - it)) != 0L }.map { base + it }.toSet()
        }
    }
}
