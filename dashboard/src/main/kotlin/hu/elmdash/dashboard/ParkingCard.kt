package hu.elmdash.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import hu.elmdash.connection.ParkingStore
import kotlinx.coroutines.delay

@SuppressLint("MissingPermission", "SetJavaScriptEnabled")
@Composable
internal fun ParkingCard(previewRequested: Boolean = false) {
    var preview by remember { mutableStateOf(previewRequested) }
    var previewCar by remember { mutableStateOf<hu.elmdash.connection.ParkedCar?>(null) }
    val bearing = remember { kotlin.random.Random.nextDouble(0.0, 2.0 * Math.PI) }
    LaunchedEffect(preview) { if (preview) { delay(600_000); preview = false } }
    val context = LocalContext.current
    val store = remember { ParkingStore(context) }
    var car by remember { mutableStateOf(store.load()) }
    var current by remember { mutableStateOf<Location?>(null) }
    var permission by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    var backgroundAllowed by remember { mutableStateOf(hu.elmdash.connection.RouteGps.allowed(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(permission, lifecycle, preview) {
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { if (location.hasAccuracy()) current = Location(location) }
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) { current = null }
            @Deprecated("Legacy") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        fun sync() {
            manager.removeUpdates(listener)
            current = null
            permission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            backgroundAllowed = hu.elmdash.connection.RouteGps.allowed(context)
            if (permission && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                runCatching {
                    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5_000, 0f, listener, Looper.getMainLooper())
                    if (preview) manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5_000, 0f, listener, Looper.getMainLooper())
                }
        }
        val observer = LifecycleEventObserver { _, _ -> sync() }
        lifecycle.addObserver(observer); sync()
        onDispose { lifecycle.removeObserver(observer); manager.removeUpdates(listener) }
    }
    LaunchedEffect(Unit) { while (true) { car = store.load(); delay(2_000) } }
    val fix = current?.takeIf { SystemClock.elapsedRealtime() - it.elapsedRealtimeNanos / 1_000_000 in 0..20_000 && it.accuracy <= if (preview) 500 else 25 }
    LaunchedEffect(fix, preview) {
        if (preview && previewCar == null && fix != null) {
            val angular = 2500.0 / 6371000.0
            val lat = Math.toRadians(fix.latitude)
            val lon = Math.toRadians(fix.longitude)
            val targetLat = kotlin.math.asin(kotlin.math.sin(lat) * kotlin.math.cos(angular) + kotlin.math.cos(lat) * kotlin.math.sin(angular) * kotlin.math.cos(bearing))
            val targetLon = lon + kotlin.math.atan2(kotlin.math.sin(bearing) * kotlin.math.sin(angular) * kotlin.math.cos(lat), kotlin.math.cos(angular) - kotlin.math.sin(lat) * kotlin.math.sin(targetLat))
            previewCar = hu.elmdash.connection.ParkedCar(Math.toDegrees(targetLat), (Math.toDegrees(targetLon) + 540) % 360 - 180, 5f, System.currentTimeMillis(), true)
        }
    }
    val displayedCar = if (preview) previewCar else car
    val parked = displayedCar?.takeIf { it.confirmed || System.currentTimeMillis() - it.atMs >= 120_000 }
    val away = parked != null && fix != null && fix.distanceTo(parked.location()) - fix.accuracy - parked.accuracy > 50
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hol hagytam az autót?")
            if (preview) {
                Text("TESZT · az autó helye kitalált, kb. 2,5 km-re. 10 perc után megszűnik.", color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { preview = false }) { Text("Előnézet bezárása") }
            }
            if (!permission) {
                TextButton(onClick = { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Helyengedély megadása") }
            }
            if (permission && !backgroundAllowed) {
                TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Háttérhely engedélyezése · Mindig") }
            }
            run {
                Text(if (!permission) "Pontos helyengedély szükséges."
                    else if (preview && previewCar == null) "Előnézet: friss helyzetre várunk…"
                    else if (displayedCar == null) "Automatikus parkolóhely-mentés motorleálláskor. Háttérben a pontos és Mindig helyengedély szükséges."
                    else if (parked == null) "Motor leállt; 2 percig várunk az újraindításra."
                    else "Utolsó parkolás: ±${parked.accuracy.toInt()} m. " + if (fix == null) "Friss GPS-jelre vár." else "Kb. ${fix.distanceTo(parked.location()).toInt()} m-re.", style = MaterialTheme.typography.bodySmall)
                if (away && parked != null && fix != null) {
                    val html = remember(parked) {
                        val js = context.assets.open("leaflet/leaflet.js").bufferedReader().use { it.readText() }
                        val css = context.assets.open("leaflet/leaflet.css").bufferedReader().use { it.readText() }
                        """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>$css html,body{margin:0}#map{width:100%;height:220px}</style></head><body><div id="map"></div><script>$js</script><script>const p=[${parked.latitude},${parked.longitude}];const m=L.map('map').setView(p,17);L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{attribution:'© OpenStreetMap contributors',maxZoom:19}).addTo(m);L.circleMarker(p,{radius:10}).addTo(m).bindPopup('Autó').openPopup();L.circle(p,{radius:${parked.accuracy}}).addTo(m);new ResizeObserver(()=>m.invalidateSize()).observe(document.getElementById('map'));</script></body></html>"""
                    }
                    key(parked) { AndroidView(modifier = Modifier.fillMaxWidth().height(220.dp), factory = { ctx -> WebView(ctx).apply {
                        settings.javaScriptEnabled = true; settings.allowFileAccess = false; settings.allowContentAccess = false
                        settings.userAgentString += " ELM-Dash/0.17 (+https://github.com/szabbenjamin/ELM-dash)"
                        webViewClient = WebViewClient()
                        loadDataWithBaseURL("https://appassets.androidplatform.net/parking/", html, "text/html", "UTF-8", null)
                    } }, onRelease = { it.destroy() }) }
                    Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                        val navigation = Intent(Intent.ACTION_VIEW,
                            Uri.parse("google.navigation:q=${parked.latitude},${parked.longitude}&mode=w"))
                            .setPackage("com.google.android.apps.maps")
                        try {
                            context.startActivity(navigation)
                        } catch (_: android.content.ActivityNotFoundException) {
                            android.widget.Toast.makeText(context, "A Google Maps alkalmazás nem érhető el. Telepítsd vagy engedélyezd a gyalogos navigációhoz.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }) { Text("Navigálás az autóhoz · gyalog") }
                }
                Text("A parkolóhely helyben marad. A térkép az OSM-től tölt csempéket; a gyalogos navigációt a Google Maps alkalmazás indítja az aktuális helyedtől az autóhoz.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
