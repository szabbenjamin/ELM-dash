package hu.elmdash.trip

/** Price observed at trip start, not a later revaluation or the user's actual receipt price. */
data class PetrolPrice(val hufPerLiter: Double, val fetchedAtMs: Long, val cached: Boolean = false,
    val sourceUrl: String = SOURCE_URL, val appliedAfterStart: Boolean = false) {
    init { require(hufPerLiter.isFinite() && hufPerLiter in 200.0..2000.0); require(fetchedAtMs >= 0) }
    companion object { const val MANUAL_SOURCE = "user-refill"; const val SOURCE_URL = "https://holtankoljak.hu/" }
}

/** Deliberately strict: isolate regular 95 E10 before interpreting the average, never minimum/diesel/premium. */
object PetrolPriceParser {
    fun parse(html: String): Double? {
        val marker = Regex("<img\\b[^>]*src=[\"'][^\"']*/95-benzin-e10\\.png[\"'][^>]*>", RegexOption.IGNORE_CASE)
        val start = marker.find(html)?.range?.last?.plus(1) ?: return null
        val end = Regex("<img\\b", RegexOption.IGNORE_CASE).find(html, start)?.range?.first ?: html.length
        val block = html.substring(start, minOf(end, start + 4000))
            .replace("&Aacute;", "Á").replace("&#193;", "Á").replace("&nbsp;", " ")
        val matches = Regex("Átlag\\s*-\\s*Ft/l\\s*<br\\s*/?>\\s*<span\\s+class=[\"']ar[\"']\\s*>\\s*([0-9]+(?:[.,][0-9]+)?)\\s*</span>")
            .findAll(block).toList()
        if (matches.size != 1) return null
        return matches.single().groupValues[1].replace(',', '.').toDoubleOrNull()?.takeIf { it in 200.0..2000.0 }
    }
}
