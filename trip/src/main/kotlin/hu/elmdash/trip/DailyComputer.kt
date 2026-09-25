package hu.elmdash.trip

import hu.elmdash.obd.Telemetry
import java.time.LocalDate

/** Local calendar days, summed from the same valid samples as trips; never average averages. */
class DailyComputer(initial: Map<LocalDate, TripSummary> = emptyMap()) {
    private val days = initial.toMutableMap()
    private var date: LocalDate? = null
    private var computer: TripComputer? = null

    fun summary(day: LocalDate): TripSummary = if (day == date) computer!!.summary else days[day] ?: TripSummary()

    fun update(day: LocalDate, data: Telemetry, fuel: FuelReading, nowMs: Long): TripSummary {
        if (day != date) {
            gap()
            date = day
            computer = TripComputer(days[day] ?: TripSummary(), resetOnEngineRestart = false)
        }
        val result = computer!!.update(data, fuel, nowMs)
        days[day] = result
        days.keys.sortedDescending().drop(31).forEach { days.remove(it) }
        return result
    }

    fun gap() {
        computer?.gap()
        date?.let { days[it] = computer!!.summary }
    }

    fun snapshot(): Map<LocalDate, TripSummary> = days.toMap()
}
