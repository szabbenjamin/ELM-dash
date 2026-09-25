package hu.elmdash.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import hu.elmdash.elm.ElmTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Classic Bluetooth SPP. BLE adapters require a different transport implementation. */
@SuppressLint("MissingPermission") // The visible Activity and the Service both check CONNECT first.
class BluetoothElmTransport(
    private val adapter: BluetoothAdapter,
    private val address: String,
    private val insecure: Boolean = false
) : ElmTransport {
    @Volatile private var socket: BluetoothSocket? = null
    private val mutex = Mutex()

    override suspend fun connect() {
        require(BluetoothAdapter.checkBluetoothAddress(address)) { "Hibás Bluetooth-cím" }
        check(adapter.isEnabled) { "A Bluetooth ki van kapcsolva" }
        val device = adapter.getRemoteDevice(address)
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        val pending = if (insecure) device.createInsecureRfcommSocketToServiceRecord(uuid)
            else device.createRfcommSocketToServiceRecord(uuid)
        socket = pending
        try {
            withTimeout(12_000) {
                // Socket.connect isn't interruptible; closing the socket unblocks it on cancellation.
                suspendCancellableCoroutine<Unit> { continuation ->
                    continuation.invokeOnCancellation { runCatching { pending.close() } }
                    Thread({
                        try { pending.connect(); continuation.resume(Unit) }
                        catch (e: Exception) { continuation.resumeWithException(e) }
                    }, "elm-connect").start()
                }
            }
        } catch (e: TimeoutCancellationException) {
            close(); throw IOException("Bluetooth csatlakozási időtúllépés", e)
        } catch (e: Exception) { close(); throw e }
    }

    override suspend fun exchange(command: String, timeoutMs: Long): String = mutex.withLock {
        // Read-only vehicle allowlist; no arbitrary commands / DTC clearing / ECU coding surface.
        require(command.matches(Regex("01[0-9A-F]{2}")) || command in setOf("ATZ", "ATE0", "ATL0", "ATS1", "ATH1", "ATSP0", "ATAT1", "ATST64", "ATRV"))
        try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.IO) {
                    val live = socket ?: throw IOException("Nincs Bluetooth-kapcsolat")
                    val input = live.inputStream
                    // A dedicated worker covers blocking vendor socket writes and reads. Cancellation
                    // closes the connection before a late response can be mistaken for another PID.
                    suspendCancellableCoroutine { continuation ->
                        continuation.invokeOnCancellation { runCatching { live.close() } }
                        Thread({
                            try {
                                live.outputStream.write((command + "\r").toByteArray(Charsets.US_ASCII))
                                live.outputStream.flush()
                                val result = StringBuilder()
                                while (true) {
                                    val b = input.read()
                                    if (b < 0) throw IOException("Az adapter bontotta a kapcsolatot")
                                    if (b == '>'.code) break
                                    result.append(b.toChar())
                                    if (result.length > 16_384) throw IOException("ELM válasz túl hosszú")
                                }
                                continuation.resume(result.toString())
                            } catch (e: Exception) { continuation.resumeWithException(e) }
                        }, "elm-command").start()
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            close()
            throw IOException("ELM időtúllépés ($command)", e)
        } catch (e: Exception) { close(); throw e }
    }

    override fun close() { val old = socket; socket = null; runCatching { old?.close() } }
}
