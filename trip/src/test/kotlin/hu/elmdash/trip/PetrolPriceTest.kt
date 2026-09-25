package hu.elmdash.trip

import hu.elmdash.obd.*
import org.junit.Assert.*
import org.junit.Test

class PetrolPriceTest {
    private val block = """<img src="images/ua_pin/95-benzin-e10.png"><div>Minimum - Ft/l<br><span class="ar">597.0</span></div><div>Átlag - Ft/l<br><span class="ar">635.5</span></div><div>Maximum - Ft/l<br><span class="ar">695.0</span></div><img src="images/ua_pin/gazolaj.png"><div>Átlag - Ft/l<br><span class="ar">709.1</span></div>"""
    @Test fun `captured live source section selects published average`() {
        val page=javaClass.getResource("/petrol-95-2026-09-18.html")!!.readText()
        assertEquals(635.5,PetrolPriceParser.parse(page)!!,0.0)
    }
    @Test fun `parser selects only 95 average including decimal comma`() {
        assertEquals(635.5, PetrolPriceParser.parse(block)!!, 0.0)
        assertEquals(635.5, PetrolPriceParser.parse(block.replace("635.5", "635,5").replace("Átlag", "&Aacute;tlag"))!!, 0.0)
    }
    @Test fun `changed or ambiguous markup fails closed instead of using diesel minimum or an implausible price`() {
        assertNull(PetrolPriceParser.parse(block.replace("95-benzin-e10", "100-benzin-e5")))
        assertNull(PetrolPriceParser.parse(block.replace("635.5", "5")))
        assertNull(PetrolPriceParser.parse(block.replace("Átlag", "Medián")))
        assertNull(PetrolPriceParser.parse(block.replace("<div>Maximum", "<div>Átlag - Ft/l<br><span class=\"ar\">620</span></div><div>Maximum")))
        assertNull(PetrolPriceParser.parse("<html>offline</html>"))
    }
    @Test fun `price is bound to active journey once and archived cost stays immutable`() {
        val log = JourneyLog()
        fun tick(t: Long) = log.update(Telemetry(mapOf(Pid.RPM to Reading(1800.0,t,Quality.OK), Pid.SPEED to Reading(60.0,t,Quality.OK))),
            FuelReading(6.0,10.0,FuelSource.MAP_ESTIMATE),t,100_000+t,"Kalos")
        tick(0)
        val id = log.state.active!!.id
        val price = PetrolPrice(635.5,100_000)
        log.attachPetrolPrice("wrong",price); assertNull(log.state.active!!.petrolPrice)
        log.attachPetrolPrice(id,price)
        log.attachPetrolPrice(id,PetrolPrice(700.0,101_000))
        for(t in 1000L..60_000L step 1000) tick(t)
        log.finish(JourneyEnd.STOPPED,160_000)
        assertEquals(63.55,log.state.journeys.single().estimatedCostHuf!!,1e-7)
        tick(61_000)
        log.attachPetrolPrice(id,PetrolPrice(900.0,161_000))
        assertNull(log.state.active!!.petrolPrice)
        assertEquals(price,log.state.journeys.single().petrolPrice)
        assertTrue(JourneyCsv.export(log.state).contains("benzinár_Ft_l"))
        assertTrue(JourneyCsv.export(log.state).contains(PetrolPrice.SOURCE_URL))
    }
    @Test fun `backfill fixes only missing prices including records without fuel and never revalues them`() {
        val priced=JourneyRecord("known",0,1,1,JourneyEnd.STOPPED,petrolPrice=PetrolPrice(600.0,0))
        val missing=JourneyRecord("missing",0,1,1,JourneyEnd.STOPPED,summary=TripSummary(fuelLiters=2.0),sources=setOf(FuelSource.MAP_ESTIMATE))
        val noFuel=missing.copy(id="noFuel",sources=emptySet())
        val log=JourneyLog(JourneyLogState(journeys=listOf(priced,missing,noFuel)))
        log.backfillMissingPrices(PetrolPrice(630.0,100,true))
        assertNull(log.state.journeys[1].petrolPrice)
        log.backfillMissingPrices(PetrolPrice(635.5,200))
        assertEquals(priced,log.state.journeys[0])
        assertEquals(1271.0,log.state.journeys[1].estimatedCostHuf!!,0.0)
        assertTrue(log.state.journeys[1].petrolPrice!!.appliedAfterStart)
        assertNotNull(log.state.journeys[2].petrolPrice)
        assertNull(log.state.journeys[2].estimatedCostHuf)
        val fixed=log.state
        log.backfillMissingPrices(PetrolPrice(800.0,300))
        assertEquals(fixed,log.state)
    }
    @Test fun `missing price or fuel never produces a fabricated zero cost`() {
        val r=JourneyRecord("a",0,0,summary=TripSummary(fuelLiters=2.0))
        assertNull(r.estimatedCostHuf)
        assertNull(r.copy(petrolPrice=PetrolPrice(635.5,0)).estimatedCostHuf)
        assertEquals(1271.0,r.copy(petrolPrice=PetrolPrice(635.5,0),sources=setOf(FuelSource.ECU)).estimatedCostHuf!!,0.0)
    }
}
