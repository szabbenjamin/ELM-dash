package hu.elmdash.media

import android.media.session.PlaybackState
import hu.elmdash.connection.*
import hu.elmdash.graphics.DashboardTiles
import hu.elmdash.obd.*
import hu.elmdash.trip.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaDashboardTest {
    private fun live() = DashboardState(phase = Phase.LIVE, nowMs = 1_000,
        telemetry = Telemetry(mapOf(Pid.RPM to 3_200.0, Pid.COOLANT to 89.0,
            Pid.LOAD to 35.0, Pid.SPEED to 72.0, Pid.MAP to 42.0, Pid.TPS to 20.0,
            Pid.VOLTAGE to 14.2).mapValues { Reading(it.value, 1_000, Quality.OK) }),
        driving = DrivingAdvice(DriveCue.UPSHIFT, CoolantBand.NORMAL, 4),
        fuelDisplay = FuelDisplay(5.8, "l/100 km", true, FuelSource.MAF_ESTIMATE),
        trip = TripSummary(started = true, pairedDistanceKm = 10.0, pairedFuelLiters = 0.6),
        daily = TripSummary(started = true, distanceKm = 20.0, fuelLiters = 1.4, pairedDistanceKm = 20.0, pairedFuelLiters = 1.4))

    @Test fun `empty session never shows plausible fabricated engine values`() {
        val frame = MediaDashboard.frame(DashboardState())
        assertEquals("Nincs kapcsolat az autóval", frame.title)
        assertTrue(frame.status.contains("Nincs kapcsolat"))
        assertTrue(frame.tiles.all { !it.fresh })
    }

    @Test fun `live pages expose engine consumption and all requested auxiliary PIDs`() {
        val frame = MediaDashboard.frame(live())
        assertEquals("≈5,8 • Ma 7,0 l/100 km", frame.title)
        assertEquals("↑ 3200 rpm • 89 °C • 35 %", frame.subtitles[0])
        assertTrue(frame.subtitles[1].contains("Ma 1,40 l / 20,0 km • Út 6,0 l/100 km"))
        assertEquals("MAP 42 kPa • TPS 20 % • 14,2 V", frame.subtitles[2])
        assertTrue(frame.status.contains("MAF-becslés"))
        assertFalse(frame.simulated)
    }

    @Test fun `stop preserves numbers and removes color and shift suggestion`() {
        val before = live()
        val stopped = before.copy(phase = Phase.STOPPED,
            telemetry = before.telemetry.clearValues(1_100), nowMs = 1_100,
            fuelDisplay = before.fuelDisplay.copy(fresh = false), driving = DrivingAdvice())
        val frame = MediaDashboard.frame(stopped)
        assertEquals("Nincs kapcsolat az autóval", frame.title)
        assertEquals(MediaDashboard.frame(before).tiles.map { it.value }, frame.tiles.map { it.value })
        assertTrue(frame.tiles.all { !it.fresh })
        assertEquals(1, frame.tiles.map { it.color }.distinct().size)
        assertEquals("dash", frame.tiles[0].symbol)
        assertFalse(frame.subtitles[0].contains("↑"))
        assertTrue(frame.subtitles[2].contains("14,2 V (utolsó)"))
    }

    @Test fun `demo origin stays explicit after stopping`() {
        val state = live().copy(phase = Phase.STOPPED, simulated = true)
        val frame = MediaDashboard.frame(state)
        assertTrue(frame.simulated)
        assertTrue(frame.title.startsWith("DEMÓ • "))
        assertTrue(frame.status.startsWith("DEMÓ • "))
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun `coolant artwork turns grey even before advice tick catches up`() {
        val state = live().copy(nowMs = 7_000)
        val frame = MediaDashboard.frame(state)
        assertEquals(frame.tiles[0].color, frame.tiles[1].color)
        assertEquals("3200", frame.tiles[0].value)
        val bitmap = DashboardTiles.overview(state)
        assertEquals(frame.tiles[0].color, bitmap.getPixel(10, 100))
        assertEquals(frame.tiles[1].color, bitmap.getPixel(10, 400))
    }

    @Test fun `clock ticks without displayed changes do not churn media metadata`() {
        assertEquals(MediaDashboard.frame(live()), MediaDashboard.frame(live().copy(nowMs = 1_250)))
    }

    @Test fun `untrusted and impersonated media browsers cannot read telemetry`() {
        val aa = MediaBrowserAccess.ANDROID_AUTO
        assertTrue(MediaBrowserAccess.allowed(aa, 100, 200, arrayOf(aa)))
        assertFalse(MediaBrowserAccess.allowed(aa, 300, 200, arrayOf("other.app")))
        assertFalse(MediaBrowserAccess.allowed("other.app", 300, 200, arrayOf("other.app")))
        assertFalse(MediaBrowserAccess.allowed(aa, 100, 200, null))
        assertTrue(MediaBrowserAccess.allowed("own.app", 200, 200, arrayOf("own.app")))
    }

    @Test fun `data collection alone never claims audio playback`() {
        for (active in listOf(false, true)) {
            val playback = DashboardMediaService.playbackState(active)
            assertEquals(PlaybackState.STATE_PAUSED, playback.state)
            assertEquals(0f, playback.playbackSpeed)
            assertEquals(if (active) DashboardMediaService.STOP else DashboardMediaService.DEMO,
                playback.customActions.last().action)
        }
    }

    @Test fun `radio state follows real playback buffering pause and error`() {
        assertEquals(PlaybackState.STATE_PLAYING, DashboardMediaService.playbackState(false, playing = true).state)
        assertEquals(PlaybackState.STATE_BUFFERING, DashboardMediaService.playbackState(true, buffering = true).state)
        assertEquals(PlaybackState.STATE_ERROR, DashboardMediaService.playbackState(true, error = "offline").state)
    }

    @Test fun `idle shows a dash with fixed per100 unit`() {
        val frame = MediaDashboard.frame(live().copy(fuelDisplay = FuelDisplay().update(FuelReading(0.8, null, FuelSource.ECU), 0.0)))
        assertEquals("— • Ma 7,0 l/100 km", frame.title)
        assertFalse(frame.title.contains("l/h"))
    }

    @Test fun `opening media surface does not silently start demo or Bluetooth`() {
        val owner = Robolectric.buildService(DashboardMediaService::class.java).create()
        val service = owner.get()
        assertNotNull(service.sessionToken)
        assertEquals(Phase.STOPPED, DashboardGraph.get(RuntimeEnvironment.getApplication()).state.value.phase)
        owner.destroy()
    }
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test fun `compact AA artwork retains four tiles and greys stale engine data`() {
        val state = live()
        val bitmap = DashboardTiles.compactOverview(state)
        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
        assertNotEquals(bitmap.getPixel(70, 50), bitmap.getPixel(300, 50))
        val stale = DashboardTiles.compactOverview(state.copy(nowMs = 7_000,
            fuelDisplay = state.fuelDisplay.copy(fresh = false)))
        assertEquals(stale.getPixel(70, 50), stale.getPixel(300, 50))
        assertNotEquals(stale.getPixel(70, 50), stale.getPixel(300, 175)) // Stored daily aggregate remains valid.
        // The photographed AA card overlays the lower area; no artwork data may occupy it.
        val background = bitmap.getPixel(0, 511)
        for (y in 288 until 512) for (x in 0 until 512) assertEquals(background, bitmap.getPixel(x, y))
        assertNotEquals(background, bitmap.getPixel(70, 175))
        val output = java.io.File("build/reports/aa-compact-preview.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun `offline radio keeps its service for reconnection and user pause stops the intent`() {
        val owner = Robolectric.buildService(DashboardMediaService::class.java).create()
        val service = owner.get()
        service.onStartCommand(android.content.Intent().setAction(DashboardMediaService.ACTION_PLAY), 0, 1)
        assertTrue(service.wantsPlayback)
        assertTrue(RadioGraph.get(service).state.value.buffering)
        assertFalse(org.robolectric.Shadows.shadowOf(service).isStoppedBySelf)
        service.endCarPlayback() // A phone-started session is not owned by AA.
        assertTrue(service.wantsPlayback)
        service.onStartCommand(android.content.Intent().setAction(DashboardMediaService.ACTION_PAUSE), 0, 2)
        assertFalse(service.wantsPlayback)
        assertFalse(RadioGraph.get(service).state.value.buffering)
        assertTrue(org.robolectric.Shadows.shadowOf(service).isStoppedBySelf)
        owner.destroy()
    }

    @Test fun `network errors retry but decoding and unsupported content do not`() {
        fun error(code: Int) = androidx.media3.common.PlaybackException("test", null, code)
        assertTrue(RadioErrors.retryable(error(androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)))
        assertTrue(RadioErrors.retryable(error(androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)))
        assertFalse(RadioErrors.retryable(error(androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED)))
        assertFalse(RadioErrors.retryable(error(androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)))
    }

    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    @Test fun `HTTP retry honors transient status and rejects unauthorized or missing streams`() {
        for (code in listOf(401, 403, 404, 408, 429, 500, 503)) {
            val cause = androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException(code, "test", null,
                emptyMap(), androidx.media3.datasource.DataSpec(android.net.Uri.parse("https://example.org/radio")), byteArrayOf())
            val error = androidx.media3.common.PlaybackException("test", cause, androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
            assertEquals(code.toString(), code in listOf(408, 429, 500, 503), RadioErrors.retryable(error))
        }
    }

    @Test fun `MAP estimates and stale fuel are explicit in media text`() {
        val d = live().copy(fuelDisplay = FuelDisplay(6.2, "l/100 km", true, FuelSource.MAP_ESTIMATE))
        assertTrue(MediaDashboard.frame(d).title.startsWith("≈6,2"))
        assertTrue(MediaDashboard.frame(d).status.contains("MAP-becslés"))
        assertTrue(MediaDashboard.frame(d.copy(fuelDisplay = d.fuelDisplay.copy(fresh = false))).title.contains("utolsó"))
    }

    @Test fun `subtitle alternates data and station every eight seconds with no appended truncation`() {
        val radio = RadioState(RadioCatalog.stations, "oxygen", playing = true)
        val f = MediaDashboard.frame(live())
        for (t in listOf(0L, 7_999L, 16_000L, 23_999L))
            assertEquals(f.subtitles[0], MediaDashboard.subtitle(f, MediaPage.ENGINE, radio, t))
        for (t in listOf(8_000L, 15_999L, 24_000L))
            assertEquals("Oxygen Music", MediaDashboard.subtitle(f, MediaPage.ENGINE, radio, t))
        assertEquals(f.subtitles[2], MediaDashboard.subtitle(f, MediaPage.SENSORS, radio, 0))
        assertEquals("Oxygen Music", MediaDashboard.subtitle(f, MediaPage.SENSORS, radio, 8_000))
    }
    @Test fun `radio subtitle distinguishes pause and buffering from playback`() {
        val f = MediaDashboard.frame(live())
        val radio = RadioState(RadioCatalog.stations, "oxygen")
        assertTrue(MediaDashboard.subtitle(f, MediaPage.ENGINE, radio, 8_000).endsWith("szünet"))
        assertTrue(MediaDashboard.subtitle(f, MediaPage.ENGINE, radio.copy(buffering = true), 8_000).contains("kapcsolódás"))
        assertTrue(MediaDashboard.subtitle(f, MediaPage.ENGINE, radio.copy(error = "error"), 8_000).endsWith("hiba"))
    }
    @Test fun `service allows timed subtitle publication even when OBD and radio are unchanged`() {
        val owner = Robolectric.buildService(DashboardMediaService::class.java).create()
        val service = owner.get()
        fun subtitle(): String = org.robolectric.util.ReflectionHelpers.getField(service, "publishedSubtitle")
        val initial = subtitle()
        val publish = service.javaClass.getDeclaredMethod("publish", Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        try {
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(8))
        publish.invoke(service, false)
        assertEquals("Oxygen Music • szünet", subtitle())
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(8))
        publish.invoke(service, false)
        assertEquals(initial, subtitle())
        } finally { owner.destroy() }
    }

    @Test fun `tank page marks unknown baseline and approximate percentage without changing fuel title`() {
        val initial = MediaDashboard.frame(live())
        assertTrue(initial.subtitles[MediaPage.TANK.ordinal].contains("teletankolást"))
        val tank = TankEstimate(fullAtMs = 1, consumedLiters = 9.0, hasGaps = true)
        val f = MediaDashboard.frame(live().copy(journal = JourneyLogState(tank = tank)))
        assertTrue(f.subtitles[MediaPage.TANK.ordinal].contains("80 %"))
        assertTrue(f.subtitles[MediaPage.TANK.ordinal].contains("bizonytalan"))
        assertEquals(initial.title, f.title)
    }

}
