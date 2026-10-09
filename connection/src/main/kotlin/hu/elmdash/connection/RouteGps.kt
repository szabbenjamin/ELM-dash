package hu.elmdash.connection

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock

/** A fresh GNSS request every minute, with a 20-second fix timeout. Never substitutes lastKnownLocation. */
class RouteGps(private val context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var requestedAt: Long? = null
    private var lastRequest: Long? = null
    var location: Location? = null; private set
    var status = "GPS nincs elindítva"; private set
    private val listener = object : LocationListener {
        override fun onLocationChanged(value: Location) {
            val age = SystemClock.elapsedRealtime() - value.elapsedRealtimeNanos / 1_000_000
            if (age !in 0..20_000 || !value.hasAccuracy()) return
            location = Location(value); status = "GPS-pont rögzítve"
            cancelRequest()
        }
        override fun onProviderDisabled(provider: String) { status = "GPS kikapcsolva" }
        override fun onProviderEnabled(provider: String) = Unit
        @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    @SuppressLint("MissingPermission")
    fun tick(running: Boolean, now: Long) {
        if (!running) { stop(); return }
        if (!allowed(context)) { status = "GPS-hez pontos és háttérbeli helyengedély szükséges"; cancelRequest(); location = null; return }
        if (requestedAt?.let { now - it >= 20_000 } == true) { cancelRequest(); status = "Nincs friss GPS-jel" }
        if (lastRequest?.let { now - it < 60_000 } == true) return
        lastRequest = now
        try {
            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { status = "GPS kikapcsolva"; return }
            requestedAt = now; status = "GPS-helyzet lekérése…"
            manager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
        } catch (_: SecurityException) { status = "GPS-engedély hiányzik"; cancelRequest() }
        catch (_: IllegalArgumentException) { status = "GPS nem érhető el"; cancelRequest() }
    }
    private fun cancelRequest() { runCatching { manager.removeUpdates(listener) }; requestedAt = null }
    fun stop() { cancelRequest(); lastRequest = null; location = null; status = "GPS csak járó motornál rögzít" }
    companion object {
        fun allowed(context: Context) = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < 29 || context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
}
