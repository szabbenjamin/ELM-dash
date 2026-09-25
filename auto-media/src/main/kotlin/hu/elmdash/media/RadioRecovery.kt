package hu.elmdash.media

import kotlinx.coroutines.*

/** Main-thread playback intent. Player errors never erase the user's wish to keep listening. */
internal class RadioRecovery(
    private val scope: CoroutineScope,
    initiallyOnline: Boolean,
    private val connect: () -> Unit,
    private val changed: () -> Unit
) {
    var wanted = false; private set
    var waiting = false; private set
    var error: String? = null; private set
    private var online = initiallyOnline
    private var playing = false
    private var suppressed = false
    private var attempts = 0
    private var retry: Job? = null
    private var stall: Job? = null
    val message: String? get() = when {
        !wanted -> null
        suppressed -> "Más hang miatt átmenetileg szünetel"
        waiting && !online -> "Nincs internet • a rádió automatikusan folytatódik"
        waiting -> "Megszakadt az adás • automatikus újracsatlakozás…"
        else -> null
    }

    fun start() {
        cancelJobs(); attempts = 0; playing = false; wanted = true; waiting = false; error = null; suppressed = false
        open()
    }
    fun pause() {
        wanted = false; waiting = false; error = null; playing = false
        cancelJobs(); changed()
    }
    fun failure(retryable: Boolean) {
        if (!wanted) return
        playing = false; stall?.cancel(); stall = null
        if (!retryable) {
            wanted = false; waiting = false; cancelJobs()
            error = "Ez az adás nem játszható le. Válassz másik állomást, vagy ellenőrizd a streamcímet."
        } else {
            waiting = true
            scheduleRetry()
        }
        changed()
    }
    fun networkChanged(available: Boolean) {
        if (online == available) return
        online = available
        if (!wanted || playing) return
        if (!online) {
            retry?.cancel(); retry = null; stall?.cancel(); stall = null; waiting = true
        } else {
            retry?.cancel(); retry = null
            if (!suppressed) scheduleRetry(immediate = true)
        }
        changed()
    }
    fun playerState(isPlaying: Boolean, buffering: Boolean, focusSuppressed: Boolean, ended: Boolean = false) {
        playing = isPlaying; suppressed = focusSuppressed
        if (!wanted) return
        when {
            isPlaying -> { waiting = false; attempts = 0; cancelJobs() }
            suppressed -> { retry?.cancel(); retry = null; stall?.cancel(); stall = null }
            ended -> { failure(true); return }
            !online -> { waiting = true; cancelJobs() }
            waiting -> scheduleRetry()
            buffering && stall?.isActive != true -> {
                stall = scope.launch {
                    delay(30_000)
                    stall = null
                    failure(true) // A server can hang forever without sending an explicit error.
                }
            }
            !buffering -> { stall?.cancel(); stall = null }
        }
        changed()
    }
    private fun open() {
        if (!wanted || suppressed) return
        waiting = !online
        if (online) {
            try { connect() } catch (_: Exception) { failure(false) }
        }
        changed()
    }
    private fun scheduleRetry(immediate: Boolean = false) {
        if (!wanted || !online || suppressed || retry?.isActive == true) return
        val delayMs = if (immediate) 0L else longArrayOf(2_000, 5_000, 10_000, 20_000, 30_000)[attempts.coerceAtMost(4)]
        if (!immediate) attempts++
        retry = scope.launch {
            delay(delayMs)
            retry = null
            stall?.cancel(); stall = null
            if (wanted && online && !suppressed) open()
        }
    }
    private fun cancelJobs() { retry?.cancel(); retry = null; stall?.cancel(); stall = null }
    fun close() { wanted = false; cancelJobs() }
}
