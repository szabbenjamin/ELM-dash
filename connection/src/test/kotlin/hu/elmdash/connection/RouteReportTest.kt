package hu.elmdash.connection

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RouteReportTest {
    @Test fun duplicateFixesAndLongGapsDoNotInventContinuousRoutes() {
        val file = File.createTempFile("route-report", ".jsonl")
        try {
            fun point(time: String, lat: Double = 47.0) = JSONObject().put("type", "sample").put("gps", JSONObject()
                .put("fixUtc", time).put("latitude", lat).put("longitude", 19.0)).toString()
            file.writeText(listOf("{\"type\":\"header\",\"targetId\":\"private-target\"}",
                point("2026-01-01T00:00:00Z"), point("2026-01-01T00:00:00Z"),
                point("2026-01-01T00:01:00Z"), point("2026-01-01T00:10:00Z"),
                point("2026-01-01T00:11:00Z", 999.0),
                "{\"type\":\"end\",\"distanceKm\":5,\"partial\":true}").joinToString("\n"))
            val report = RouteReport.read(listOf(file))
            val segments = report.getJSONArray("segments")
            assertEquals(2, segments.length())
            assertEquals(2, segments.getJSONArray(0).length())
            assertEquals(1, segments.getJSONArray(1).length())
            assertEquals(5.0, report.getJSONObject("summary").getDouble("distanceKm"), 0.0)
            assertFalse(report.toString().contains("private-target"))
            val html = RouteReport.generate(RuntimeEnvironment.getApplication(), file)
            assertTrue(html.readText().contains("© OpenStreetMap"))
            assertFalse(html.readText().contains("/*ROUTE_DATA*/null"))
            html.delete()
        } finally { file.delete() }
    }
    @Test fun absentGpsAndScriptLookingTextAreSafe() {
        val data = RouteReport.read(emptyList())
        assertEquals(0, data.getJSONArray("segments").length())
        data.getJSONObject("summary").put("startedUtc", "</script><script>alert('injection')</script>")
        val html = RouteReport.html(RuntimeEnvironment.getApplication(), data)
        assertFalse(html.contains("</script><script>alert('injection')"))
        assertTrue(html.contains("\\u003c"))
        assertTrue(html.contains("textContent"))
    }
}
