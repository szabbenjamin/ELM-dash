package hu.elmdash.connection

import hu.elmdash.obd.*
import hu.elmdash.trip.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RouteRecorderTest {
    @Test fun realTripClosesInOrderAndDemoNeverRecords() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val dir = RouteRecorder.directory(context).apply { deleteRecursively(); mkdirs() }
        val uploaded = mutableListOf<File>()
        val recorder = RouteRecorder(context, backgroundScope) { uploaded.add(it) }
        val record = JourneyRecord("test-trip", 1000, 2000)
        val state = DashboardState(phase = Phase.LIVE, nowMs = 1000,
            telemetry = Telemetry(mapOf(Pid.RPM to Reading(900.0, 1000, Quality.OK))),
            journal = JourneyLogState(active = record))
        val config = RouteConfig(record = true, upload = true, server = "https://example.org/dav", username = "fixture")
        recorder.tick(state.copy(simulated = true), 1000, null, "no fix", config)
        runCurrent(); assertTrue(dir.listFiles().orEmpty().isEmpty())
        recorder.tick(state, 1000, null, "no fix", config)
        recorder.tick(state.copy(nowMs = 2000), 2000, null, "no fix", config)
        val ended = record.copy(endedAtMs = 3000, end = JourneyEnd.ENGINE_OFF)
        recorder.tick(state.copy(journal = JourneyLogState(journeys = listOf(ended))), 3000, null, "no fix", config)
        runCurrent()
        val lines = dir.listFiles()!!.single { it.extension == "jsonl" }.readLines().map(::JSONObject)
        assertEquals(listOf("header", "sample", "sample", "end"), lines.map { it.getString("type") })
        assertEquals(config.targetId(), lines.first().getString("targetId"))
        assertEquals("ENGINE_OFF", lines.last().getString("reason"))
        assertEquals(1, uploaded.size)
        assertFalse(uploaded.single().readText().contains("https://"))
        dir.deleteRecursively()
    }
    @Test fun interruptedPartialLineIsDiscardedBeforeRecoveryFooter() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val dir = RouteRecorder.directory(context).apply { deleteRecursively(); mkdirs() }
        File(dir, "test-1.open").writeText("{\"type\":\"header\",\"targetId\":null}\n{\"type\":\"sam")
        RouteRecorder(context, backgroundScope) {}
        runCurrent()
        val lines = File(dir, "test-1.jsonl").readLines().map(::JSONObject)
        assertEquals(2, lines.size)
        assertEquals("APP_RESTART", lines.last().getString("reason"))
        assertTrue(lines.last().getBoolean("partial"))
        dir.deleteRecursively()
    }
}
