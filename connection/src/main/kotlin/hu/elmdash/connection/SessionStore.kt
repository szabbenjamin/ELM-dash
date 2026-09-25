package hu.elmdash.connection

import android.content.Context
import hu.elmdash.trip.*
import org.json.JSONObject
import java.time.LocalDate

/** Local settings and the last observation only; a new process never silently resumes an old trip. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("elm-dashboard", Context.MODE_PRIVATE)
    var autoConnect: Boolean
        get() = prefs.getBoolean("autoConnect", false)
        set(value) { prefs.edit().putBoolean("autoConnect", value).apply() }
    var autoPaused: Boolean
        get() = prefs.getBoolean("autoPaused", false)
        set(value) { prefs.edit().putBoolean("autoPaused", value).apply() }
    var headUnitAddress: String
        get() = prefs.getString("headUnit", "").orEmpty()
        set(value) { prefs.edit().putString("headUnit", value).apply() }
    fun enableAutoByDefaultOnce() { if (!prefs.contains("autoConnect")) autoConnect = true }
    var address: String
        get() = prefs.getString("address", "").orEmpty()
        set(value) { prefs.edit().putString("address", value).apply() }
    var insecure: Boolean
        get() = prefs.getBoolean("insecure", false)
        set(value) { prefs.edit().putBoolean("insecure", value).apply() }
    var fuelSettings: FuelSettings
        get() = runCatching {
            FuelSettings(FuelProfile.valueOf(prefs.getString("profile", "KALOS_12")!!), prefs.getFloat("correction", 1f).toDouble(),
                prefs.getFloat("volumetricEfficiency", 0.8f).toDouble())
        }.getOrDefault(FuelSettings(FuelProfile.KALOS_12))
        set(value) { prefs.edit().putString("profile", value.profile.name).putFloat("correction", value.correction.toFloat())
            .putFloat("volumetricEfficiency", value.volumetricEfficiency.toFloat()).apply() }
    fun saveTrip(trip: TripSummary) {
        if (!trip.started) return
        prefs.edit().putString("lastTrip", encode(trip).toString()).apply()
    }
    private fun encode(trip: TripSummary) = JSONObject().put("distance", trip.distanceKm).put("liters", trip.fuelLiters)
            .put("pairedDistance", trip.pairedDistanceKm).put("pairedFuel", trip.pairedFuelLiters)
            .put("seconds", trip.observedSeconds).put("covered", trip.coveredSeconds)
            .put("gaps", trip.hasGaps).put("fromStart", trip.fromEngineStart).put("estimate", trip.containsEstimate)
    fun lastTrip(): TripSummary? = runCatching {
        decode(JSONObject(prefs.getString("lastTrip", null) ?: return null))
    }.getOrNull()
    private fun decode(j: JSONObject) = TripSummary(true, j.getDouble("distance"), j.getDouble("liters"), j.getDouble("pairedDistance"),
            j.getDouble("pairedFuel"), j.getDouble("seconds"), j.getDouble("covered"),
            j.getBoolean("gaps"), j.getBoolean("fromStart"), j.getBoolean("estimate"))

    fun days(): Map<LocalDate, TripSummary> = runCatching {
        val json = JSONObject(prefs.getString("daily-v1", "{}")!!)
        json.keys().asSequence().mapNotNull { key ->
            runCatching { LocalDate.parse(key) to decode(json.getJSONObject(key)) }.getOrNull()
        }.toMap()
    }.getOrDefault(emptyMap())

    fun saveDays(days: Map<LocalDate, TripSummary>) {
        val json = JSONObject()
        days.filterValues { it.started }.forEach { (day, trip) -> json.put(day.toString(), encode(trip)) }
        prefs.edit().putString("daily-v1", json.toString()).apply()
    }
}
