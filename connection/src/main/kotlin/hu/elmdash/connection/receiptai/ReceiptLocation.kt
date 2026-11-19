package hu.elmdash.connection.receiptai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/** One-shot location only when a receipt is explicitly submitted; no permission escalation. */
object ReceiptLocation {
    fun usable(location: Location, now: Long): Boolean =
        location.hasAccuracy() && location.accuracy <= 150f &&
            (now-location.elapsedRealtimeNanos/1_000_000) in 0..300_000
    suspend fun context(context: Context): String? {
        if(listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION).none {
            ContextCompat.checkSelfPermission(context,it)==PackageManager.PERMISSION_GRANTED
        }) return null
        val manager=context.getSystemService(LocationManager::class.java)
        val fix=try {
            val fresh=if(Build.VERSION.SDK_INT>=30) withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine<Location?> { continuation ->
                    val signal=CancellationSignal()
                    continuation.invokeOnCancellation { signal.cancel() }
                    val provider=if(manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                    manager.getCurrentLocation(provider,signal,context.mainExecutor) { location ->
                        if(continuation.isActive) continuation.resume(location)
                    }
                }
            } else null
            fresh ?: listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).mapNotNull {
                runCatching { manager.getLastKnownLocation(it) }.getOrNull()
            }.maxByOrNull { it.elapsedRealtimeNanos }
        } catch(e:CancellationException) { throw e } catch(_:Exception) { null }
        fix ?: return null
        if(!usable(fix,SystemClock.elapsedRealtime())) return null
        val address=withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                val match=Geocoder(context,java.util.Locale.forLanguageTag("hu-HU")).getFromLocation(fix.latitude,fix.longitude,1)?.firstOrNull()
                match?.getAddressLine(0)?.take(300)
            }.getOrNull()
        }
        return "Jelenlegi telefonhely (nem feltétlenül a tankolás helye): ${fix.latitude}, ${fix.longitude}; pontosság: ${fix.accuracy.toInt()} m. Helyszín címe: ${address ?: "ismeretlen"}. A blokk neve/címe/dátuma az elsődleges bizonyíték."
    }
}
