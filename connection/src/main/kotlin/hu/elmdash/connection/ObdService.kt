package hu.elmdash.connection

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class ObdService : Service() {
    private val controller by lazy { DashboardGraph.get(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var observer: Job? = null
    private var initialWindow: Job? = null
    private var disconnectAlerted = false

    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AutoObd.pause(this); controller.stopLiveOnly(); stopSelf(); return START_NOT_STICKY
        }
        val automatic = intent?.action == ACTION_AUTO
        if (automatic && controller.state.value.active) { AutoObd.released(); return START_NOT_STICKY }
        if (!controller.hasBluetoothPermission()) {
            controller.error("Nincs kapcsolat az autóval. Hiányzik a Közeli eszközök engedély.")
            AutoObd.alert(this, controller.state.value.message); stopSelf(); return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("obd", "OBD-kapcsolat", NotificationManager.IMPORTANCE_LOW))
            ServiceCompat.startForeground(this, 327, notification("Kapcsolódás a mentett OBD-adapterhez…"),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
            AutoObd.automaticRunning = automatic
            if (!automatic) controller.store.autoPaused = false
            if (wakeLock == null) {
                wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "elmdash:obd").apply { setReferenceCounted(false); acquire(4 * 60 * 60 * 1000L) }
                scope.launch {
                    delay(4 * 60 * 60 * 1000L)
                    if (!AutoObd.automaticRunning) {
                        AutoObd.alert(this@ObdService, "A négyórás kézi mérési időkeret véget ért.")
                        controller.stopLiveOnly(); stopSelf()
                    }
                }
            }
            // The automatic session follows AA lifetime, including journeys longer than four hours.
            // A bounded, non-reference-counted lock is renewed only while projection is confirmed.
            if (automatic && observer == null) scope.launch {
                while (isActive) {
                    delay(30 * 60 * 1000L)
                    if (AutoObd.automaticRunning && AutoObd.projection.value) wakeLock?.acquire(60 * 60 * 1000L)
                }
            }
            initialWindow?.cancel()
            if (automatic && !AutoObd.projection.value) {
                // The head unit's Bluetooth link often appears before wireless projection.
                initialWindow = scope.launch {
                    delay(120_000)
                    if (!AutoObd.projection.value) stopSelf()
                }
            }
            controller.startLive(intent?.getStringExtra("address") ?: controller.store.address,
                if (intent?.hasExtra("insecure") == true) intent.getBooleanExtra("insecure", false) else controller.store.insecure,
                retryForever = automatic)
            observer?.cancel()
            observer = scope.launch {
                var lastPhase: Phase? = null
                controller.state.collectLatest { state ->
                    if (state.phase != lastPhase) {
                        lastPhase = state.phase
                        val text = when (state.phase) {
                            Phase.LIVE -> "Kapcsolódva az autóhoz • OBD-adatolvasás folyamatban"
                            Phase.CONNECTING, Phase.DISCOVERING -> "Nincs kapcsolat az autóval • kapcsolódás…"
                            Phase.RETRY -> "Nincs kapcsolat az autóval • automatikus újrapróbálkozás"
                            else -> "Nincs kapcsolat az autóval"
                        }
                        manager.notify(327, notification(text))
                        if (state.phase == Phase.LIVE) { disconnectAlerted = false; AutoObd.clearAlert(this@ObdService) }
                        if (state.phase in setOf(Phase.RETRY, Phase.ERROR) && !disconnectAlerted) {
                            disconnectAlerted = true
                            AutoObd.alert(this@ObdService, if (automatic) "A mentett OBD-adapter vagy az ECU nem érhető el. Bekapcsolt gyújtás mellett automatikusan újrapróbálkozom."
                                else state.message)
                        }
                    }
                    if (state.phase == Phase.ERROR && state.journal.active == null) stopSelf()
                }
            }
        } catch (e: Exception) {
            controller.error("Nincs kapcsolat az autóval. ${e.message ?: "A háttérkapcsolat nem indítható"}")
            AutoObd.alert(this, controller.state.value.message); stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)!!
        val stop = PendingIntent.getService(this, 1, Intent(this, ObdService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, "obd").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("ELM Dash • OBD-kapcsolat").setContentText(text)
            .setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setOngoing(true).setOnlyAlertOnce(true).addAction(0, "Leállítás", stop).build()
    }

    override fun onDestroy() {
        scope.cancel()
        if (controller.state.value.phase != Phase.ERROR) controller.stopLiveOnly()
        AutoObd.automaticRunning = false
        AutoObd.released()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }
    companion object {
        const val ACTION_STOP = "hu.elmdash.STOP"
        const val ACTION_AUTO = "hu.elmdash.AUTO_CONNECT"
    }
}
