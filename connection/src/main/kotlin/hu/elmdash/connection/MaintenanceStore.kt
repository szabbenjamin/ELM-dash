package hu.elmdash.connection

import android.content.Context
import hu.elmdash.trip.ServiceEntry
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class MaintenanceStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("elm-maintenance", Context.MODE_PRIVATE)
    var odometer: Double? get() = prefs.getString("odometer", null)?.toDoubleOrNull()
        set(value) { require(value == null || value.isFinite() && value >= 0); prefs.edit().putString("odometer", value?.toString()).apply() }
    fun entries(): List<ServiceEntry> = runCatching {
        val array = JSONArray(prefs.getString("entries", "[]"))
        (0 until array.length()).mapNotNull { i -> runCatching {
            val j = array.getJSONObject(i)
            ServiceEntry(j.getString("id"),j.getString("name"),LocalDate.parse(j.getString("date")),j.getDouble("km"),
                if (j.isNull("intervalKm")) null else j.getDouble("intervalKm"),
                if (j.isNull("months")) null else j.getInt("months"), j.optString("note"))
        }.getOrNull() }
    }.getOrDefault(emptyList())
    fun save(entry: ServiceEntry) { write(listOf(entry) + entries().filterNot { it.id == entry.id }); MaintenanceWorker.schedule(context) }
    fun remove(id: String) = write(entries().filterNot { it.id == id })
    private fun write(entries: List<ServiceEntry>) {
        val array = JSONArray()
        entries.take(500).forEach { e -> array.put(JSONObject().put("id",e.id).put("name",e.name).put("date",e.performed.toString()).put("km",e.odometerKm)
            .put("intervalKm",e.intervalKm ?: JSONObject.NULL).put("months",e.intervalMonths ?: JSONObject.NULL).put("note",e.note)) }
        prefs.edit().putString("entries",array.toString()).apply()
    }
    fun latest() = entries().groupBy { it.name.lowercase() }.values.map { group -> group.maxBy { it.performed } }
}
