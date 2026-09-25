package hu.elmdash.trip

import hu.elmdash.obd.*
import org.junit.Assert.*
import org.junit.Test

class MapFuelTest {
    private val settings = FuelSettings(FuelProfile.KALOS_12)
    private fun data(time: Long = 0, speed: Double = 60.0) = Telemetry(mapOf(
        Pid.RPM to Reading(2400.0, time, Quality.OK), Pid.MAP to Reading(50.0, time, Quality.OK),
        Pid.IAT to Reading(30.0, time, Quality.OK), Pid.SPEED to Reading(speed, time, Quality.OK),
        Pid.MAF to Reading(null, time, Quality.UNSUPPORTED), Pid.FUEL_RATE to Reading(null, time, Quality.UNSUPPORTED)))

    @Test fun `Kalos without MAF uses measured pressure temperature and RPM`() {
        val f = FuelCalculator.calculate(data(), 0, settings)
        // At 50 kPa / 303.15 K air density is 0.5746 kg/m³; 1.15 L × 20 cycles/s × 0.8 VE.
        assertEquals(FuelSource.MAP_ESTIMATE, f.source)
        assertEquals(3.4755, f.litersPerHour!!, 0.001)
        assertEquals(5.7925, f.litersPer100Km!!, 0.002)
        assertTrue(f.source.estimated)
        assertNull(f.unavailableReason)
        val idle = FuelCalculator.calculate(data(speed = 0.0), 0, settings)
        assertNotNull(idle.litersPerHour); assertNull(idle.litersPer100Km)
    }
    @Test fun `ECU and measured MAF take precedence over MAP estimate`() {
        val d = data()
        val maf = d.copy(readings = d.readings + (Pid.MAF to Reading(14.7, 0, Quality.OK)))
        assertEquals(FuelSource.MAF_ESTIMATE, FuelCalculator.calculate(maf, 0, settings).source)
        val ecu = maf.copy(readings = maf.readings + (Pid.FUEL_RATE to Reading(4.0, 0, Quality.OK)))
        assertEquals(FuelSource.ECU, FuelCalculator.calculate(ecu, 0, settings).source)
        assertNull(FuelCalculator.calculate(d, 0, FuelSettings()).litersPerHour)
        assertNull(FuelCalculator.calculate(d, 0, FuelSettings(FuelProfile.PETROL)).litersPerHour)
    }
    @Test fun `missing stale or unsupported MAP inputs never use a guessed temperature`() {
        for (pid in listOf(Pid.RPM, Pid.MAP, Pid.IAT)) {
            for (bad in listOf(Reading(null, 0, Quality.UNSUPPORTED), Reading(30.0, -6000, Quality.OK), Reading(Double.NaN, 0, Quality.OK))) {
                val d = data().let { it.copy(readings = it.readings + (pid to bad)) }
                val f = FuelCalculator.calculate(d, 0, settings)
                assertNull("$pid $bad", f.litersPerHour)
                assertNotNull(f.unavailableReason)
            }
        }
        val unsupported = data().let { it.copy(readings = it.readings + (Pid.IAT to Reading(null, 0, Quality.UNSUPPORTED))) }
        assertTrue(FuelCalculator.calculate(unsupported, 0, settings).unavailableReason!!.contains("IAT"))
    }
    @Test fun `VE and correction scale estimate and engine off stays zero`() {
        val rate = FuelCalculator.calculate(data(), 0, settings).litersPerHour!!
        assertEquals(rate * 1.5, FuelCalculator.calculate(data(), 0, settings.copy(volumetricEfficiency = 1.2)).litersPerHour!!, 1e-9)
        assertEquals(rate * 1.1, FuelCalculator.calculate(data(), 0, settings.copy(correction = 1.1)).litersPerHour!!, 1e-9)
        val off = data().let { it.copy(readings = it.readings + (Pid.RPM to Reading(0.0, 0, Quality.OK))) }
        assertEquals(0.0, FuelCalculator.calculate(off, 0, settings).litersPerHour!!, 0.0)
    }
    @Test fun `daily integrates MAP estimates without backfilling earlier missing fuel`() {
        val day = java.time.LocalDate.of(2026, 9, 16)
        val c = DailyComputer()
        for (time in 0L..20_000L step 250L) {
            val d = data(time)
            c.update(day, d, FuelReading(null, null, FuelSource.UNAVAILABLE), time)
        }
        c.gap()
        for (time in 21_000L..81_000L step 250L) {
            val d = data(time)
            c.update(day, d, FuelCalculator.calculate(d, time, settings), time)
        }
        val s = c.summary(day)
        assertTrue(s.containsEstimate); assertTrue(s.hasGaps)
        assertEquals(1.0, s.pairedDistanceKm, 1e-8)
        assertEquals(5.7925, s.averageL100!!, 0.002)
        assertTrue(s.distanceKm > s.pairedDistanceKm)
    }
}
