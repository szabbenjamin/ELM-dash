package hu.elmdash.trip

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class JournalStatisticsTest {
    private val zone=ZoneId.of("Europe/Budapest")
    private fun at(s:String)=LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()
    @Test fun localDayWeekMonthAndWeightedConsumption() {
        val a=JourneyRecord("a",at("2026-10-25T23:50:00"),at("2026-10-26T00:20:00"),endedAtMs=at("2026-10-26T00:20:00"),summary=TripSummary(distanceKm=10.0,fuelLiters=1.0,pairedDistanceKm=10.0,pairedFuelLiters=1.0,coveredSeconds=60.0))
        val b=a.copy(id="b",summary=a.summary.copy(distanceKm=90.0,fuelLiters=4.5,pairedDistanceKm=90.0,pairedFuelLiters=4.5))
        val log=JourneyLogState(journeys=listOf(a,b),refills=listOf(FullRefill("f",at("2026-10-26T00:01:00"),20.0,totalPaidHuf=12000.0)))
        val days=JournalStatistics.buckets(log,StatisticsPeriod.DAY,LocalDate.parse("2026-10-26"),zone,2)
        assertEquals(100.0,days[0].distanceKm!!,0.0);assertEquals(5.5,days[0].averageL100!!,0.0)
        assertNull(days[1].consumedLiters);assertEquals(20.0,days[1].pumpedLiters!!,0.0)
        assertEquals(LocalDate.parse("2026-10-19"),StatisticsPeriod.WEEK.start(LocalDate.parse("2026-10-25")))
        assertEquals(LocalDate.parse("2026-10-01"),StatisticsPeriod.MONTH.start(LocalDate.parse("2026-10-25")))
        val month=JournalStatistics.buckets(log,StatisticsPeriod.MONTH,LocalDate.parse("2026-10-26"),zone,1).single()
        assertEquals(12000.0,month.paidHuf!!,0.0);assertEquals(2,month.trips)
    }
    @Test fun missingRefillDataIsNotZeroAndActiveIsDeduplicated() {
        val r=JourneyRecord("a",at("2026-10-26T12:00:00"),at("2026-10-26T12:01:00"))
        val log=JourneyLogState(journeys=listOf(r),active=r,refills=listOf(FullRefill("f",r.startedAtMs)))
        val bucket=JournalStatistics.buckets(log,StatisticsPeriod.DAY,LocalDate.parse("2026-10-26"),zone,1).single()
        assertEquals(1,bucket.trips);assertNull(bucket.pumpedLiters);assertNull(bucket.paidHuf);assertNull(bucket.averageL100);assertTrue(bucket.incomplete)
    }
}
