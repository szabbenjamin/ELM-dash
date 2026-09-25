package hu.elmdash.obd

import org.junit.Assert.*
import org.junit.Test

class PidTest {
    @Test fun `standard PID formulas`() {
        val examples = listOf(
            Triple(Pid.LOAD, listOf(128), 50.196078), Triple(Pid.COOLANT, listOf(123), 83.0),
            Triple(Pid.IAT, listOf(70), 30.0), Triple(Pid.IAT, listOf(0), -40.0),
            Triple(Pid.MAP, listOf(100), 100.0), Triple(Pid.RPM, listOf(0x1A, 0xF8), 1726.0),
            Triple(Pid.SPEED, listOf(80), 80.0), Triple(Pid.MAF, listOf(1, 244), 5.0),
            Triple(Pid.TPS, listOf(255), 100.0), Triple(Pid.RUN_TIME, listOf(1, 0), 256.0),
            Triple(Pid.VOLTAGE, listOf(0x37, 0x78), 14.2), Triple(Pid.FUEL_RATE, listOf(0, 120), 6.0)
        )
        examples.forEach { (pid, data, value) -> assertEquals(pid.name, value, pid.decode(data)!!, 0.00001) }
    }
    @Test fun `malformed values and stale readings never become zero`() {
        assertNull(Pid.RPM.decode(listOf(0)))
        assertNull(Pid.COOLANT.decode(listOf(-1)))
        val r = Reading(800.0, 1_000, Quality.OK)
        assertNull(r.fresh(6_001)); assertNull(r.fresh(999))
        assertEquals(800.0, r.fresh(2_000)!!, 0.0)
        assertNull(r.copy(quality = Quality.ERROR).fresh(2_000))
    }
    @Test fun `support masks preserve MSB ordering and continuation`() {
        assertEquals(setOf(1, 32), PidRepository.supportedBy(0, listOf(0x80, 0, 0, 1)))
        assertEquals(setOf(0x42, 0x5E), PidRepository.supportedBy(0x40, listOf(0x40, 0, 0, 4)))
    }
}
