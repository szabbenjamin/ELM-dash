package hu.elmdash.connection

import android.content.Context
import hu.elmdash.trip.JourneyRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Only validated coordinates and summary fields enter the document; never raw JSONL or credentials. */
object RouteReport {
    fun read(files: List<File>): JSONObject {
        val segments = JSONArray()
        var header = JSONObject()
        var end = JSONObject()
        var damaged = false
        files.sortedBy { it.name }.forEach { file ->
            var segment = JSONArray()
            var lastFix: String? = null
            var lastTime: Long? = null
            fun flush() { if (segment.length() > 0) segments.put(segment); segment = JSONArray() }
            file.useLines { lines -> lines.forEach { line ->
                if (line.isNotBlank()) try {
                    val row = JSONObject(line)
                    when (row.optString("type")) {
                        "header" -> if (header.length() == 0) header = row
                        "end" -> end = row
                        "sample" -> {
                            val gps = row.optJSONObject("gps")
                            if (gps != null) {
                                val lat = gps.optDouble("latitude", Double.NaN)
                                val lon = gps.optDouble("longitude", Double.NaN)
                                val fix = gps.optString("fixUtc")
                                val time = runCatching { Instant.parse(fix).toEpochMilli() }.getOrNull()
                                if (lat.isFinite() && lon.isFinite() && lat in -85.0..85.0 && lon in -180.0..180.0 && time != null && fix != lastFix) {
                                    if (lastTime?.let { time - it !in 1..120_000 } == true) flush()
                                    segment.put(JSONArray().put(lat).put(lon))
                                    lastFix = fix; lastTime = time
                                }
                            }
                        }
                    }
                } catch (_: Exception) { damaged = true; flush(); lastTime = null; lastFix = null }
            } }
            flush()
        }
        val summary = JSONObject()
        for (key in listOf("distanceKm", "fuelLiters", "averageLitersPer100Km", "estimatedCostHuf", "durationSeconds", "averageSpeed", "maxSpeed", "maxRpm", "minCoolant", "maxCoolant", "averageLoad", "coveragePercent", "movingSeconds", "idleSeconds", "idleFuelLiters", "pairedFuelLiters", "pairedDistanceKm"))
            summary.put(key, end.optDouble(key, Double.NaN).takeIf { it.isFinite() } ?: JSONObject.NULL)
        for (key in listOf("reasonLabel", "sources", "profile")) summary.put(key, end.optString(key, ""))
        summary.put("startedUtc", header.optString("startedUtc", ""))
        summary.put("endedUtc", end.optString("utc", ""))
        summary.put("partial", damaged || end.optBoolean("partial", true))
        return JSONObject().put("segments", segments).put("summary", summary)
    }
    fun journey(context: Context, record: JourneyRecord): String {
        val files = RouteRecorder.directory(context).listFiles().orEmpty().filter {
            it.extension in listOf("jsonl", "open") && runCatching {
                it.bufferedReader().use { reader -> JSONObject(reader.readLine()).optString("journeyId") == record.id }
            }.getOrDefault(false)
        }
        val data = read(files)
        data.put("summary", summary(record))
        return html(context, data)
    }
    fun summary(record: JourneyRecord): JSONObject = JSONObject().put("distanceKm", record.summary.distanceKm).put("fuelLiters", record.summary.fuelLiters)
            .put("averageLitersPer100Km", record.summary.averageL100 ?: JSONObject.NULL)
            .put("estimatedCostHuf", record.estimatedCostHuf ?: JSONObject.NULL)
            .put("startedUtc", Instant.ofEpochMilli(record.startedAtMs).toString())
            .put("endedUtc", record.endedAtMs?.let { Instant.ofEpochMilli(it).toString() } ?: "Folyamatban")
            .put("partial", record.summary.hasGaps || record.summary.containsEstimate)
            .put("durationSeconds", record.durationSeconds).put("averageSpeed", record.averageSpeed ?: JSONObject.NULL)
            .put("maxSpeed", record.stats.maxSpeed ?: JSONObject.NULL).put("maxRpm", record.stats.maxRpm ?: JSONObject.NULL)
            .put("minCoolant", record.stats.minCoolant ?: JSONObject.NULL).put("maxCoolant", record.stats.maxCoolant ?: JSONObject.NULL)
            .put("averageLoad", record.stats.averageLoad ?: JSONObject.NULL).put("coveragePercent", record.summary.coveragePercent)
            .put("movingSeconds", record.stats.movingSeconds).put("idleSeconds", record.stats.idleSeconds)
            .put("idleFuelLiters", record.stats.idleFuelLiters).put("pairedFuelLiters", record.summary.pairedFuelLiters)
            .put("pairedDistanceKm", record.summary.pairedDistanceKm).put("reasonLabel", record.end?.label ?: "Folyamatban")
            .put("sources", record.sources.joinToString { it.label }).put("profile", record.profile)
    @Synchronized fun generate(context: Context, file: File): File {
        val html = File(file.parentFile, file.nameWithoutExtension + ".html")
        val temp = File(html.path + ".tmp")
        temp.writeText(html(context, read(listOf(file))))
        check(temp.renameTo(html))
        return html
    }
    fun html(context: Context, data: JSONObject): String {
        val template = context.assets.open("route-report.html").bufferedReader().use { it.readText() }
        val js = context.assets.open("leaflet/leaflet.js").bufferedReader().use { it.readText() }
        val css = context.assets.open("leaflet/leaflet.css").bufferedReader().use { it.readText() }
        val license = context.assets.open("leaflet/LICENSE").bufferedReader().use { it.readText() }
        return template.replace("/*LEAFLET_CSS*/", css).replace("/*LEAFLET_JS*/", js)
            .replace("/*LEAFLET_LICENSE*/", license.replace("*/", "* /"))
            .replace("/*ROUTE_DATA*/null", data.toString().replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029"))
    }
}
