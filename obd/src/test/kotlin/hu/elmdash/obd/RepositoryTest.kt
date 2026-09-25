package hu.elmdash.obd

import hu.elmdash.elm.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RepositoryTest {
    @Test fun `failed continuation mask does not mark later PIDs unsupported`() = runTest {
        val transport = object : ElmTransport {
            override suspend fun connect() = Unit
            override fun close() = Unit
            override suspend fun exchange(command: String, timeoutMs: Long) = when(command) {
                "0100" -> "7E8 06 41 00 00 10 00 01"
                else -> "NO DATA"
            }
        }
        val repo = PidRepository(ElmSession(transport)) { 0L }
        repo.discover()
        assertEquals(Quality.NO_DATA, repo.telemetry.readings[Pid.FUEL_RATE]!!.quality)
        assertEquals(Quality.UNSUPPORTED, repo.telemetry.readings[Pid.MAF]!!.quality)
    }

    @Test fun `repository pins engine ECU and uses ATRV fallback`() = runTest {
        var now = 0L
        val commands = mutableListOf<String>()
        val transport = object : ElmTransport {
            override suspend fun connect() = Unit
            override fun close() = Unit
            override suspend fun exchange(command: String, timeoutMs: Long): String {
                commands += command
                return when (command) {
                    "0100" -> "7E9 06 41 00 00 00 00 00\r7E8 06 41 00 00 18 00 00"
                    "010C" -> "7E9 04 41 0C 00 00\r7E8 04 41 0C 0C 80"
                    "010D" -> "7E8 03 41 0D 32"
                    "ATRV" -> "14.2V"
                    else -> "NO DATA"
                }
            }
        }
        val repo = PidRepository(ElmSession(transport)) { now }
        repo.discover()
        repeat(3) { repo.pollNext(); now += 40 }
        assertEquals("7E8", repo.telemetry.ecu)
        assertEquals(800.0, repo.telemetry.value(Pid.RPM, now)!!, 0.0)
        assertEquals(14.2, repo.telemetry.value(Pid.VOLTAGE, now)!!, 0.0)
        assertEquals(Quality.UNSUPPORTED, repo.telemetry.readings[Pid.COOLANT]!!.quality)
        assertFalse(commands.contains("0105"))
        assertFalse(commands.contains("0142"))
    }

    @Test fun `NO DATA clears previous value and transient failures are reprobed`() = runTest {
        var now = 0L
        var rpmCalls = 0
        val transport = object : ElmTransport {
            override suspend fun connect() = Unit
            override fun close() = Unit
            override suspend fun exchange(command: String, timeoutMs: Long) = when(command) {
                "0100" -> "41 00 00 10 00 00"
                "010C" -> if (++rpmCalls == 1) "41 0C 0C 80" else "NO DATA"
                "ATRV" -> "12.0V"
                else -> "NO DATA"
            }
        }
        val repo = PidRepository(ElmSession(transport)) { now }
        repo.discover(); repo.pollNext(); repo.pollNext()
        assertEquals(800.0, repo.telemetry.value(Pid.RPM, now)!!, 0.0)
        now = 600; repo.pollNext()
        assertNull(repo.telemetry.value(Pid.RPM, now))
        assertEquals(800.0, repo.telemetry.readings[Pid.RPM]!!.lastKnownValue!!, 0.0)
        assertEquals(Quality.NO_DATA, repo.telemetry.readings[Pid.RPM]!!.quality)
    }
}
