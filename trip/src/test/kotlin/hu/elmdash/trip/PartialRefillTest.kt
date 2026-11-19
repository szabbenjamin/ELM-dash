package hu.elmdash.trip
import org.junit.Assert.*
import org.junit.Test
class PartialRefillTest {
    @Test fun partialAddsFuelWithoutResettingConsumptionOrLearning() {
        val log=JourneyLog(JourneyLogState(tank=TankEstimate(fullAtMs=1,consumedLiters=30.0)))
        val before=log.state.tank.remainingLiters!!
        log.partialTank(2,10.0,6000.0,"95",null)
        assertEquals(before+10,log.state.tank.remainingLiters!!,1e-9)
        assertEquals(30.0,log.state.tank.consumedLiters,0.0)
        assertEquals(0,log.state.tank.learningSamples)
        assertFalse(log.state.refills.first().isFull)
        assertNull(log.state.refills.first().differenceLiters)
        log.fullTank(3,23.0)
        assertEquals(1.1,log.state.tank.learnedFactor,1e-9)
        assertEquals(33.0,log.state.refills.first().cyclePumpedLiters!!,0.0)
        assertEquals(0.0,log.state.tank.addedLiters,0.0)
    }
    @Test fun partialWithoutAnchorDoesNotInventLevelOrCalibration() {
        val log=JourneyLog()
        log.partialTank(1,10.0,null,null,null)
        assertNull(log.state.tank.percent)
        log.fullTank(2,30.0)
        assertEquals(0,log.state.tank.learningSamples)
        assertEquals(1.0,log.state.tank.learnedFactor,0.0)
    }
}
