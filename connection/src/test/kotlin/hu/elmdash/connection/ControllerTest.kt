package hu.elmdash.connection

import hu.elmdash.elm.ElmTransport
import hu.elmdash.obd.Pid
import hu.elmdash.trip.FuelSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ControllerTest {
    @Test fun `demo runs through transport parser PID and trip then clears on stop`() = runTest {
        val c = DashboardController(RuntimeEnvironment.getApplication(), { currentTime }, backgroundScope)
        c.startDemo()
        advanceTimeBy(20_000); runCurrent()
        val live = c.state.value
        assertEquals(Phase.DEMO, live.phase)
        assertNotNull(live.telemetry.value(Pid.RPM, live.nowMs))
        assertNotNull(live.fuel.litersPer100Km)
        assertTrue(live.fuelDisplay.fresh)
        assertTrue(live.trip.distanceKm > 0.1)
        assertTrue(live.trip.fuelLiters > 0)
        c.stop()
        advanceTimeBy(10_000)
        assertEquals(Phase.STOPPED, c.state.value.phase)
        assertNull(c.state.value.telemetry.value(Pid.RPM, c.state.value.nowMs))
        assertNull(c.state.value.fuel.litersPerHour)
        assertEquals(live.fuelDisplay.value, c.state.value.fuelDisplay.value)
        assertFalse(c.state.value.fuelDisplay.fresh)
        assertNull(c.store.lastTrip()) // Demo must not write the user's real trip history.
    }

    @Test fun `every published sample has a matching clock so numbers cannot flash missing`() = runTest {
        val c = DashboardController(RuntimeEnvironment.getApplication(), { currentTime }, backgroundScope)
        val publications = mutableListOf<DashboardState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.state.collect { publications += it } }
        c.startDemo()
        advanceTimeBy(10_000); runCurrent()
        val live = publications.filter { it.telemetry.readings[Pid.RPM]?.value != null }
        assertTrue(live.size > 10)
        live.forEach { state ->
            assertNotNull("New sample must be visible immediately", state.telemetry.value(Pid.RPM, state.nowMs))
        }
        c.stop()
    }

    @Test fun `automatic reconnect keeps last visible values through rediscovery`() = runTest {
        var dropped = false
        val c = DashboardController(RuntimeEnvironment.getApplication(), { currentTime }, backgroundScope) { _, _, _ ->
            val delegate = DemoTransport { currentTime }
            object : ElmTransport by delegate {
                override suspend fun exchange(command: String, timeoutMs: Long): String {
                    if (!dropped && currentTime > 4_000 && command.startsWith("01")) {
                        dropped = true; throw IOException("simulated disconnect")
                    }
                    return delegate.exchange(command, timeoutMs)
                }
            }
        }
        val publications = mutableListOf<DashboardState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.state.collect { publications += it } }
        c.startLive("00:11:22:33:44:55", false)
        advanceTimeBy(12_000); runCurrent()
        assertTrue(dropped)
        assertEquals(Phase.LIVE, c.state.value.phase)
        val displayed = publications.dropWhile { it.telemetry.readings[Pid.RPM]?.lastKnownValue == null }
        assertTrue(displayed.any { it.phase == Phase.RETRY })
        displayed.forEach { assertNotNull(it.telemetry.readings[Pid.RPM]?.lastKnownValue) }
        c.stop()
    }

    @Test fun `reconnect exhausts finite attempts and never leaves stale fuel`() = runTest {
        var connects = 0
        var closes = 0
        val c = DashboardController(RuntimeEnvironment.getApplication(), { currentTime }, backgroundScope) { _, _, _ ->
            object : ElmTransport {
                override suspend fun connect() { connects++; throw IOException("adapter unavailable") }
                override suspend fun exchange(command: String, timeoutMs: Long) = ""
                override fun close() { closes++ }
            }
        }
        c.startLive("00:11:22:33:44:55", false)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(6, connects) // Initial attempt plus five retries.
        assertEquals(6, closes)
        assertEquals(Phase.ERROR, c.state.value.phase)
        assertEquals(FuelSource.UNAVAILABLE, c.state.value.fuel.source)
        assertNull(c.state.value.fuel.litersPerHour)
        c.stop()
    }
    @Test fun `daily totals persist across trips while reset and demo do not erase real day`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("elm-dashboard", 0).edit().clear().commit()
        val date = java.time.LocalDate.of(2026, 9, 16)
        val c = DashboardController(context, { currentTime }, backgroundScope, { date }) { _, _, _ -> DemoTransport { currentTime } }
        c.startLive("00:11:22:33:44:55", false)
        advanceTimeBy(30_000); runCurrent()
        val day = c.state.value.daily
        assertTrue(day.distanceKm > 0.1)
        assertTrue(day.fuelLiters > 0.0)
        c.resetTrip()
        assertEquals(day, c.state.value.daily)
        assertEquals(0.0, c.state.value.trip.distanceKm, 0.0)
        c.stop()
        val saved = c.store.days().getValue(date)
        assertEquals(day.distanceKm, saved.distanceKm, 0.0)
        c.startDemo(); advanceTimeBy(20_000); runCurrent(); c.stop()
        assertEquals(saved, c.store.days().getValue(date))
        val reopened = DashboardController(context, { currentTime }, backgroundScope, { date })
        assertEquals(saved.distanceKm, reopened.state.value.daily.distanceKm, 0.0)
        assertEquals(saved.fuelLiters, reopened.state.value.daily.fuelLiters, 0.0)
    }

    @Test fun `midnight changes stopped dashboard to the new day`() = runTest {
        var date = java.time.LocalDate.of(2026, 9, 16)
        val c = DashboardController(RuntimeEnvironment.getApplication(), { currentTime }, backgroundScope, { date })
        c.startDemo(); advanceTimeBy(20_000); runCurrent(); c.stop()
        assertTrue(c.state.value.daily.distanceKm > 0)
        date = date.plusDays(1)
        advanceTimeBy(1_100); runCurrent()
        assertEquals(date, c.state.value.today)
        assertEquals(0.0, c.state.value.daily.distanceKm, 0.0)
        assertNull(c.state.value.daily.averageL100)
    }

    @Test fun `automatic reconnect reaches an adapter that appears after the normal retry limit`() = runTest {
        var attempts = 0
        val c = DashboardController(RuntimeEnvironment.getApplication(), { currentTime }, backgroundScope) { _, _, _ ->
            val delegate = DemoTransport { currentTime }
            object : ElmTransport by delegate {
                override suspend fun connect() {
                    attempts++
                    if (attempts <= 7) throw IOException("not in range yet")
                    delegate.connect()
                }
            }
        }
        c.startLive("00:11:22:33:44:55", false, retryForever = true)
        advanceTimeBy(140_000); runCurrent()
        assertTrue(attempts >= 8)
        assertEquals(Phase.LIVE, c.state.value.phase)
        assertNotNull(c.state.value.telemetry.value(Pid.RPM, c.state.value.nowMs))
        assertFalse(c.state.value.simulated)
        c.stop()
        val stoppedAt = attempts
        advanceTimeBy(120_000); runCurrent()
        assertEquals(stoppedAt, attempts)
        assertEquals(Phase.STOPPED, c.state.value.phase)
    }

    @Test fun `unset vehicle profile uses Kalos but an explicit ECU choice is retained`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("elm-dashboard", 0)
        prefs.edit().clear().commit()
        val store = SessionStore(context)
        assertEquals(hu.elmdash.trip.FuelProfile.KALOS_12, store.fuelSettings.profile)
        store.fuelSettings = hu.elmdash.trip.FuelSettings(hu.elmdash.trip.FuelProfile.ECU_ONLY, 1.1, 0.9)
        val restored = SessionStore(context).fuelSettings
        assertEquals(hu.elmdash.trip.FuelProfile.ECU_ONLY, restored.profile)
        assertEquals(0.9, restored.volumetricEfficiency, 1e-6)
        assertEquals(1.1, restored.correction, 1e-6)
    }

    @Test fun `Kalos MAF absent fixture reaches fuel and daily average through the ELM pipeline`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("elm-dashboard", 0).edit().clear().commit()
        val c = DashboardController(context, { currentTime }, backgroundScope) { _, _, _ ->
            val delegate = DemoTransport { currentTime }
            object : ElmTransport by delegate {
                override suspend fun exchange(command: String, timeoutMs: Long): String {
                    if (command in listOf("0100", "0120", "0140")) {
                        val base = command.drop(2).toInt(16)
                        val codes = (Pid.entries.map { it.code } + listOf(0x20, 0x40)).toSet() - setOf(0x10, 0x5E)
                        val mask = (1..32).filter { base + it in codes }.fold(0L) { bits, bit -> bits or (1L shl (32 - bit)) }
                        return "7E8 06 41 %02X %s\r>".format(base, (3 downTo 0).joinToString(" ") { "%02X".format((mask shr (it * 8)) and 255) })
                    }
                    check(command !in listOf("0110", "015E"))
                    return delegate.exchange(command, timeoutMs)
                }
            }
        }
        c.startLive("00:11:22:33:44:55", false)
        advanceTimeBy(30_000); runCurrent()
        val s = c.state.value
        assertEquals(hu.elmdash.obd.Quality.UNSUPPORTED, s.telemetry.readings[Pid.MAF]?.quality)
        assertNotNull(s.telemetry.value(Pid.IAT, s.nowMs))
        assertEquals(FuelSource.MAP_ESTIMATE, s.fuel.source)
        assertTrue(s.fuelDisplay.fresh)
        assertNotNull(s.daily.averageL100)
        assertTrue(s.daily.containsEstimate)
        c.stop()
        assertTrue(c.store.days().values.single().containsEstimate)
    }

}
