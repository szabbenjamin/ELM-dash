package hu.elmdash.connection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.round

class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val store = MaintenanceStore(context)
        val today = LocalDate.now()
        val due = store.latest().filter { it.soon(today,store.odometer) }
        if (due.isNotEmpty()) notify("service", "Szervizemlékeztető", due.joinToString(" • ") { it.name + if (it.due(today,store.odometer)) ": esedékes" else ": hamarosan" })
        val prefs = context.getSharedPreferences("elm-maintenance",Context.MODE_PRIVATE)
        if (prefs.getBoolean("weather",false)) {
            val car = ParkingStore(context).load()
            if (car != null && System.currentTimeMillis() - car.atMs in 0..7 * 86_400_000L) runCatching {
                // Coarse ~1 km coordinates; never the receipt, OBD data or full route.
                val lat = round(car.latitude * 100) / 100; val lon = round(car.longitude * 100) / 100
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m&timezone=UTC"
                OkHttpClient.Builder().callTimeout(20,TimeUnit.SECONDS).build().newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful)
                    val body = checkNotNull(response.body)
                    check(!body.source().request(64 * 1024 + 1L))
                    val current = JSONObject(body.string()).getJSONObject("current")
                    val temperature = current.getDouble("temperature_2m")
                    check(temperature.isFinite() && temperature in -60.0..60.0)
                    val time = java.time.LocalDateTime.parse(current.getString("time")).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
                    check(System.currentTimeMillis() - time in -120_000..7_200_000)
                    val previous = prefs.getString("temperature",null)?.toDoubleOrNull()
                    val previousAt = prefs.getLong("temperatureAt",0)
                    val cooled = temperature <= 7 || (previous != null && System.currentTimeMillis()-previousAt < 7*86_400_000L && previous-temperature >= 10)
                    if (cooled && System.currentTimeMillis()-prefs.getLong("tyreAlert",0) >= 7*86_400_000L) {
                        if (notify("tyres", "Guminyomás ellenőrzése", "Hűvös idő a legutóbbi parkolóhely környékén (${temperature.toInt()} °C, Open-Meteo). Ellenőrizd hideg gumiknál a gyári előírás szerinti nyomást."))
                            prefs.edit().putLong("tyreAlert",System.currentTimeMillis()).apply()
                    }
                    prefs.edit().putString("temperature",temperature.toString()).putLong("temperatureAt",System.currentTimeMillis()).apply()
                }
            }
        }
        Result.success()
    }
    private fun notify(tag: String, title: String, text: String): Boolean {
        val context = applicationContext
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("maintenance","Szerviz és guminyomás",NotificationManager.IMPORTANCE_DEFAULT))
        if (!manager.areNotificationsEnabled() || manager.getNotificationChannel("maintenance").importance == NotificationManager.IMPORTANCE_NONE) return false
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return false
        return runCatching {
            manager.notify(tag,335,NotificationCompat.Builder(context,"maintenance").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setOnlyAlertOnce(true).setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(context,335,open,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build())
            true
        }.getOrDefault(false)
    }
    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("maintenance",ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MaintenanceWorker>(24,TimeUnit.HOURS).build())
        }
    }
}
