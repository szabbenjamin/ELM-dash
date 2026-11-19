package hu.elmdash.trip

import org.junit.Assert.*
import org.junit.Test

class RangeEstimateTest {
    private val tank = TankEstimate(fullAtMs = 1, consumedLiters = 15.0)
    private fun summary(km: Double, liters: Double) = TripSummary(pairedDistanceKm = km, pairedFuelLiters = liters)
    private fun record(id: String, s: TripSummary) = JourneyRecord(id, 0, 1, endedAtMs = 1, summary = s)
    @Test fun rangeColorsUseStrictDistanceThresholdsNotUncertainty() {
        fun level(km: Double?) = RangeEstimate(km, 7.0, "test", true).level
        assertEquals(RangeLevel.UNKNOWN, level(null))
        assertEquals(RangeLevel.NORMAL, level(150.0))
        assertEquals(RangeLevel.NORMAL, level(250.0))
        assertEquals(RangeLevel.GOOD, level(250.01))
        assertEquals(RangeLevel.LOW, level(149.99))
        assertEquals(RangeLevel.LOW, level(100.0))
        assertEquals(RangeLevel.CRITICAL, level(99.99))
        assertEquals(RangeLevel.CRITICAL, level(0.0))
    }
    @Test fun dailyRange() {
        val r = RangeEstimate.calculate(tank, summary(100.0, 6.0), emptyList())
        assertEquals(27.25 / 6 * 100, r.km!!, 1e-8); assertEquals("mai átlag",r.basis)
    }
    @Test fun historyIsWeightedAndSurvivesMidnight() {
        val r = RangeEstimate.calculate(tank, TripSummary(), listOf(record("a",summary(10.0,1.0)),record("b",summary(90.0,5.0))))
        assertEquals(6.0,r.averageL100!!,1e-8); assertEquals(27.25 / 6 * 100,r.km!!,1e-8)
    }
    @Test fun dailyOverridesHistory() {
        assertEquals(272.5, RangeEstimate.calculate(tank, summary(10.0,1.0), listOf(record("a",summary(100.0,5.0)))).km!!,1e-8)
    }
    @Test fun missingTankOrAverageNotInvented() {
        assertNull(RangeEstimate.calculate(TankEstimate(),summary(100.0,6.0),emptyList()).km)
        for (s in listOf(summary(0.5,0.1),summary(100.0,0.0),summary(100.0,Double.NaN)))
            assertNull(RangeEstimate.calculate(tank,s,emptyList()).km)
    }
    @Test fun gapsRemainExplicitAndEmptyTankIsZero() {
        assertTrue(RangeEstimate.calculate(tank.copy(hasGaps = true),summary(100.0,6.0),emptyList()).uncertain)
        assertEquals(0.0,RangeEstimate.calculate(tank.copy(consumedLiters = 45.0),summary(100.0,6.0),emptyList()).km!!,0.0)
    }
    @Test fun unpairedDistanceDoesNotInflateRange() {
        assertEquals(27.25 / 6 * 100,RangeEstimate.calculate(tank,summary(100.0,6.0).copy(distanceKm = 500.0,hasGaps = true),emptyList()).km!!,1e-8)
    }
}
