package hu.elmdash.elm

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ElmSessionTest {
    @Test fun `session serializes callers and configures headers`() = runTest {
        var inFlight = 0
        var maxInFlight = 0
        val commands = mutableListOf<String>()
        val transport = object : ElmTransport {
            override suspend fun connect() = Unit
            override fun close() = Unit
            override suspend fun exchange(command: String, timeoutMs: Long): String {
                commands += command
                inFlight++; maxInFlight = maxOf(maxInFlight, inFlight)
                delay(10); inFlight--
                return if (command == "ATZ") "ELM327" else if (command.startsWith("AT")) "OK" else "41 ${command.takeLast(2)} 00 00"
            }
        }
        val session = ElmSession(transport)
        session.initialize()
        val a = async { session.read(12) }; val b = async { session.read(13) }
        a.await(); b.await()
        assertEquals(1, maxInFlight)
        assertTrue(commands.containsAll(listOf("ATH1", "ATSP0", "010C", "010D")))
    }
}
