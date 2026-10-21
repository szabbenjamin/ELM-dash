package hu.elmdash.connection

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import hu.elmdash.traffic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit

object TrafficRuntime {
    private val mutable = MutableStateFlow("Útinform kikapcsolva")
    val status = mutable.asStateFlow()
    var line: String? = null; internal set
    var aaConnected = false; private set
    internal fun status(value: String) { mutable.value = value }
    fun connectionChanged(context: Context, connected: Boolean) {
        if (aaConnected == connected) return
        aaConnected = connected
        if (connected) start(context, background = true) else stop(context)
    }
    fun start(context: Context, background: Boolean = false) {
        val config = runCatching { TrafficSettings(context).load() }.getOrElse { status("A NAP-link nem olvasható; mentsd újra."); return }
        if (!config.enabled) return
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            background && !RouteGps.allowed(context)) {
            status("Útinform: pontos helyengedély kell; automatikus induláshoz Mindig engedélyezett."); return
        }
        runCatching { context.startForegroundService(Intent(context, TrafficService::class.java)) }
            .onFailure { status("Az Android blokkolta az indulást. Indítsd a telefon Útinform oldaláról.") }
    }
    fun stop(context: Context) {
        context.stopService(Intent(context, TrafficService::class.java)); line = null; status("Útinform figyelés leállítva")
    }
}

/** Separate location FGS: no OBD dependency, no GPS persistence, no coordinate upload. */
class TrafficService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager by lazy { getSystemService(LocationManager::class.java) }
    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private var call: okhttp3.Call? = null
    private var job: Job? = null
    private var fix: TrafficFix? = null
    private var feed: TrafficFeed? = null
    private var receivedAt: Long? = null
    private var sourceStatus = "Adatlekérés…"
    private var presenter = TrafficPresenter()
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!location.hasAccuracy()) return
            fix = runCatching { TrafficFix(GeoPoint(location.latitude, location.longitude), location.accuracy.toDouble(),
                if (location.hasBearing() && (!location.hasBearingAccuracy() || location.bearingAccuracyDegrees <= 35f)) location.bearing.toDouble() else null,
                if (location.hasSpeed()) location.speed.toDouble() else 0.0, location.elapsedRealtimeNanos / 1_000_000) }.getOrNull()
        }
        override fun onProviderDisabled(provider: String) { fix = null; TrafficRuntime.line = null }
        override fun onProviderEnabled(provider: String) = Unit
        @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    override fun onBind(intent: Intent?) = null
    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { TrafficRuntime.stop(this); return START_NOT_STICKY }
        try {
            val config = TrafficSettings(this).load().also { it.validate() }
            if (!config.enabled) { stopSelf(); return START_NOT_STICKY }
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("traffic", "Útinform helyfigyelés", NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 0, Intent(this, TrafficService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, "traffic").setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Útinform helyfigyelés").setContentText("Közeli események · helyadatok mentése nélkül")
                .setOngoing(true).addAction(0, "Leállítás", stop).build()
            ServiceCompat.startForeground(this, 329, notification, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
            job?.cancel(); call?.cancel(); manager.removeUpdates(listener)
            feed = null; receivedAt = null; fix = null; presenter = TrafficPresenter(); TrafficRuntime.line = null
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3_000, 0f, listener, Looper.getMainLooper())
            job = scope.launch {
                launch {
                    while (isActive) {
                        fetch(config.url)
                        delay(300_000)
                    }
                }
                launch {
                    while (isActive) {
                        evaluate(); delay(2_000)
                    }
                }
                // A manually started preview must not run indefinitely after leaving settings.
                delay(30 * 60_000L)
                while (TrafficRuntime.aaConnected) delay(60_000)
                TrafficRuntime.status("A kézi Útinform-próba véget ért")
                stopSelf()
            }
        } catch (_: Exception) {
            TrafficRuntime.status("Útinform nem indítható: ellenőrizd a linket és a helyengedélyt.")
            stopSelf()
        }
        return START_NOT_STICKY
    }
    private class FeedSizeException : Exception()
    private suspend fun fetch(url: String) {
        try {
            val parsed = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).header("Accept", "application/xml, text/xml").build()
                val active = client.newCall(request); call = active
                active.execute().use { response ->
                    if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
                    val body = checkNotNull(response.body)
                    if (body.contentLength() > DatexParser.MAX_BYTES) throw FeedSizeException()
                    val source = body.source()
                    if (source.request(DatexParser.MAX_BYTES.toLong() + 1)) throw FeedSizeException()
                    DatexParser.parse(source.readByteArray())
                }
            }
            feed = parsed; receivedAt = SystemClock.elapsedRealtime()
            sourceStatus = "${parsed.events.size} esemény; ${parsed.skipped} kihagyva (nem támogatott/hely nélküli/inaktív)"
        } catch (e: CancellationException) { throw e }
        catch (_: FeedSizeException) { sourceStatus = "A NAP-adat meghaladja a 24 MiB méretkorlátot." }
        catch (_: java.net.SocketTimeoutException) { sourceStatus = "A NAP-lekérés időtúllépés miatt megszakadt." }
        catch (_: javax.net.ssl.SSLException) { sourceStatus = "A NAP biztonságos kapcsolata nem hozható létre." }
        catch (e: java.io.IOException) {
            // Only our numeric HTTP status is safe to expose; network messages may contain the secret URL.
            sourceStatus = if (e.message?.matches(Regex("HTTP [0-9]{3}")) == true)
                "NAP: ${e.message}. Ellenőrizd a hozzáférést és a szolgáltatás állapotát."
            else "A NAP nem érhető el. Ellenőrizd az internetkapcsolatot."
        }
        catch (_: Exception) { sourceStatus = "A NAP válasza nem támogatott vagy hibás DATEX II XML." }
    }
    private fun evaluate() {
        val elapsed = SystemClock.elapsedRealtime()
        val now = Instant.now()
        val current = fix
        val data = feed
        val fresh = data != null && receivedAt?.let { elapsed - it in 0..900_000 } == true &&
            now.toEpochMilli() - data.published.toEpochMilli() in -120_000..900_000
        val gpsFresh = current != null && elapsed - current.elapsedMs in 0..20_000 && current.accuracy <= 100
        val matches = if (fresh && gpsFresh) TrafficFilter.nearby(data!!, current!!, now, elapsed) else emptyList()
        TrafficRuntime.line = presenter.line(matches, elapsed)
        TrafficRuntime.status(when {
            !fresh -> "$sourceStatus · Nincs friss Útinform-adat"
            !gpsFresh -> "$sourceStatus · Pontos GPS-jelre vár"
            else -> "$sourceStatus · ${matches.size} közeli találat · forrás ${(now.toEpochMilli() - data!!.published.toEpochMilli()).coerceAtLeast(0) / 60_000} perce · térképi útillesztés nélkül"
        })
    }
    override fun onDestroy() {
        job?.cancel(); call?.cancel(); scope.cancel()
        runCatching { manager.removeUpdates(listener) }
        fix = null; feed = null; TrafficRuntime.line = null
        super.onDestroy()
    }
}
