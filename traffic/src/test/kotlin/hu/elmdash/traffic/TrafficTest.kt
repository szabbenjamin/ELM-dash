package hu.elmdash.traffic

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TrafficTest {
    private val now = Instant.parse("2026-10-19T12:00:00Z")
    private val origin = GeoPoint(47.0, 19.0)
    private fun event(id: String = "one", lat: Double = 47.01, bearing: Double? = null) =
        TrafficEvent(id, GeoPoint(lat, 19.0), "M3", "baleset", bearing, null, null, 3)
    private fun matches(events: List<TrafficEvent>, heading: Double? = 0.0, age: Long = 0, published: Instant = now) =
        TrafficFilter.nearby(TrafficFeed(published, events, 0), TrafficFix(origin, 5.0, heading, 20.0, 100_000 - age), now, 100_000)
    @Test fun aheadAndBehind() { assertEquals(listOf("one"), matches(listOf(event(), event("behind", 46.99))).map { it.event.id }) }
    @Test fun opposingTrafficExcluded() { assertTrue(matches(listOf(event(bearing = 180.0))).isEmpty()) }
    @Test fun unknownHeadingUsesNearbyNotDirection() { assertEquals(2, matches(listOf(event(), event("behind", 46.99)), null).size) }
    @Test fun staleGpsAndFeedAreNotTrafficClear() {
        assertTrue(matches(listOf(event()), age = 20_001).isEmpty())
        assertTrue(matches(listOf(event()), published = now.minusSeconds(901)).isEmpty())
        assertTrue(matches(listOf(event()), published = now.plusSeconds(121)).isEmpty())
    }
    @Test fun validityWindows() {
        assertTrue(matches(listOf(event().copy(end = now), event("future").copy(start = now.plusSeconds(1)))).isEmpty())
    }
    @Test fun inaccurateLocationExcluded() {
        assertTrue(TrafficFilter.nearby(TrafficFeed(now, listOf(event()), 0), TrafficFix(origin, 101.0, 0.0, 10.0, 0), now, 0).isEmpty())
    }
    @Test fun geometryAndBearingWrap() {
        assertEquals(1112.0, origin.distance(GeoPoint(47.01,19.0)), 1.0)
        assertEquals(2.0, TrafficFilter.angle(359.0,1.0), 0.001)
    }
    @Test fun displaySlotAndCooldown() {
        val p = TrafficPresenter(); val match = TrafficMatch(event(), 1000.0)
        assertNotNull(p.line(listOf(match),0)); assertNotNull(p.line(listOf(match),19_999))
        assertNull(p.line(listOf(match),20_000)); assertNull(p.line(listOf(match),60_000))
        assertNotNull(p.line(listOf(match),1_800_000))
    }
    @Test fun displayDoesNotChurnWithEachGpsFix() {
        val p = TrafficPresenter()
        val line = p.line(listOf(TrafficMatch(event(), 2500.0)), 0)
        assertEquals(line, p.line(listOf(TrafficMatch(event(), 1500.0)), 10_000))
    }
    @Test fun disappearingMatchClearsImmediately() {
        val p = TrafficPresenter(); p.line(listOf(TrafficMatch(event(), 1000.0)),0)
        assertNull(p.line(emptyList(), 1))
    }
    private fun xml(record: String = record(), time: String = now.toString()) = """
        <d2LogicalModel xmlns="http://datex2.eu/schema/2/2_0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        <payloadPublication xsi:type="SituationPublication"><publicationTime>$time</publicationTime>
        <situation id="s">$record</situation></payloadPublication></d2LogicalModel>
    """.trimIndent().toByteArray()
    private fun record(location: String = "<pointCoordinates><latitude>47.01</latitude><longitude>19</longitude></pointCoordinates>", extra: String = "") = """
        <situationRecord id="one" xsi:type="Accident"><validity><validityStatus>active</validityStatus>$extra</validity>
        <groupOfLocations xsi:type="Point">$location<roadNumber>M3</roadNumber><directionBoundOnRoad>northBound</directionBoundOnRoad></groupOfLocations></situationRecord>
    """
    @Test fun acceptsNationalSnapshotLargerThanOldLimit() {
        val padding = " ".repeat(12 * 1024 * 1024)
        val feed = DatexParser.parse(xml().toString(Charsets.UTF_8).replace("</d2LogicalModel>", "$padding</d2LogicalModel>").toByteArray())
        assertEquals(1, feed.events.size)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedSnapshot() {
        DatexParser.parse(ByteArray(DatexParser.MAX_BYTES + 1))
    }
    @Test fun parsesNamespacedPoint() {
        val f = DatexParser.parse(xml()); assertEquals(now, f.published); assertEquals(1, f.events.size)
        assertEquals(0.0, f.events.single().direction!!,0.0); assertEquals("M3",f.events.single().road)
    }
    @Test fun unsupportedLocationCounted() {
        val f = DatexParser.parse(xml(record("<alertCLocation><specificLocation>123</specificLocation></alertCLocation>")))
        assertTrue(f.events.isEmpty()); assertEquals(1,f.skipped)
    }
    @Test fun invalidDatesAndCoordinatesSkipped() {
        assertEquals(1, DatexParser.parse(xml(record(extra = "<overallStartTime>garbage</overallStartTime>"))).skipped)
        assertEquals(1, DatexParser.parse(xml(record("<pointCoordinates><latitude>NaN</latitude><longitude>19</longitude></pointCoordinates>"))).skipped)
    }
    @Test fun expiredRecordsFiltered() {
        val f = DatexParser.parse(xml(record(extra = "<overallEndTime>2026-10-18T12:00:00Z</overallEndTime>")))
        assertTrue(matches(f.events).isEmpty())
    }
    @Test fun duplicateIdsOnlyOnce() { assertEquals(1, DatexParser.parse(xml(record()+record())).events.size) }
    @Test fun multiPointNotSilentlyReduced() {
        val point = "<pointCoordinates><latitude>47</latitude><longitude>19</longitude></pointCoordinates>"
        assertEquals(1, DatexParser.parse(xml(record(point+point))).skipped)
    }
    @Test fun rejectsHtmlMissingTimeAndDtd() {
        listOf("<html/>", "<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><a>&x;</a>").forEach {
            assertTrue(runCatching { DatexParser.parse(it.toByteArray()) }.isFailure)
        }
        assertTrue(runCatching { DatexParser.parse(xml(time = "")) }.isFailure)
        assertTrue(runCatching { DatexParser.parse(xml().toString(Charsets.UTF_8).toByteArray(Charsets.UTF_16)) }.isFailure)
    }
    @Test fun exercisePublicationRejected() {
        val input = xml().toString(Charsets.UTF_8).replace("<publicationTime>", "<headerInformation><informationStatus>technicalExercise</informationStatus></headerInformation><publicationTime>")
        assertTrue(runCatching { DatexParser.parse(input.toByteArray()) }.isFailure)
    }
    @Test fun sizeLimit() { assertTrue(runCatching { DatexParser.parse(ByteArray(DatexParser.MAX_BYTES+1)) }.isFailure) }
}
