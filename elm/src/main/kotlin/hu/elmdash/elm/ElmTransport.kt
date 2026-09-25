package hu.elmdash.elm

/** One ASCII command and its complete, prompt-terminated response. No concurrent wire access. */
interface ElmTransport {
    suspend fun connect()
    suspend fun exchange(command: String, timeoutMs: Long = 4_000): String
    fun close()
}
