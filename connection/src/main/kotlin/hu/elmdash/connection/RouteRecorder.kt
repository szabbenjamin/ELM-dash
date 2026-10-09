package hu.elmdash.connection

import android.content.Context
import android.location.Location
import hu.elmdash.obd.Pid
import hu.elmdash.trip.JourneyRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.io.File
import java.time.Instant

object RouteJson {
    fun running(s: DashboardState) = !s.simulated && s.phase == Phase.LIVE && s.journal.active != null && (s.telemetry.value(Pid.RPM, s.nowMs) ?: 0.0) > 0
    fun sample(s: DashboardState, wall: Long, location: Location?, gpsStatus: String): JSONObject {
        val age = location?.let { s.nowMs - it.elapsedRealtimeNanos / 1_000_000 }
        val gps = location?.takeIf { age != null && age in 0..90_000 }?.let {
            JSONObject().put("latitude", it.latitude).put("longitude", it.longitude).put("accuracyMeters", it.accuracy.toDouble())
                .put("fixUtc", Instant.ofEpochMilli(it.time).toString()).put("ageMs", age)
                .put("speedKmh", if (it.hasSpeed()) it.speed * 3.6 else JSONObject.NULL)
                .put("altitudeMeters", if (it.hasAltitude()) it.altitude else JSONObject.NULL)
        }
        val pids = JSONObject()
        Pid.entries.forEach { pid ->
            val r = s.telemetry.readings[pid]
            pids.put(pid.name, JSONObject().put("pid", "%02X".format(pid.code)).put("unit", pid.unit)
                .put("value", r?.fresh(s.nowMs) ?: JSONObject.NULL).put("lastKnownValue", r?.lastKnownValue ?: JSONObject.NULL)
                .put("ageMs", r?.let { (s.nowMs - it.atMs).coerceAtLeast(0) } ?: JSONObject.NULL)
                .put("quality", r?.quality?.name ?: "NOT_READ").put("source", r?.source ?: "ECU"))
        }
        return JSONObject().put("type", "sample").put("utc", Instant.ofEpochMilli(wall).toString())
            .put("elapsedRealtimeMs", s.nowMs).put("gps", gps ?: JSONObject.NULL).put("gpsStatus", gpsStatus)
            .put("obd", pids).put("ecu", s.telemetry.ecu ?: JSONObject.NULL)
            .put("fuel", JSONObject().put("litersPerHour", s.fuel.litersPerHour ?: JSONObject.NULL)
                .put("litersPer100Km", s.fuel.litersPer100Km ?: JSONObject.NULL)
                .put("rolling10sLitersPer100Km", s.fuelDisplay.value.takeIf { s.fuelDisplay.fresh } ?: JSONObject.NULL)
                .put("source", s.fuel.source.name).put("estimated", s.fuel.source.estimated))
    }
}

