package hu.elmdash.connection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One process-wide coordinator; radio browsing alone is not proof of projection. */
object AutoObd {
    private val mutableProjection = MutableStateFlow(false)
    val projection = mutableProjection.asStateFlow()
    private var application: Context? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingDisconnect: Runnable? = null
    private var requested = false
    var automaticRunning: Boolean = false
        internal set
    fun install(context: Context) { application = context.applicationContext }

    fun projectionChanged(connected: Boolean) {
        val context = application ?: return
        pendingDisconnect?.let { handler.removeCallbacks(it) }
        pendingDisconnect = null
        val before = mutableProjection.value
        mutableProjection.value = connected
        if (!connected) {
            SessionStore(context).autoPaused = false
            if (before && automaticRunning) {
                // Keep foreground collection alive through the OBD trip-end grace period.
                // Reprojection cancels this shutdown; the radio has its own shorter timeout.
                pendingDisconnect = Runnable {
                    if (!projection.value && automaticRunning) context.stopService(Intent(context, ObdService::class.java))
                }.also { handler.postDelayed(it, hu.elmdash.trip.JOURNEY_DISCONNECT_MS + 2_000) }
            }
        } else if (!before) request(context)
    }

    fun request(context: Context, headUnitWake: Boolean = false) {
        val c = DashboardGraph.get(context)
        val store = c.store
        if (!AutoObdPolicy.canStart(store.autoConnect, store.autoPaused, projection.value || headUnitWake,
                c.state.value.active, requested)) return
        if (!c.hasBluetoothPermission() || store.address.isBlank()) {
            val message = if (store.address.isBlank()) "Válassz és ments egy OBD-adaptert a Kapcsolat lapon." else "Engedélyezd a Közeli eszközök hozzáférést a telefonon."
            c.error("Nincs kapcsolat az autóval. $message")
            alert(context, message)
            return
        }
        try {
            requested = true
            context.startForegroundService(Intent(context, ObdService::class.java)
                .setAction(ObdService.ACTION_AUTO).putExtra("headUnitWake", headUnitWake))
        } catch (_: Exception) {
            requested = false
            c.error("Nincs kapcsolat az autóval. Az Android blokkolta a háttérindítást; engedélyezd az automatikus háttérkapcsolatot a Kapcsolat lapon.")
            alert(context, "Az Android blokkolta a háttérindítást. Nyisd meg a Kapcsolat lap háttérengedélyét.")
        }
    }

    fun released() { requested = false }
    fun pause(context: Context) {
        SessionStore(context).autoPaused = projection.value || automaticRunning
        requested = false
    }
    fun resume(context: Context) { SessionStore(context).autoPaused = false; request(context) }
    fun backgroundAllowed(context: Context): Boolean = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun alert(context: Context, detail: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("obd-alert", "Nincs kapcsolat az autóval", NotificationManager.IMPORTANCE_DEFAULT))
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val open = PendingIntent.getActivity(context, 73, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching { manager.notify(329, NotificationCompat.Builder(context, "obd-alert")
            .setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle("Nincs kapcsolat az autóval")
            .setContentText(detail).setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build()) }
    }
    fun clearAlert(context: Context) { context.getSystemService(NotificationManager::class.java).cancel(329) }
}

internal object AutoObdPolicy {
    fun canStart(enabled: Boolean, paused: Boolean, carPresent: Boolean, active: Boolean, requested: Boolean) =
        enabled && !paused && carPresent && !active && !requested
}

/** ACL broadcasts can wake a stopped process. Only the explicitly saved head unit/adapter matches. */
class CarBluetoothReceiver : BroadcastReceiver() {
    @android.annotation.SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED)) return
        val store = SessionStore(context)
        if (!store.autoConnect || !DashboardGraph.get(context).hasBluetoothPermission()) return
        val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else @Suppress("DEPRECATION") (intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice)
        val address = device?.address ?: return
        if (address == store.headUnitAddress && address.isNotBlank()) {
            if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) AutoObd.request(context, headUnitWake = true)
            else if (!AutoObd.projection.value) store.autoPaused = false // Projection/Wi-Fi can remain active.
        } else if (address == store.address && intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            AutoObd.request(context) // An unrelated Bluetooth device never starts OBD collection.
        }
    }
}
