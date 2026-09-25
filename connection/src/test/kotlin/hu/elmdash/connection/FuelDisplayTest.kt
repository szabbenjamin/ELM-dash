package hu.elmdash.connection

import hu.elmdash.trip.FuelReading
import hu.elmdash.trip.FuelSource
import org.junit.Assert.*
import org.junit.Test

class FuelDisplayTest {
    @Test fun `missing fuel preserves number and unit but marks stale`() {
        val display = FuelDisplay().update(FuelReading(4.2, 7.0, FuelSource.ECU), 60.0)
        val stale = display.update(FuelReading(null, null, FuelSource.UNAVAILABLE), 60.0)
        assertEquals(7.0, stale.value!!, 0.0)
        assertEquals("l/100 km", stale.unit)
        assertFalse(stale.fresh)
    }
    @Test fun `missing speed cannot switch units or replace number`() {
        val display = FuelDisplay().update(FuelReading(4.2, 7.0, FuelSource.ECU), 60.0)
        val noSpeed = display.update(FuelReading(4.3, null, FuelSource.ECU), null)
        assertEquals(display.value, noSpeed.value)
        assertEquals(display.unit, noSpeed.unit)
        assertFalse(noSpeed.fresh)
        val stopped = noSpeed.update(FuelReading(0.8, null, FuelSource.ECU), 0.0)
        assertEquals("l/100 km", stopped.unit)
        assertNull(stopped.value)
        assertFalse(stopped.fresh)
    }
    @Test fun `hourly rate is never displayed as per100 and drive resumes with a per100 value`() {
        var d = FuelDisplay()
        for (speed in listOf(0.0, 1.0, 4.9)) {
            d = d.update(FuelReading(0.8, null, FuelSource.MAP_ESTIMATE), speed)
            assertNull(d.value); assertEquals("l/100 km", d.unit)
        }
        d = d.update(FuelReading(4.0, 8.0, FuelSource.MAP_ESTIMATE), 50.0)
        assertEquals(8.0, d.value!!, 0.0); assertTrue(d.fresh)
        assertFalse(d.update(FuelReading(4.0, null, FuelSource.ECU), 50.0).fresh)
    }

}
