package hu.elmdash.connection

import android.location.Location
import hu.elmdash.obd.Pid
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RouteTest {
    @Test fun destinationRejectsUnsafeUrlsAndEncodesFolder() {
        for (url in listOf("http://example.org", "https://user:pass@example.org", "https://example.org?q=x", "https://example.org/../x")) {
            assertTrue(runCatching { RouteConfig(server = url).destination() }.isFailure)
        }
        val config = RouteConfig(server = "https://example.org/dav", folder = "car logs/day")
        assertEquals("https://example.org/dav/car%20logs/day/", config.destination())
        assertTrue(runCatching { config.copy(folder = "../secret").destination() }.isFailure)
        assertNotEquals(config.targetId(), config.copy(username = "another").targetId())
        assertEquals(config.targetId(), config.copy(password = "rotated").targetId())
    }
    @Test fun transportSendsRealDavMethodsAndDoesNotFollowRedirects() {
        MockWebServer().use { server ->
            server.start()
            val client = WebDavClient()
            val config = RouteConfig(username = "test", password = "secret")
            server.enqueue(MockResponse().setResponseCode(201))
            assertEquals(201, client.request(config, server.url("/folder/").toString(), "MKCOL"))
            assertEquals("MKCOL", server.takeRequest().method)
            val file = File.createTempFile("elm-route", ".jsonl")
            try {
                file.writeText("{\"type\":\"header\"}\n")
                server.enqueue(MockResponse().setResponseCode(201))
                assertEquals(201, client.request(config, server.url("/folder/trip.jsonl").toString(), "PUT", file))
                val request = server.takeRequest()
                assertEquals("PUT", request.method)
                assertEquals(file.readText(), request.body.readUtf8())
                assertEquals("Basic dGVzdDpzZWNyZXQ=", request.getHeader("Authorization"))
                server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/other")))
                assertEquals(302, client.request(config, server.url("/folder/").toString(), "MKCOL"))
                server.takeRequest()
                assertEquals(3, server.requestCount)
            } finally { file.delete() }
        }
        assertTrue(DavFailure(503).retryable)
        assertTrue(DavFailure(429).retryable)
        assertFalse(DavFailure(401).retryable)
    }
    @Test fun samplesKeepMissingDiagnosticsNullAndRejectOldGps() {
        val state = DashboardState(nowMs = 100_000)
        val location = Location("gps").apply {
            latitude = 47.0; longitude = 19.0; accuracy = 10f
            elapsedRealtimeNanos = 99_000_000_000; time = 1000
        }
        val sample = RouteJson.sample(state, 1000, location, "test")
        assertEquals(1000, sample.getJSONObject("gps").getLong("ageMs"))
        assertTrue(sample.getJSONObject("obd").getJSONObject(Pid.RPM.name).isNull("value"))
        assertTrue(RouteJson.sample(state.copy(nowMs = 200_000), 1000, location, "test").isNull("gps"))
        assertFalse(RouteJson.running(state))
        assertFalse(RouteJson.running(state.copy(simulated = true)))
    }
}
