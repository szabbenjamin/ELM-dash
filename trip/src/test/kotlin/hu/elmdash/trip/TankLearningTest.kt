package hu.elmdash.trip
import org.junit.Assert.*
import org.junit.Test
class TankLearningTest {
    private fun refill(raw: Double, actual: Double, factor: Double=1.0, gaps: Boolean=false): TankEstimate {
        val log=JourneyLog(JourneyLogState(tank=TankEstimate(fullAtMs=1,consumedLiters=raw,hasGaps=gaps,learnedFactor=factor)))
        log.fullTank(2,actual)
        return log.state.tank
    }
    @Test fun learnsUnderestimateWithoutCompoundingOrReducing() {
        assertEquals(34.0/31.3,refill(31.3,34.0).learnedFactor,1e-9)
        assertEquals(1.2,refill(30.0,33.0,1.2).learnedFactor,0.0)
        assertEquals(1.1,refill(30.0,33.0,1.1).learnedFactor,0.0)
    }
    @Test fun rejectsGapsSmallTopupsAndOutliers() {
        for(t in listOf(refill(30.0,33.0,gaps=true),refill(5.0,6.0),refill(10.0,40.0))) {
            assertEquals(1.0,t.learnedFactor,0.0); assertEquals(0,t.learningSamples)
        }
    }
    @Test fun reserveNeverInventsFuelAndDoesNotAlterRawCalibrationEvidence() {
        val t=TankEstimate(fullAtMs=1,consumedLiters=30.0,learnedFactor=1.1)
        assertEquals(8.35,t.remainingLiters!!,1e-9)
        assertEquals(30.0,t.consumedLiters,0.0)
        assertTrue(t.copy(hasGaps=true).remainingLiters!! < t.remainingLiters!!)
        assertEquals(0.0,t.copy(consumedLiters=100.0).remainingLiters!!,0.0)
        assertNull(TankEstimate().remainingLiters)
    }
}
