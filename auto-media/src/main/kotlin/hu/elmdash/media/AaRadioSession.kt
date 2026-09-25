package hu.elmdash.media

import kotlinx.coroutines.*

/** AA edges, five-second disconnect grace, and manual pause priority; no Android dependency. */
internal class AaRadioSession(
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
    private val paused: () -> Boolean,
    private val savePaused: (Boolean) -> Unit,
    private val start: () -> Boolean,
    private val end: () -> Unit
) {
    var connected = false; private set
    private var observed = false
    private var handled = false
    private var disconnect: Job? = null
    fun connectionChanged(value: Boolean) {
        if (observed && connected == value) return
        disconnect?.cancel(); disconnect = null
        connected = value
        if (value) {
            if (!handled && enabled() && !paused()) handled = start()
        } else if (!observed) {
            savePaused(false)
        } else {
            disconnect = scope.launch {
                delay(5_000)
                handled = false; savePaused(false); end()
                disconnect = null
            }
        }
        observed = true
    }
    fun manualPause() { savePaused(connected || disconnect?.isActive == true) }
    fun explicitPlay() { savePaused(false) }
}
