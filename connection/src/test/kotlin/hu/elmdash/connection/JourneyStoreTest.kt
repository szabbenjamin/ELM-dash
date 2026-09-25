package hu.elmdash.connection

import hu.elmdash.trip.*
import hu.elmdash.obd.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class JourneyStoreTest {
    @Test fun `manual paid amount persists and prices next real journey without web lookup`() = runTest {
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences("elm-journal",0).edit().clear().commit()
        val c=DashboardController(context,{currentTime},backgroundScope,wallClock={100_000+currentTime}) { _,_,_-> DemoTransport {currentTime} }
        c.markFullTank(20.0,12000.0)
        assertEquals(12000.0,JourneyStore(context).load().refills.single().totalPaidHuf!!,0.0)
        c.startLive("00:11:22:33:44:55",false)
        advanceTimeBy(30_000);runCurrent()
        val record=c.state.value.journal.active!!
        assertEquals(600.0,record.petrolPrice!!.hufPerLiter,0.0)
        assertEquals(record.summary.fuelLiters*600.0,record.estimatedCostHuf!!,1e-8)
        c.stop()
        val saved=JourneyStore(context).load()
        assertEquals(PetrolPrice.MANUAL_SOURCE,saved.journeys.single().petrolPrice!!.sourceUrl)
        assertEquals(12000.0,saved.refills.single().totalPaidHuf!!,0.0)
    }
    @Test fun `one snapshot round trips active draft tank refills nullable stats and history`() {
        val store = JourneyStore(RuntimeEnvironment.getApplication())
        val record = JourneyRecord("one", 1000, 2000, summary = TripSummary(started = true, distanceKm = 1.0, containsEstimate = true),
            sources = setOf(FuelSource.MAP_ESTIMATE), profile = "Kalos")
        val state = JourneyLogState(TankEstimate(fullAtMs = 1000, consumedLiters = 1.2), listOf(record.copy(id = "ended", endedAtMs = 2000, end = JourneyEnd.STOPPED)),
            listOf(FullRefill("full", 1000, 24.5)), record)
        store.save(state)
        assertEquals(state, store.load())
        val restored = JourneyLog(store.load())
        store.save(restored.state)
        assertEquals(2, JourneyLog(store.load()).state.journeys.size)
    }
    @Test fun `demo never writes a journey or consumes the real tank`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("elm-journal", 0).edit().clear().commit()
        val c = DashboardController(context, { currentTime }, backgroundScope, wallClock = { 100_000 + currentTime })
        c.markFullTank(12.0)
        val initial = c.state.value.journal
        c.startDemo(); advanceTimeBy(30_000); runCurrent()
        assertEquals(initial, c.state.value.journal)
        assertThrows(IllegalStateException::class.java) { c.markFullTank() }
        c.stop()
        assertEquals(initial, JourneyStore(context).load())
    }
    @Test fun `real controller saves trip and tank across reset and reopen without erasing daily totals`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("elm-journal", 0).edit().clear().commit()
        context.getSharedPreferences("elm-dashboard", 0).edit().clear().commit()
        val c = DashboardController(context, { currentTime }, backgroundScope, wallClock = { 100_000 + currentTime }) { _, _, _ -> DemoTransport { currentTime } }
        c.markFullTank()
        c.startLive("00:11:22:33:44:55", false)
        advanceTimeBy(30_000); runCurrent()
        assertTrue(c.state.value.journal.active!!.summary.distanceKm > 0.1)
        assertTrue(c.state.value.journal.tank.percent!! < 100)
        val daily = c.state.value.daily
        c.resetTrip()
        assertEquals(daily, c.state.value.daily)
        assertEquals(1, c.state.value.journal.journeys.size)
        advanceTimeBy(15_000); runCurrent(); c.stop()
        val saved = JourneyStore(context).load()
        assertEquals(2, saved.journeys.size)
        assertNull(saved.active)
        assertTrue(saved.tank.consumedLiters > 0)
        val reopened = DashboardController(context, { currentTime }, backgroundScope)
        assertEquals(saved, reopened.state.value.journal)
        val tank = saved.tank
        reopened.markFullTank(10.0)
        assertEquals(saved.journeys, reopened.state.value.journal.journeys)
        assertEquals(tank.consumedLiters, reopened.state.value.journal.refills.first().previousConsumedLiters!!, 0.0)
        assertEquals(c.state.value.daily, reopened.state.value.daily)
    }
}
