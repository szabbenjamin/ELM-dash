package hu.elmdash.connection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import hu.elmdash.trip.JourneyEnd
import hu.elmdash.trip.JourneyRecord
import java.util.Locale

/** Separate from the foreground connection notification, so summaries survive service shutdown. */
internal class JourneyNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences("elm-journey-notifications", Context.MODE_PRIVATE)

    fun show(record: JourneyRecord) {
        if (record.end == null || record.end == JourneyEnd.RESET) return
        if (record.id in prefs.getStringSet("delivered", emptySet()).orEmpty()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Út végi összesítés", NotificationManager.IMPORTANCE_DEFAULT))
        if (!manager.areNotificationsEnabled()) return
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return
        val text = summary(record)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Utazás véget ért")
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setOnlyAlertOnce(true)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            builder.setContentIntent(PendingIntent.getActivity(context, 330, launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        try {
            manager.notify(record.id, 330, builder.build())
            val delivered = prefs.getStringSet("delivered", emptySet()).orEmpty()
            prefs.edit().putStringSet("delivered", (delivered.toList().takeLast(199) + record.id).toSet()).apply()
        } catch (_: SecurityException) { /* User may revoke notification permission between checks. */ }
    }

    companion object {
        const val CHANNEL = "journey-summary"
        fun summary(record: JourneyRecord): String {
            val s = record.summary
            fun number(n: Double, decimals: Int) = String.format(Locale.forLanguageTag("hu-HU"), "%.${decimals}f", n)
            val fuel = if (record.sources.isEmpty()) "—" else number(s.fuelLiters, 2)
            val average = s.averageL100?.let { number(it, 1) } ?: "—"
            val cost = record.estimatedCostHuf?.let { "≈${number(it, 0)} Ft benzinköltség" } ?: "Benzinköltség: — (nincs ár/adat)"
            val priceNote = record.petrolPrice?.let { " • ${number(it.hufPerLiter, 1)} Ft/l" + if (it.cached) " • mentett ár" else "" }.orEmpty()
            return "${number(s.distanceKm, 2)} km • $fuel l benzin • $average l/100 km\n$cost$priceNote" +
                if (s.hasGaps || s.containsEstimate || record.sources.isEmpty()) {
                    "\n" + listOfNotNull(
                        "Részleges mérés".takeIf { s.hasGaps },
                        "Becsült fogyasztás".takeIf { s.containsEstimate },
                        "Nincs fogyasztási adat".takeIf { record.sources.isEmpty() }).joinToString(" • ")
                } else ""
        }
    }
}
