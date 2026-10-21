package hu.elmdash.connection

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import hu.elmdash.obd.Pid
import org.json.JSONObject

data class ParkedCar(val latitude: Double, val longitude: Double, val accuracy: Float, val atMs: Long, val confirmed: Boolean) {
    fun location() = Location("parking").also { it.latitude = latitude; it.longitude = longitude; it.accuracy = accuracy }
}
class ParkingStore(context: Context) {
    private val prefs = context.getSharedPreferences("elm-parking", Context.MODE_PRIVATE)
    fun load(): ParkedCar? = runCatching {
        val j = JSONObject(prefs.getString("point", null) ?: return null)
        ParkedCar(j.getDouble("lat"), j.getDouble("lon"), j.getDouble("accuracy").toFloat(), j.getLong("at"), j.getBoolean("confirmed"))
    }.getOrNull()
    fun save(p: ParkedCar) { prefs.edit().putString("point", JSONObject().put("lat",p.latitude).put("lon",p.longitude).put("accuracy",p.accuracy).put("at",p.atMs).put("confirmed",p.confirmed).toString()).apply() }
    fun clear() { prefs.edit().remove("point").apply() }
}
/** Continuous fixes only while the OBD foreground service runs. Engine-off freezes the last fresh fix. */
class ParkingTracker(context: Context, private val elapsed: () -> Long = SystemClock::elapsedRealtime, private val wall: () -> Long = System::currentTimeMillis) {
    private val store = ParkingStore(context)
    private val manager = context.getSystemService(LocationManager::class.java)
    private var listening = false
    private var wasRunning = false
    private var stoppedAt: Long? = null
    private var last: Location? = null
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.hasAccuracy() && location.accuracy <= 25 && elapsed() - location.elapsedRealtimeNanos / 1_000_000 in 0..10_000) last = Location(location)
        }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) { last = null }
        @Deprecated("Legacy") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    @SuppressLint("MissingPermission")
    fun tick(state: DashboardState, locationAllowed: Boolean) {
        if (state.simulated || !locationAllowed) { stop(); return }
        if (!listening) {
            runCatching { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000, 0f, listener, Looper.getMainLooper()); listening = true }
        }
        val now = elapsed()
        val rpm = state.telemetry.value(Pid.RPM, now)
        if (rpm != null && rpm > 0) {
            if (!wasRunning || stoppedAt != null) store.clear()
            wasRunning = true; stoppedAt = null
        } else if (rpm == 0.0 && wasRunning && stoppedAt == null) {
            stoppedAt = now
            val fix = last?.takeIf { now - it.elapsedRealtimeNanos / 1_000_000 in 0..10_000 }
            if (fix != null) store.save(ParkedCar(fix.latitude, fix.longitude, fix.accuracy, wall(), false))
            // No fix: do not substitute the phone's location after the driver has walked away.
        }
        if (stoppedAt?.let { now - it >= 120_000 } == true) {
            store.load()?.takeIf { !it.confirmed }?.let { store.save(it.copy(confirmed = true)) }
            wasRunning = false; stoppedAt = null
        }
    }
    fun stop() { if (listening) manager.removeUpdates(listener); listening = false; last = null }
}
