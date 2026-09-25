package hu.elmdash.connection

import hu.elmdash.trip.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30])
class PetrolPriceRepositoryTest {
    private val html="""<img src="images/ua_pin/95-benzin-e10.png">Átlag - Ft/l<br><span class="ar">635.5</span><img src="images/ua_pin/gazolaj.png">"""
    @Test fun `offline fallback survives repository recreation and expires after seven days`() = runTest {
        val c=RuntimeEnvironment.getApplication()
        c.getSharedPreferences("elm-petrol-price",0).edit().clear().commit()
        assertNull(PetrolPriceRepository(c) { error("offline") }.atJourneyStart(1000))
        assertEquals(PetrolPrice(635.5,1000),PetrolPriceRepository(c) { html }.atJourneyStart(1000))
        assertEquals(PetrolPrice(635.5,1000,true),PetrolPriceRepository(c) { error("offline") }.atJourneyStart(2000))
        assertNull(PetrolPriceRepository(c) { "malformed" }.atJourneyStart(1001+PetrolPriceRepository.MAX_CACHE_MS))
        assertNull(PetrolPriceRepository(c) { error("clock moved backwards") }.atJourneyStart(999))
    }
    @Test fun `journal price roundtrips and older records still load`() {
        val c=RuntimeEnvironment.getApplication();val store=JourneyStore(c)
        val record=JourneyRecord("a",1000,2000,2000,JourneyEnd.ENGINE_OFF,
            summary=TripSummary(fuelLiters=2.0),sources=setOf(FuelSource.MAP_ESTIMATE),petrolPrice=PetrolPrice(635.5,1000,true))
        val state=JourneyLogState(journeys=listOf(record,record.copy(id="old",petrolPrice=null)))
        store.save(state);assertEquals(state,store.load())
        assertTrue(JourneyNotifications.summary(store.load().journeys.first()).contains("1271 Ft"))
        assertTrue(JourneyNotifications.summary(record).contains("mentett ár"))
        assertNull(store.load().journeys.last().estimatedCostHuf)
    }
}
