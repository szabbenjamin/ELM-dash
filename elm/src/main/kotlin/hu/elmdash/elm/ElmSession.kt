package hu.elmdash.elm

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** Owns the request/response lock even for transports supplied by future Wi-Fi / USB modules. */
class ElmSession(private val transport: ElmTransport) {
    private val mutex = Mutex()

    suspend fun initialize(): String = mutex.withLock {
        transport.connect()
        val identity = transport.exchange("ATZ", 5_000).replace('>', ' ').trim()
        for (command in listOf("ATE0", "ATL0", "ATS1", "ATH1", "ATSP0")) {
            val reply = transport.exchange(command)
            if (!Regex("\\bOK\\b").containsMatchIn(reply)) throw IOException("ELM inicializálás: $command → ${reply.take(80)}")
        }
        // Clone adapters may not implement adaptive timing. The host timeout remains bounded.
        transport.exchange("ATAT1")
        transport.exchange("ATST64")
        identity
    }

    suspend fun read(pid: Int, timeoutMs: Long = 4_000): ElmReply = mutex.withLock {
        require(pid in 0..255)
        ElmParser.mode01(transport.exchange("01%02X".format(pid), timeoutMs), pid)
    }

    suspend fun adapterVoltage(): Double? = mutex.withLock { ElmParser.voltage(transport.exchange("ATRV")) }
    fun close() = transport.close()
}
