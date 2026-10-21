package hu.elmdash.trip
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class MaintenanceTest {
    private val date = LocalDate.of(2025,10,21)
    private val entry = ServiceEntry("1","Olaj",date,100000.0,10000.0,12)
    @Test fun eitherLimitCanTriggerIndependently() {
        assertTrue(entry.due(date.plusMonths(12),null))
        assertTrue(entry.due(date.plusMonths(2),110000.0))
        assertFalse(entry.due(date.plusMonths(2),109999.0))
        assertFalse(entry.due(date.plusMonths(2),null))
        assertTrue(entry.soon(date.plusMonths(2),109500.0))
    }
    @Test fun fuelComparisonCannotCalibrateMissingObservations() {
        val full = FullRefill("x",1,30.0,25.0,false,18000.0)
        assertEquals(5.0,full.differenceLiters!!,0.0)
        assertEquals(20.0,full.differencePercent!!,0.0)
        assertEquals(1.2,full.suggestedMultiplier!!,0.0)
        assertNull(full.copy(previousHasGaps=true).suggestedMultiplier)
        assertNull(full.copy(previousConsumedLiters=null).differenceLiters)
        assertNull(full.copy(previousConsumedLiters=0.0).differencePercent)
    }
}
