package hu.elmdash.media

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal object RadioErrors {
    fun retryable(error: PlaybackException): Boolean {
        var cause: Throwable? = error
        repeat(12) {
            val current = cause ?: return@repeat
            if (current is HttpDataSource.InvalidResponseCodeException) {
                return current.responseCode == 408 || current.responseCode == 429 || current.responseCode in 500..599
            }
            cause = current.cause
        }
        return error.errorCode in setOf(PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)
    }
}
