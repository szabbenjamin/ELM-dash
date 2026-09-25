package hu.elmdash.trip

import hu.elmdash.obd.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DailyComputerTest {
    private val day = LocalDate.of(2026, 9, 16)
    private fun data(time: Long, runtime: Double = 100.0, speed: Double = 60.0) = Telemetry(mapOf(
        Pid.RPM to Reading(2000.0, time, Quality.OK), Pid.SPEED to Reading(speed, time, Quality.OK),
        Pid.RUN_TIME to Reading(runtime, time, Quality.OK)))
    private val fuel = FuelReading(6.0, 10.0, FuelSource.ECU)
    private fun segment(c: DailyComputer, date: LocalDate, start: Long, seconds: Int = 60) {
        for (i in 0..seconds) c.update(date, data(start + i * 1000), fuel, start + i * 1000)
    }
    @Test fun `same day resumes from saved totals and weights distance instead of averages`() {
        val c = DailyComputer(); segment(c, day, 0)
        val restored = DailyComputer(c.snapshot())
        val other = FuelReading(3.0, 5.0, FuelSource.ECU)
        for (i in 0..120) restored.update(day, data(i * 1000L), other, i * 1000L)
        val result = restored.summary(day)
        assertEquals(3.0, result.distanceKm, 0.00001)
        assertEquals(0.2, result.fuelLiters, 0.00001)
        assertEquals(6.6666667, result.averageL100!!, 0.00001)
    }
    @Test fun `engine restart never erases earlier daily driving`() {
        val c = DailyComputer(); segment(c, day, 0)
        c.update(day, data(61_000, runtime = 0.0), fuel, 61_000)
        assertEquals(1.0, c.summary(day).distanceKm, 0.00001)
        c.update(day, data(62_000, runtime = 1.0), fuel, 62_000)
        assertTrue(c.summary(day).distanceKm > 1.0)
    }
    @Test fun `midnight separates days without interpolating across the boundary`() {
        val c = DailyComputer(); segment(c, day, 0)
        c.update(day.plusDays(1), data(61_000), fuel, 61_000)
        assertEquals(0.0, c.summary(day.plusDays(1)).distanceKm, 0.0)
        assertEquals(1.0, c.summary(day).distanceKm, 0.00001)
        assertTrue(c.summary(day).hasGaps)
        assertNull(c.summary(day.plusDays(1)).averageL100)
    }
    @Test fun `idle fuel counts and a missing section is never imputed`() {
        val c = DailyComputer(); segment(c, day, 0)
        c.gap()
        val idleFuel = FuelReading(0.6, null, FuelSource.ECU)
        for (i in 0..60) c.update(day, data(100_000L+i*1000, speed=0.0), idleFuel, 100_000L+i*1000)
        assertEquals(1.0, c.summary(day).distanceKm, 0.00001)
        assertEquals(0.11, c.summary(day).fuelLiters, 0.00001)
        assertEquals(11.0, c.summary(day).averageL100!!, 0.00001)
        assertTrue(c.summary(day).hasGaps)
    }
}
