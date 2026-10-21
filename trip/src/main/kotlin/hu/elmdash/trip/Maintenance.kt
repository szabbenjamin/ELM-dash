package hu.elmdash.trip

import java.time.LocalDate

data class ServiceEntry(val id: String, val name: String, val performed: LocalDate, val odometerKm: Double,
    val intervalKm: Double?, val intervalMonths: Int?, val note: String = "") {
    init {
        require(name.isNotBlank() && name.length <= 100)
        require(odometerKm.isFinite() && odometerKm >= 0)
        require(intervalKm == null || intervalKm.isFinite() && intervalKm > 0)
        require(intervalMonths == null || intervalMonths in 1..240)
    }
    fun due(today: LocalDate, currentKm: Double?): Boolean =
        intervalMonths?.let { !today.isBefore(performed.plusMonths(it.toLong())) } == true ||
        (currentKm != null && intervalKm?.let { currentKm >= odometerKm + it } == true)
    fun soon(today: LocalDate, currentKm: Double?): Boolean =
        intervalMonths?.let { !today.plusDays(30).isBefore(performed.plusMonths(it.toLong())) } == true ||
        (currentKm != null && intervalKm?.let { currentKm + 500 >= odometerKm + it } == true)
}