/** Single IO consumer preserves sample/footer ordering. No coordinates or credentials in the ordinary journal prefs. */
class RouteRecorder(private val context: Context,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val enqueue: (File) -> Unit = { RouteUploads.enqueue(context, it) }) {
    private data class Tick(val state: DashboardState, val wall: Long, val gps: Location?, val status: String, val config: RouteConfig)
    private val queue = Channel<Tick>(64)
    private val settings = RouteSettings(context)
    private val directory = directory(context)
    private var open: File? = null
    private var id: String? = null
    private var target: String? = null
    private var capped = false
    init {
        scope.launch {
            directory.mkdirs()
            // Never append a new process to old monotonic timestamps; close interrupted recordings explicitly.
            directory.listFiles().orEmpty().filter { it.extension == "open" }.forEach { file ->
                runCatching {
                    // A process may die halfway through a line. Keep only complete JSON records.
                    java.io.RandomAccessFile(file, "rw").use { out ->
                        var length = out.length()
                        while (length > 0) { out.seek(length - 1); if (out.read() == 10) break; length-- }
                        out.setLength(length)
                    }
                    file.bufferedReader().use { require(JSONObject(it.readLine()).getString("type") == "header") }
                    file.appendText(JSONObject().put("type", "end").put("reason", "APP_RESTART").put("partial", true).toString() + "\n")
                    check(file.renameTo(File(directory, file.nameWithoutExtension + ".jsonl")))
                }.onFailure { settings.status = "Megszakadt út helyreállítása sikertelen; a helyi fájl megmaradt." }
            }
            RouteUploads.enqueuePending(context)
            for (tick in queue) runCatching { process(tick) }.onFailure {
                settings.status = "Útvonal mentési hiba; ellenőrizd a szabad tárhelyet."
            }
        }
    }
    fun tick(s: DashboardState, wall: Long, gps: Location?, status: String, config: RouteConfig) {
        if (!queue.trySend(Tick(s, wall, gps?.let(::Location), status, config)).isSuccess)
            settings.status = "Napló írása túl lassú; egyes minták kimaradtak."
    }
    private fun process(t: Tick) {
        val active = t.state.journal.active
        if (open != null && (active?.id != id || !t.config.record || t.state.simulated)) {
            finish(t.state.journal.journeys.firstOrNull { it.id == id }, t.wall)
        }
        if (!t.config.record || !RouteJson.running(t.state)) return
        if (open == null) {
            if (directory.walkTopDown().filter { it.isFile }.sumOf { it.length() } >= 200L * 1024 * 1024) {
                settings.status = "A helyi útvonalnapló elérte a 200 MB korlátot. Exportálj/törölj lezárt naplókat a beállításokban."; return
            }
            capped = false
            id = active!!.id
            target = if (t.config.upload) t.config.targetId() else null
            open = File(directory, "${id}-${t.wall}.open")
            open!!.writeText(JSONObject().put("type", "header").put("schema", 1).put("journeyId", id)
                .put("startedUtc", Instant.ofEpochMilli(active.startedAtMs).toString()).put("recordingStartedUtc", Instant.ofEpochMilli(t.wall).toString())
                .put("targetId", target ?: JSONObject.NULL).put("profile", active.profile)
                .put("scope", "Supported standard OBD-II live PIDs only; no manufacturer modules or DTC scan")
                .put("obdIntervalMs", 1000).put("gpsRequestIntervalMs", 60000).toString() + "\n")
        }
        if (open!!.length() >= 50L * 1024 * 1024) {
            capped = true
            settings.status = "Az aktív út elérte az 50 MB-os korlátot; a részletes mintavétel szünetel."; return
        }
        open!!.appendText(RouteJson.sample(t.state, t.wall, t.gps, t.status).toString() + "\n")
    }
    private fun finish(record: JourneyRecord?, wall: Long) {
        val file = open ?: return
        file.appendText((record?.let { RouteReport.summary(it) } ?: JSONObject()).put("type", "end").put("utc", Instant.ofEpochMilli(record?.endedAtMs ?: wall).toString())
            .put("reason", record?.end?.name ?: "RECORDING_DISABLED").put("partial", capped || record == null || record.summary.hasGaps)
            .put("distanceKm", record?.summary?.distanceKm ?: JSONObject.NULL).put("fuelLiters", record?.summary?.fuelLiters ?: JSONObject.NULL)
            .put("averageLitersPer100Km", record?.summary?.averageL100 ?: JSONObject.NULL)
            .put("estimatedCostHuf", record?.estimatedCostHuf ?: JSONObject.NULL).toString() + "\n")
        val closed = File(directory, file.nameWithoutExtension + ".jsonl")
        check(file.renameTo(closed))
        open = null; id = null
        runCatching { RouteReport.generate(context, closed) }.onFailure { settings.status = "A HTML-térkép később újra előállítható a naplóból." }
        if (target != null) enqueue(closed)
        target = null
    }
    companion object { fun directory(context: Context) = File(context.noBackupFilesDir, "routes-v1") }
}
