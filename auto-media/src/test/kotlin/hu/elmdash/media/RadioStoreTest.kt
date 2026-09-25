package hu.elmdash.media

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RadioStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun clear() { context.getSharedPreferences("elm-radio", 0).edit().clear().commit() }

    @Test fun `catalog includes requested exact Oxygen stream and popular Hungarian stations`() {
        val s = RadioStore(context).state.value
        assertEquals("https://oxygenmusic.hu:8443/oxygenmusic", s.selected.url)
        assertEquals(14, s.stations.size)
        assertEquals(s.stations.size, s.stations.map { it.id }.distinct().size)
        assertTrue(s.stations.any { it.name == "Retro Rádió" })
        s.stations.forEach { assertEquals(it.url, RadioStore.validateUrl(it.url)) }
    }

    @Test fun `custom station selection and favorites survive restart and removal cleans references`() {
        val store = RadioStore(context)
        store.add(" Saját rádió ", " https://example.org/live.aac ")
        val station = store.state.value.stations.last()
        assertEquals("Saját rádió", station.name)
        store.select(station.id); store.toggleFavorite(station.id)
        val reloaded = RadioStore(context)
        assertEquals(station, reloaded.state.value.selected)
        assertTrue(station.id in reloaded.state.value.favorites)
        reloaded.remove(station.id)
        val after = RadioStore(context).state.value
        assertFalse(after.stations.any { it.id == station.id })
        assertFalse(station.id in after.favorites)
        assertEquals("oxygen", after.selectedId)
    }

    @Test fun `active custom station and builtin catalog cannot be removed`() {
        val store = RadioStore(context)
        store.add("Test", "https://example.org/radio")
        val id = store.state.value.stations.last().id
        store.select(id); store.status(false, true)
        assertThrows(IllegalArgumentException::class.java) { store.remove(id) }
        store.status(false, false); store.remove(id); store.remove("oxygen")
        assertEquals(14, store.state.value.stations.size)
    }

    @Test fun `reject malformed credentials local files duplicate streams and oversized custom list`() {
        listOf("file:///sdcard/music.mp3", "javascript:alert(1)", "https://u:p@example.org/live", "https://example.org/live#part", "https://", "just text").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { RadioStore.validateUrl(it) }
        }
        val store = RadioStore(context)
        assertThrows(IllegalArgumentException::class.java) { store.add("Duplicate", store.state.value.selected.url) }
        repeat(20) { store.add("Station $it", "https://example.org/$it") }
        assertThrows(IllegalArgumentException::class.java) { store.add("Too many", "https://example.org/21") }
    }

    @Test fun `search ignores accents and case for Hungarian voice queries`() {
        assertTrue(RadioStore.matches("Petőfi Rádió", " petofi "))
        assertTrue(RadioStore.matches("Sláger FM", "SLAGER"))
        assertFalse(RadioStore.matches("Retro Rádió", "Kossuth"))
    }
}
