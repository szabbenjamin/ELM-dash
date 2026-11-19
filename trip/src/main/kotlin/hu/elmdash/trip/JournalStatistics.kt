package hu.elmdash.trip

import java.time.*
import java.time.temporal.TemporalAdjusters

enum class StatisticsPeriod(val label: String) {
    DAY("Napi"), WEEK("Heti"), MONTH("Havi");
    fun start(date: LocalDate): LocalDate = when(this) {
        DAY -> date
        WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        MONTH -> date.withDayOfMonth(1)
    }
    fun shift(date: LocalDate, count: Long): LocalDate = when(this) {
        DAY -> date.plusDays(count)
        WEEK -> date.plusWeeks(count)
        MONTH -> date.plusMonths(count)
    }
}
data class StatisticsBucket(val start: LocalDate, val trips: Int, val refills: Int,
    val distanceKm: Double?, val consumedLiters: Double?, val averageL100: Double?,
    val pumpedLiters: Double?, val paidHuf: Double?, val incomplete: Boolean)

object JournalStatistics {
    /** A journey belongs to its local start date; no invented midnight split without samples. */
    fun buckets(log: JourneyLogState, period: StatisticsPeriod, end: LocalDate,
                zone: ZoneId = ZoneId.systemDefault(), count: Int = 12): List<StatisticsBucket> {
        require(count in 1..120)
        val journeys=(log.journeys+listOfNotNull(log.active)).distinctBy { it.id }
        fun day(ms: Long)=Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val grouped=journeys.groupBy { period.start(day(it.startedAtMs)) }
        val refills=log.refills.distinctBy { it.id }.groupBy { period.start(day(it.atMs)) }
        return (count-1 downTo 0).map { offset ->
            val start=period.shift(period.start(end),-offset.toLong())
            val trips=grouped[start].orEmpty(); val fills=refills[start].orEmpty()
            val fuelTrips=trips.filter { it.summary.coveredSeconds>0 }
            val pairedKm=fuelTrips.sumOf { it.summary.pairedDistanceKm }
            StatisticsBucket(start,trips.size,fills.size,
                trips.takeIf { it.isNotEmpty() }?.sumOf { it.summary.distanceKm },
                fuelTrips.takeIf { it.isNotEmpty() }?.sumOf { it.summary.fuelLiters },
                if(pairedKm>=0.1) fuelTrips.sumOf { it.summary.pairedFuelLiters }*100/pairedKm else null,
                fills.mapNotNull { it.pumpedLiters }.takeIf { it.isNotEmpty() }?.sum(),
                fills.mapNotNull { it.totalPaidHuf }.takeIf { it.isNotEmpty() }?.sum(),
                trips.any { it.summary.hasGaps || it.summary.containsEstimate || it.endedAtMs==null } ||
                    fills.any { it.pumpedLiters==null || it.totalPaidHuf==null })
        }
    }
}
