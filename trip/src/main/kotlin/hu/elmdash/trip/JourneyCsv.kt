package hu.elmdash.trip

import java.time.Instant

object JourneyCsv {
    fun export(log: JourneyLogState): String {
        val rows = listOf(listOf("azonosító", "indulás_UTC", "befejezés_UTC", "állapot", "km", "liter", "l_100km",
            "időtartam_mp", "átlagsebesség_kmh", "max_sebesség_kmh", "alapjárat_mp", "alapjárat_liter", "max_rpm",
            "min_víz_C", "max_víz_C", "átlag_terhelés_százalék", "lefedettség_százalék", "részleges", "becslés", "forrás", "profil", "becsült_költség_Ft", "benzinár_Ft_l", "ár_lekérve_UTC", "mentett_ár", "árforrás", "utólag_rögzített_ár")) +
            (listOfNotNull(log.active) + log.journeys).map { r ->
                val t = r.summary; val s = r.stats
                listOf(r.id, Instant.ofEpochMilli(r.startedAtMs), r.endedAtMs?.let { Instant.ofEpochMilli(it) },
                    r.end?.label ?: "Folyamatban", t.distanceKm, t.fuelLiters, t.averageL100, r.durationSeconds,
                    r.averageSpeed, s.maxSpeed, s.idleSeconds, s.idleFuelLiters, s.maxRpm, s.minCoolant, s.maxCoolant,
                    s.averageLoad, t.coveragePercent, t.hasGaps, t.containsEstimate, r.sources.joinToString { it.label }, r.profile, r.estimatedCostHuf, r.petrolPrice?.hufPerLiter,
                    r.petrolPrice?.let { Instant.ofEpochMilli(it.fetchedAtMs) }, r.petrolPrice?.cached, r.petrolPrice?.sourceUrl, r.petrolPrice?.appliedAfterStart)
            }
        return "\uFEFF" + rows.joinToString("\r\n", postfix = "\r\n") { row ->
            row.joinToString(";") { value -> "\"" + (value?.toString() ?: "").replace("\"", "\"\"") + "\"" }
        }
    }
}
