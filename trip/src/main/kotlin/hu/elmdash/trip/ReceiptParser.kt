package hu.elmdash.trip

import java.text.Normalizer

/** Conservative suggestions only. No receipt grand total is ever treated as fuel payment. */
data class ReceiptSuggestion(val fuel: String?, val liters: Double?, val paidHuf: Double?, val warnings: List<String>)
object ReceiptParser {
    private fun normalize(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    private val fuel = Regex("\\b(benzin|gasoline|petrol|diesel|gazolaj|evo\\s*95|evo\\s*100|fuelsave|v[ -]?power|super\\s*(95|100)|premium\\s*(95|100)|e10|e5)\\b")
    private val quantity = Regex("(?<![\\d.,])(\\d{1,2}(?:[,.]\\d{1,3})?)\\s*(?:liter|litre|lit|l|i)\\b")
    private val price = Regex("(?<![\\d.,])(\\d{3,4}(?:[,.]\\d{1,2})?)\\s*(?:ft\\s*/\\s*l|huf\\s*/\\s*l)")
    private val money = Regex("(?<![\\d.,])(-?\\d{1,3}(?:[ \\u00a0]\\d{3})+(?:[,.]\\d{1,2})?|-?\\d+(?:[,.]\\d{1,2})?)\\s*(?:ft|huf)\\b(?!\\s*/)")
    private fun number(s: String) = s.replace(" ", "").replace("\u00a0", "").replace(',', '.').toDoubleOrNull()
    fun parse(text: String): ReceiptSuggestion {
        val lines = text.take(50_000).lines().map { normalize(it.trim()) }.filter { it.isNotBlank() }
        val indices = lines.indices.filter { fuel.containsMatchIn(lines[it]) && !lines[it].contains("kedvezmeny") }
        val warnings = mutableListOf("Ellenőrizd a blokk üzemanyagsorát, a litert és a ténylegesen fizetett összeget.")
        if (indices.size != 1) return ReceiptSuggestion(null,null,null,warnings + "Nem egyértelmű az üzemanyagtétel (hiányzik vagy több van). Töltsd ki kézzel.")
        val index = indices.single()
        // Only the fuel line and immediately following quantity/price-only lines belong to the candidate.
        val block = mutableListOf(lines[index])
        for (line in lines.drop(index + 1).take(3)) {
            if (line.contains("osszes") || line.contains("fizet") || line.contains("kedvezmeny")) break
            if (!quantity.containsMatchIn(line) && !price.containsMatchIn(line) && !line.matches(Regex("[\\d., +x×*/-]+(?:ft|huf)?"))) break
            block += line
        }
        val joined = block.joinToString(" ")
        val quantities = quantity.findAll(joined).mapNotNull { number(it.groupValues[1]) }.filter { it > 0 && it <= 45 }.distinct().toList()
        val liters = quantities.singleOrNull()
        val unit = price.findAll(joined).mapNotNull { number(it.groupValues[1]) }.filter { it in 200.0..2000.0 }.distinct().singleOrNull()
        val amounts = money.findAll(joined).mapNotNull { number(it.groupValues[1]) }.filter { it > 0 }.distinct().toList()
        var paid = if (liters != null) amounts.filter { it / liters in 200.0..2000.0 }.singleOrNull() else null
        if (paid == null && liters != null && unit != null) { paid = liters * unit; warnings += "Az összeg a liter × egységár számítása; kerekítés eltérhet." }
        val discounts = lines.filter { it.contains("kedvezmeny") || it.contains("kupon") || it.contains("discount") || it.contains("engedmeny") }
        if (discounts.isNotEmpty()) {
            // Only explicit fuel-specific negative HUF discounts can be attributed automatically.
            val explicit = discounts.map { line ->
                val values = money.findAll(line).mapNotNull { number(it.groupValues[1]) }.toList()
                values.singleOrNull()?.takeIf { it < 0 && fuel.containsMatchIn(line) && !line.contains('%') }
            }
            if (paid != null && explicit.all { it != null } && !joined.contains("kedvezmeny")) {
                paid += explicit.filterNotNull().sum()
                warnings += "A külön megnevezett üzemanyag-kedvezményt levontuk. Ellenőrizd, hogy nem volt-e már a tételárban!"
            } else { paid = null; warnings += "A kedvezmény üzemanyagra jutó része bizonytalan. Add meg kézzel a kedvezmény utáni üzemanyagösszeget." }
        }
        if (liters == null) warnings += "A betöltött liter nem egyértelmű."
        if (paid != null && (liters == null || paid / liters !in 200.0..2000.0)) paid = null
        if (paid == null) warnings += "A blokk végösszegét nem vesszük át: más vásárlást is tartalmazhat."
        val label = lines[index].substringBefore(quantity.find(lines[index])?.value ?: "\u0000").trim().take(120)
        return ReceiptSuggestion(label,liters,paid,warnings)
    }
}
