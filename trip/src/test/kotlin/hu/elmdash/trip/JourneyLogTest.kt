package hu.elmdash.trip

import hu.elmdash.obd.*
import org.junit.Assert.*
import org.junit.Test

class JourneyLogTest {
    private val epoch = 1_789_552_800_000L
    private fun data(t: Long, speed: Double = 60.0, rpm: Double = 1800.0, runtime: Double? = null) = Telemetry(
        mapOf(Pid.RPM to rpm, Pid.SPEED to speed, Pid.COOLANT to 85.0, Pid.LOAD to 30.0, Pid.RUN_TIME to runtime)
            .mapValues { Reading(it.value, t, if (it.value == null) Quality.NO_DATA else Quality.OK) })
    private fun update(log: JourneyLog, t: Long, speed: Double = 60.0, rpm: Double = 1800.0, rate: Double? = 6.0,
        source: FuelSource = FuelSource.MAP_ESTIMATE, runtime: Double? = null) =
        log.update(data(t, speed, rpm, runtime), FuelReading(rate, rate?.times(100 / speed), source), t, epoch + t, "Kalos")

    @Test fun `receipt amount sets price for following trips without repricing old trips`() {
        val log=JourneyLog()
        log.fullTank(epoch,20.0,12000.0)
        update(log,0);update(log,1000)
        assertEquals(600.0,log.state.active!!.petrolPrice!!.hufPerLiter,0.0)
        assertEquals(PetrolPrice.MANUAL_SOURCE,log.state.active!!.petrolPrice!!.sourceUrl)
        log.fullTank(epoch+2000,10.0,7000.0)
        assertEquals(600.0,log.state.active!!.petrolPrice!!.hufPerLiter,0.0)
        log.finish(JourneyEnd.STOPPED,epoch+2000)
        update(log,3000)
        assertEquals(700.0,log.state.active!!.petrolPrice!!.hufPerLiter,0.0)
        assertEquals(600.0,log.state.journeys.single().petrolPrice!!.hufPerLiter,0.0)
        log.finish(JourneyEnd.STOPPED,epoch+4000)
        log.fullTank(epoch+5000)
        update(log,6000)
        assertEquals(700.0,log.state.active!!.petrolPrice!!.hufPerLiter,0.0)
        assertThrows(IllegalArgumentException::class.java) { log.fullTank(epoch,null,1000.0) }
        assertThrows(IllegalArgumentException::class.java) { log.fullTank(epoch,10.0,Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { log.fullTank(epoch,10.0,-1.0) }
    }
    @Test fun `tank is unknown until full then subtracts observed unsmoothed fuel exactly once`() {
        val log = JourneyLog()
        update(log, 0)
        assertNull(log.state.tank.percent)
        log.fullTank(epoch)
        for (t in 0L..60_000L step 250L) update(log, t)
        assertEquals(44.9, log.state.tank.remainingLiters!!, 1e-8)
        assertEquals(99.77777778, log.state.tank.percent!!, 1e-7)
        assertTrue(log.state.tank.containsEstimate)
        val before = log.state.tank.consumedLiters
        update(log, 60_000)
        assertEquals(before, log.state.tank.consumedLiters, 0.0)
    }
    @Test fun `full reset preserves journeys and records optional actual pump liters`() {
        val log = JourneyLog(); log.fullTank(epoch)
        for (t in 0L..60_000L step 1000L) update(log, t)
        log.finish(JourneyEnd.STOPPED, epoch + 60_000)
        val record = log.state.journeys.single()
        log.fullTank(epoch + 70_000, 23.4)
        assertEquals(100.0, log.state.tank.percent!!, 0.0)
        assertEquals(record, log.state.journeys.single())
        assertEquals(23.4, log.state.refills.first().pumpedLiters!!, 0.0)
        assertEquals(0.1, log.state.refills.first().previousConsumedLiters!!, 1e-8)
        assertFalse(log.state.tank.hasGaps)
        assertThrows(IllegalArgumentException::class.java) { log.fullTank(epoch, -1.0) }
        assertThrows(IllegalArgumentException::class.java) { log.fullTank(epoch, 46.0) }
        assertThrows(IllegalArgumentException::class.java) { log.fullTank(epoch, Double.NaN) }
    }
    @Test fun `trip records matched consumption speeds temperatures load and idle time`() {
        val log = JourneyLog()
        for (t in 0L..60_000L step 250L) update(log, t)
        val r = log.state.active!!
        assertEquals(1.0, r.summary.distanceKm, 1e-8)
        assertEquals(0.1, r.summary.fuelLiters, 1e-8)
        assertEquals(10.0, r.summary.averageL100!!, 1e-8)
        assertEquals(60.0, r.averageSpeed!!, 1e-8)
        assertEquals(60.0, r.stats.maxSpeed!!, 0.0)
        assertEquals(1800.0, r.stats.maxRpm!!, 0.0)
        assertEquals(85.0, r.stats.minCoolant!!, 0.0)
        assertEquals(30.0, r.stats.averageLoad!!, 1e-8)
        log.finish(JourneyEnd.STOPPED, epoch + 60_000)
        log.finish(JourneyEnd.STOPPED, epoch + 60_001)
        assertEquals(1, log.state.journeys.size)
        for (t in 61_000L..121_000L step 1000L) update(log, t, speed = 0.0, rate = 0.6)
        assertEquals(60.0, log.state.active!!.stats.idleSeconds, 1e-8)
        assertEquals(0.01, log.state.active!!.stats.idleFuelLiters, 1e-8)
        assertNull(log.state.active!!.summary.averageL100)
    }
    @Test fun `short reconnect stays one trip but tank gaps remain explicit`() {
        val log = JourneyLog(); log.fullTank(epoch)
        for (t in 0L..10_000L step 1000L) update(log, t)
        val id = log.state.active!!.id
        val fuel = log.state.tank.consumedLiters
        log.gap()
        log.update(Telemetry(), FuelReading(null, null, FuelSource.UNAVAILABLE), 20_000, epoch + 20_000, "Kalos")
        update(log, 30_000)
        assertEquals(id, log.state.active!!.id)
        assertTrue(log.state.active!!.summary.hasGaps)
        assertEquals(fuel, log.state.tank.consumedLiters, 0.0)
        assertTrue(log.state.tank.hasGaps)
    }
    @Test fun `missing fuel marks uncertain and no inferred fuel is subtracted`() {
        val log = JourneyLog(); log.fullTank(epoch)
        update(log, 0); update(log, 1000, rate = null)
        assertTrue(log.state.tank.hasGaps)
        assertEquals(45.0, log.state.tank.remainingLiters!!, 0.0)
        assertNull(log.state.active!!.summary.averageL100)
    }
    @Test fun `runtime restart splits trips but wraparound does not`() {
        val log = JourneyLog()
        for (t in 0L..10_000L step 1000L) update(log, t, runtime = 100.0 + t / 1000)
        val old = log.state.active!!.id
        update(log, 11_000, runtime = 1.0)
        assertEquals(old, log.state.journeys.single().id)
        assertEquals(JourneyEnd.ENGINE_RESTART, log.state.journeys.single().end)
        assertNotEquals(old, log.state.active!!.id)
        update(log, 12_000, runtime = 65535.0)
        update(log, 13_000, runtime = 0.0)
        assertEquals(1, log.state.journeys.size)
    }
    @Test fun `engine off and long disconnection close the active draft`() {
        val log = JourneyLog()
        update(log, 0)
        for (t in 1000L..9000L step 1000L) update(log, t, speed = 0.0, rpm = 0.0, rate = 0.0)
        assertNull(log.state.active)
        assertEquals(JourneyEnd.ENGINE_OFF, log.state.journeys.single().end)
        update(log, 10_000)
        log.gap()
        log.update(Telemetry(), FuelReading(null, null, FuelSource.UNAVAILABLE), 191_000, epoch + 191_000, "Kalos")
        assertNull(log.state.active)
        assertEquals(JourneyEnd.CONNECTION_LOST, log.state.journeys.first().end)
    }
    @Test fun `disconnect timeout waits three minutes and cannot archive twice`() {
        val log = JourneyLog()
        update(log, 0); update(log, 1000)
        log.gap()
        assertFalse(log.checkTimeout(180_999, epoch + 180_999))
        assertNotNull(log.state.active)
        assertTrue(log.checkTimeout(181_000, epoch + 181_000))
        assertFalse(log.checkTimeout(200_000, epoch + 200_000))
        assertEquals(1, log.state.journeys.size)
    }
    @Test fun `reconnect before deadline cancels the old timeout`() {
        val log = JourneyLog()
        update(log, 0); log.gap()
        val id = log.state.active!!.id
        update(log, 179_000)
        assertFalse(log.checkTimeout(181_000, epoch + 181_000))
        assertEquals(id, log.state.active!!.id)
    }
    @Test fun `restored draft is finalized once and never charged twice`() {
        val log = JourneyLog(); log.fullTank(epoch)
        update(log, 0); update(log, 1000)
        val checkpoint = log.state
        val restored = JourneyLog(checkpoint)
        assertNull(restored.state.active)
        assertEquals(JourneyEnd.APP_RESTART, restored.state.journeys.single().end)
        assertTrue(restored.state.tank.hasGaps)
        assertEquals(checkpoint.tank.consumedLiters, restored.state.tank.consumedLiters, 0.0)
        val again = JourneyLog(restored.state)
        assertEquals(restored.state, again.state)
        update(again, 0); update(again, 1000)
        assertEquals(checkpoint.tank.consumedLiters * 2, again.state.tank.consumedLiters, 1e-8)
    }
    @Test fun `remaining percentage is bounded and completed history is bounded`() {
        assertEquals(0.0, TankEstimate(fullAtMs = epoch, consumedLiters = 80.0).percent!!, 0.0)
        val log = JourneyLog()
        for (i in 0..205) { update(log, i * 1000L); log.finish(JourneyEnd.STOPPED, epoch + i * 1000L) }
        assertEquals(200, log.state.journeys.size)
        assertEquals(200, log.state.journeys.map { it.id }.distinct().size)
    }
    @Test fun `CSV includes precise UTC times source and missing average stays empty`() {
        val log = JourneyLog()
        update(log, 0, speed = 0.0)
        val csv = JourneyCsv.export(log.state)
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("indulás_UTC"))
        assertTrue(csv.contains("Folyamatban"))
        assertTrue(csv.contains("MAP-becslés"))
        assertFalse(csv.contains("null"))
        assertEquals(2, csv.trim().lines().size)
    }
    @Test fun `manual trip reset keeps continuous tank accounting without a false gap`() {
        val log = JourneyLog(); log.fullTank(epoch)
        update(log, 0); update(log, 1000)
        log.finish(JourneyEnd.RESET, epoch + 1000)
        update(log, 2000)
        assertEquals(6.0 * 2 / 3600, log.state.tank.consumedLiters, 1e-8)
        assertFalse(log.state.tank.hasGaps)
        assertEquals(1, log.state.journeys.size)
    }

}
