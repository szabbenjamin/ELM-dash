package hu.elmdash.trip

import org.junit.Assert.*
import org.junit.Test

class FuelWindowTest {
    private fun f(rate: Double, speed: Double = 60.0, source: FuelSource = FuelSource.ECU) =
        FuelReading(rate, if (speed >= 5) rate * 100 / speed else null, source)

    @Test fun `a jump is spread across ten seconds and old samples expire`() {
        val w = FuelWindow()
        for (t in 0L..10_000L step 250L) w.update(f(3.0), 60.0, t)
        val first = w.update(f(6.0), 60.0, 10_250)
        assertEquals(5.0625, first.litersPer100Km!!, 1e-8)
        for (t in 10_500L..20_000L step 250L) w.update(f(6.0), 60.0, t)
        assertEquals(10.0, w.update(f(6.0), 60.0, 20_250).litersPer100Km!!, 1e-8)
    }
    @Test fun `distance weighted consumption does not average per100 ratios at different speeds`() {
        val w = FuelWindow()
        w.update(f(3.0, 30.0), 30.0, 0)
        val result = w.update(f(3.0, 90.0), 90.0, 1000)
        assertEquals(5.0, result.litersPer100Km!!, 1e-8) // 3 l/h over mean 60 km/h, not (10 + 3.33)/2.
    }
    @Test fun `idle uses hourly fuel and a missing interval greys rather than reusing average`() {
        val w = FuelWindow()
        w.update(f(1.0, 0.0), 0.0, 0)
        val idle = w.update(f(2.0, 0.0), 0.0, 1000)
        assertEquals(1.5, idle.litersPerHour!!, 1e-8); assertNull(idle.litersPer100Km)
        assertNull(w.update(FuelReading(null, null, FuelSource.UNAVAILABLE), 0.0, 1500).litersPerHour)
        assertEquals(4.0, w.update(f(4.0), 60.0, 2000).litersPerHour!!, 1e-8)
        assertNull(w.update(f(4.0), null, 2250).litersPerHour)
    }
    @Test fun `partial segment at boundary is clipped and duplicate timestamps add no weight`() {
        val w = FuelWindow(1500)
        w.update(f(0.0), 60.0, 0)
        w.update(f(2.0), 60.0, 1000)
        val a = w.update(f(4.0), 60.0, 2000)
        assertEquals(2.5, a.litersPerHour!!, 1e-8)
        assertEquals(a, w.update(f(4.0), 60.0, 2000))
    }
    @Test fun `pulling away has a finite ratio even while the window includes idle`() {
        val w = FuelWindow()
        w.update(f(1.0, 0.0), 0.0, 0)
        val moving = w.update(f(1.0, 6.0), 6.0, 1000)
        assertNotNull(moving.litersPer100Km)
        assertTrue(moving.litersPer100Km!!.isFinite())
    }
    @Test fun `outage reset and estimated sources are preserved without invented history`() {
        val w = FuelWindow()
        w.update(f(4.0, source = FuelSource.MAP_ESTIMATE), 60.0, 0)
        assertEquals(FuelSource.MAP_ESTIMATE, w.update(f(5.0), 60.0, 1000).source)
        assertEquals(9.0, w.update(f(9.0), 60.0, 5000).litersPerHour!!, 0.0)
        w.reset()
        assertEquals(2.0, w.update(f(2.0), 60.0, 6000).litersPerHour!!, 0.0)
    }
}
