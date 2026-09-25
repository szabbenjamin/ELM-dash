package hu.elmdash.connection

import android.content.Context
import hu.elmdash.trip.*
import org.json.JSONArray
import org.json.JSONObject

/** One versioned preference value atomically checkpoints the log, active draft and tank balance. */
class JourneyStore(context: Context) {
    private val prefs = context.getSharedPreferences("elm-journal", Context.MODE_PRIVATE)
    fun load(): JourneyLogState = runCatching {
        val j = JSONObject(prefs.getString("v1", null) ?: return JourneyLogState())
        require(j.getInt("version") == 1)
        val t = j.getJSONObject("tank")
        JourneyLogState(TankEstimate(t.getDouble("capacity"), t.longOrNull("fullAt"), t.getDouble("consumed"), t.getBoolean("gaps"), t.getBoolean("estimate")),
            j.getJSONArray("journeys").objects().mapNotNull { runCatching { record(it) }.getOrNull() }.take(200),
            j.getJSONArray("refills").objects().mapNotNull { runCatching {
                FullRefill(it.getString("id"), it.getLong("at"), it.doubleOrNull("pumped"), it.doubleOrNull("consumed"), it.getBoolean("gaps"), it.doubleOrNull("paid"))
            }.getOrNull() }.take(100), j.optJSONObject("active")?.let { record(it) })
    }.getOrDefault(JourneyLogState())

    fun save(state: JourneyLogState) {
        val t = state.tank
        val json = JSONObject().put("version", 1)
            .put("tank", JSONObject().put("capacity", t.capacityLiters).put("fullAt", t.fullAtMs)
                .put("consumed", t.consumedLiters).put("gaps", t.hasGaps).put("estimate", t.containsEstimate))
            .put("journeys", JSONArray().also { a -> state.journeys.take(200).forEach { a.put(record(it)) } })
            .put("refills", JSONArray().also { a -> state.refills.take(100).forEach {
                a.put(JSONObject().put("id", it.id).put("at", it.atMs).put("pumped", it.pumpedLiters)
                    .put("consumed", it.previousConsumedLiters).put("gaps", it.previousHasGaps).put("paid", it.totalPaidHuf))
            } }).put("active", state.active?.let { record(it) })
        prefs.edit().putString("v1", json.toString()).apply()
    }
    private fun record(r: JourneyRecord): JSONObject {
        val s = r.stats; val t = r.summary
        return JSONObject().put("id", r.id).put("start", r.startedAtMs).put("updated", r.updatedAtMs)
            .put("endAt", r.endedAtMs).put("end", r.end?.name).put("profile", r.profile)
            .put("sources", JSONArray(r.sources.map { it.name }))
            .put("petrolPrice", r.petrolPrice?.let { JSONObject().put("huf", it.hufPerLiter).put("at", it.fetchedAtMs).put("cached", it.cached).put("source", it.sourceUrl).put("afterStart", it.appliedAfterStart) })
            .put("trip", JSONObject().put("started", t.started).put("km", t.distanceKm).put("liters", t.fuelLiters)
                .put("pairedKm", t.pairedDistanceKm).put("pairedLiters", t.pairedFuelLiters)
                .put("seconds", t.observedSeconds).put("covered", t.coveredSeconds)
                .put("gaps", t.hasGaps).put("fromStart", t.fromEngineStart).put("estimate", t.containsEstimate))
            .put("stats", JSONObject().put("speedSeconds", s.speedSeconds).put("moving", s.movingSeconds)
                .put("idle", s.idleSeconds).put("idleFuel", s.idleFuelLiters).put("maxSpeed", s.maxSpeed)
                .put("maxRpm", s.maxRpm).put("minCoolant", s.minCoolant).put("maxCoolant", s.maxCoolant)
                .put("loadIntegral", s.loadIntegral).put("loadSeconds", s.loadSeconds))
    }
    private fun record(j: JSONObject): JourneyRecord {
        val t = j.getJSONObject("trip"); val s = j.getJSONObject("stats")
        return JourneyRecord(j.getString("id"), j.getLong("start"), j.getLong("updated"), j.longOrNull("endAt"),
            j.optString("end").takeIf { it.isNotBlank() }?.let { JourneyEnd.valueOf(it) },
            TripSummary(t.getBoolean("started"), t.getDouble("km"), t.getDouble("liters"), t.getDouble("pairedKm"),
                t.getDouble("pairedLiters"), t.getDouble("seconds"), t.getDouble("covered"), t.getBoolean("gaps"),
                t.getBoolean("fromStart"), t.getBoolean("estimate")),
            JourneyStats(s.getDouble("speedSeconds"), s.getDouble("moving"), s.getDouble("idle"), s.getDouble("idleFuel"),
                s.doubleOrNull("maxSpeed"), s.doubleOrNull("maxRpm"), s.doubleOrNull("minCoolant"), s.doubleOrNull("maxCoolant"),
                s.getDouble("loadIntegral"), s.getDouble("loadSeconds")),
            j.getJSONArray("sources").let { a -> (0 until a.length()).map { FuelSource.valueOf(a.getString(it)) }.toSet() }, j.getString("profile"),
            j.optJSONObject("petrolPrice")?.let { price -> runCatching { PetrolPrice(price.getDouble("huf"), price.getLong("at"), price.getBoolean("cached"), price.optString("source", PetrolPrice.SOURCE_URL), price.optBoolean("afterStart", false)) }.getOrNull() })
    }
    private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONObject.longOrNull(key: String) = if (has(key) && !isNull(key)) getLong(key) else null
    private fun JSONObject.doubleOrNull(key: String) = if (has(key) && !isNull(key)) getDouble(key) else null
}
