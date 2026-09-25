package hu.elmdash.media

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.*

internal object AutoRadio {
    private var session: AaRadioSession? = null
    internal var service: DashboardMediaService? = null
    val connected: Boolean get() = session?.connected == true
    fun connectionChanged(context: Context, connected: Boolean) {
        val app = context.applicationContext
        val radio = RadioGraph.get(app)
        if (session == null) session = AaRadioSession(
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            { radio.state.value.autoPlayOnAa }, { radio.pausedForAa }, { radio.pausedForAa = it },
            start = {
                if (service?.wantsPlayback == true) true // Preserve playback started before entering the car.
                else runCatching {
                    app.startForegroundService(Intent(app, DashboardMediaService::class.java).setAction(DashboardMediaService.ACTION_AUTO_PLAY))
                    true
                }.getOrElse {
                    radio.status(false, false, "Az automatikus rádióindítást az Android blokkolta. Ellenőrizd a háttérengedélyt a Kapcsolat lapon.")
                    false
                }
            },
            end = { service?.endCarPlayback() }
        )
        session!!.connectionChanged(connected)
    }
    fun manualPause() { session?.manualPause() }
    fun explicitPlay() { session?.explicitPlay() }
}
