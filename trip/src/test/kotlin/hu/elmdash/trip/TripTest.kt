package hu.elmdash.trip

import hu.elmdash.obd.*
import org.junit.Assert.*
import org.junit.Test

class TripTest {
    private fun data(time: Long, speed: Double? = 60.0, rpm: Double = 1800.0, rate: Double? = 6.0, runtime: Double? = null, maf: Double? = null): Telemetry {
        val values = mapOf(Pid.SPEED to speed, Pid.RPM to rpm, Pid.FUEL_RATE to rate, Pid.RUN_TIME to runtime, Pid.MAF to maf)
        return Telemetry(values.mapValues { Reading(it.value, time, if (it.value == null) Quality.NO_DATA else Quality.OK) })
    }
    private fun update(computer: TripComputer, time: Long, data: Telemetry) = computer.update(data, FuelCalculator.calculate(data, time, FuelSettings()), time)

    @Test fun `constant drive integrates liters and distance independently of update frequency`() {
        val c = TripComputer()
        for (time in 0L..60_000L step 250L) update(c, time, data(time, runtime = time / 1000.0))
        assertEquals(1.0, c.summary.distanceKm, 0.000001)
        assertEquals(0.1, c.summary.fuelLiters, 0.000001)
        assertEquals(10.0, c.summary.averageL100!!, 0.000001)
        assertEquals(100.0, c.summary.coveragePercent, 0.000001)
        assertTrue(c.summary.fromEngineStart)
    }
    @Test fun `idle counts fuel but does not divide by zero`() {
        val c = TripComputer()
        for (time in 0L..60_000L step 1000L) update(c, time, data(time, speed = 0.0, rate = 0.6))
        assertEquals(0.01, c.summary.fuelLiters, 0.000001)
        assertNull(c.summary.averageL100)
        val f = FuelCalculator.calculate(data(0, speed = 0.0, rate = 0.6), 0, FuelSettings())
        assertNull(f.litersPer100Km)
        assertEquals(0.6, f.litersPerHour!!, 0.0)
    }
    @Test fun `MAF only estimates for explicitly selected gasoline profile`() {
        val d = data(0, rate = null, maf = 14.7)
        assertNull(FuelCalculator.calculate(d, 0, FuelSettings()).litersPerHour)
        val f = FuelCalculator.calculate(d, 0, FuelSettings(FuelProfile.PETROL))
        assertEquals(3600 / 745.0, f.litersPerHour!!, 0.00001)
        assertEquals(FuelSource.MAF_ESTIMATE, f.source)
        assertEquals(FuelSource.ECU, FuelCalculator.calculate(data(0, maf = 99.0), 0, FuelSettings(FuelProfile.PETROL)).source)
    }
    @Test fun `engine off and expired inputs`() {
        assertEquals(0.0, FuelCalculator.calculate(data(0, rpm = 0.0), 0, FuelSettings()).litersPerHour!!, 0.0)
        assertNull(FuelCalculator.calculate(data(0), 6_000, FuelSettings()).litersPerHour)
    }
    @Test fun `gaps are excluded and average uses matched distance`() {
        val c = TripComputer()
        for (time in 0L..60_000L step 1000L) update(c, time, data(time))
        val old = c.summary
        c.gap()
        update(c, 120_000, data(120_000))
        assertEquals(old.distanceKm, c.summary.distanceKm, 0.0)
        for (time in 121_000L..150_000L step 1000L) update(c, time, data(time, rate = null))
        assertTrue(c.summary.hasGaps)
        assertEquals(10.0, c.summary.averageL100!!, 0.000001)
        assertTrue(c.summary.coveragePercent < 100)
    }
    @Test fun `runtime restart resets but 16 bit wrap does not`() {
        val c = TripComputer()
        for (time in 0L..60_000L step 1000L) update(c, time, data(time, runtime = 100 + time / 1000.0))
        update(c, 61_000, data(61_000, runtime = 1.0))
        assertEquals(0.0, c.summary.distanceKm, 0.0)
        update(c, 62_000, data(62_000, runtime = 65535.0))
        val before = c.summary.distanceKm
        update(c, 63_000, data(63_000, runtime = 0.0))
        assertTrue(c.summary.distanceKm > before)
    }
    @Test fun `joining an already running engine is not labeled full trip`() {
        val c = TripComputer()
        update(c, 0, data(0, runtime = 500.0))
        assertFalse(c.summary.fromEngineStart)
    }
    @Test fun `long scheduler pause is not interpolated`() {
        val c = TripComputer()
        update(c, 0, data(0)); update(c, 10_000, data(10_000))
        assertEquals(0.0, c.summary.distanceKm, 0.0)
        assertTrue(c.summary.hasGaps)
    }
}
