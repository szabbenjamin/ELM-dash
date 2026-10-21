package hu.elmdash.traffic

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.OffsetDateTime
import javax.xml.parsers.DocumentBuilderFactory

/** DATEX II 2.x SituationPublication, full snapshot, WGS84/ETRS89 point coordinates.
 * No fabricated decoding of TMC/OpenLR/linear geometries. Unsupported records are counted.
 */
object DatexParser {
    const val MAX_BYTES = 24 * 1024 * 1024
    fun parse(bytes: ByteArray): TrafficFeed {
        require(bytes.size <= MAX_BYTES) { "Feed too large" }
        // Do not resolve entities, DTDs or remote schemas, including on Android's XML implementation.
        require(!bytes.toString(Charsets.UTF_8).contains("<!DOCTYPE", true)) { "DTD forbidden" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true; isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw IllegalArgumentException("External entity forbidden") }
            setErrorHandler(object : org.xml.sax.helpers.DefaultHandler() {
                override fun error(e: org.xml.sax.SAXParseException) { throw e }
                override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
            })
        }
        // Only UTF-8 feeds are accepted, so alternative encodings cannot bypass the DTD guard.
        val input = bytes.toString(Charsets.UTF_8)
        require(!input.contains('\u0000') && !input.contains('\uFFFD'))
        val doc = builder.parse(ByteArrayInputStream(input.toByteArray(Charsets.UTF_8)))
        val root = doc.documentElement
        require(root.localName == "d2LogicalModel") { "DATEX II 2.x required" }
        val publications = root.all("payloadPublication")
        require(publications.size == 1 && publications.single().getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "type").substringAfter(':') == "SituationPublication")
        val publication = publications.single()
        require(publication.text("informationStatus").let { it.isBlank() || it == "real" }) { "Non-real publication" }
        require(publication.text("publicationType") !in setOf("delta", "deltaPublication")) { "Full snapshot required" }
        val published = time(publication.text("publicationTime")) ?: error("Missing publication time")
        val records = publication.all("situationRecord")
        require(records.size <= 20_000)
        var skipped = 0
        val events = records.mapNotNull { record ->
            try { event(record) ?: run { skipped++; null } } catch (_: IllegalArgumentException) { skipped++; null }
        }.distinctBy { it.id }
        return TrafficFeed(published, events, skipped)
    }
    private fun event(r: Element): TrafficEvent? {
        if (r.text("informationStatus").let { it.isNotBlank() && it != "real" }) return null
        // Recurring/exception windows need a dedicated schedule evaluator, not an overall-date guess.
        if (r.all("validPeriod").isNotEmpty() || r.all("exceptionPeriod").isNotEmpty()) return null
        val id = r.getAttribute("id").takeIf { it.isNotBlank() } ?: return null
        if (r.text("validityStatus") == "suspended" || r.text("probabilityOfOccurrence") in setOf("riskOf", "improbable")) return null
        // Coordinates must belong to a location, never to an unrelated extension or comment.
        val location = r.all("groupOfLocations").singleOrNull() ?: return null
        val display = location.all("locationForDisplay").singleOrNull()
        val coordinates = display ?: location.all("pointCoordinates").singleOrNull() ?: return null
        val point = GeoPoint(coordinates.text("latitude").toDoubleOrNull() ?: return null,
            coordinates.text("longitude").toDoubleOrNull() ?: return null)
        val type = r.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "type").substringAfter(':')
        val (label, priority) = when (type) {
            "Accident" -> "baleset" to 3
            "RoadOrCarriagewayOrLaneManagement" -> "korlátozás" to 3
            "Obstruction", "GeneralObstruction", "VehicleObstruction", "AnimalPresenceObstruction", "EnvironmentalObstruction" -> "akadály" to 3
            "AbnormalTraffic" -> "torlódás" to 2
            "Roadworks", "ConstructionWorks", "MaintenanceWorks" -> "útmunka" to 1
            "PoorEnvironmentConditions", "WeatherRelatedRoadConditions" -> "útviszonyok" to 2
            else -> return null
        }
        val startText = r.text("overallStartTime")
        val endText = r.text("overallEndTime")
        val start = time(startText); val end = time(endText)
        if (startText.isNotBlank() && start == null || endText.isNotBlank() && end == null) return null
        val directions = mapOf("northBound" to 0.0, "northEastBound" to 45.0, "eastBound" to 90.0, "southEastBound" to 135.0,
            "southBound" to 180.0, "southWestBound" to 225.0, "westBound" to 270.0, "northWestBound" to 315.0)
        val bearing = location.text("bearing").toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..<360.0 }
            ?: directions[location.text("directionBoundOnRoad")]
        val road = location.text("roadNumber").replace(Regex("[\\p{Cntrl}]"), "").take(12)
        return TrafficEvent(id.take(256), point, road, label, bearing, start, end, priority)
    }
    private fun time(s: String): Instant? = runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()
    private fun Element.all(name: String): List<Element> {
        val list = getElementsByTagNameNS("*", name)
        return (0 until list.length).map { list.item(it) as Element }
    }
    private fun Element.text(name: String) = all(name).firstOrNull()?.textContent?.trim().orEmpty()
}
