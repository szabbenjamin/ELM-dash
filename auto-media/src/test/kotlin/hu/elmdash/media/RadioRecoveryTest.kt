package hu.elmdash.media

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadioRecoveryTest {
    @Test fun `offline start waits and validated network resumes without a tap`() = runTest {
        var opens = 0
        val r = RadioRecovery(backgroundScope, false, { opens++ }, {})
        r.start(); advanceTimeBy(120_000)
        assertEquals(0, opens); assertTrue(r.wanted); assertTrue(r.waiting)
        r.networkChanged(true); runCurrent()
        assertEquals(1, opens)
        r.playerState(true, false, false)
        assertNull(r.message); assertFalse(r.waiting)
    }
    @Test fun `retry backs off and is bounded at thirty seconds`() = runTest {
        var opens = 0
        val r = RadioRecovery(backgroundScope, true, { opens++ }, {})
        r.start()
        for (delay in listOf(2_000L, 5_000L, 10_000L, 20_000L, 30_000L, 30_000L)) {
            val before = opens
            r.failure(true); advanceTimeBy(delay - 1); runCurrent(); assertEquals(before, opens)
            advanceTimeBy(1); runCurrent(); assertEquals(before + 1, opens)
        }
    }
    @Test fun `network restoration accelerates retry but never restarts after manual pause`() = runTest {
        var opens = 0
        val r = RadioRecovery(backgroundScope, true, { opens++ }, {})
        r.start(); r.failure(true); r.networkChanged(false)
        advanceTimeBy(90_000); runCurrent(); assertEquals(1, opens)
        r.networkChanged(true); runCurrent(); assertEquals(2, opens)
        r.failure(true); r.pause(); r.networkChanged(false); r.networkChanged(true)
        advanceTimeBy(90_000); runCurrent(); assertEquals(2, opens); assertFalse(r.wanted)
    }
    @Test fun `station change cancels the old retry and manual start resets backoff`() = runTest {
        val opened = mutableListOf<String>(); var station = "oxygen"
        val r = RadioRecovery(backgroundScope, true, { opened += station }, {})
        r.start(); r.failure(true); station = "retro"; r.start()
        advanceTimeBy(2_100); runCurrent()
        assertEquals(listOf("oxygen", "retro"), opened)
    }
    @Test fun `buffering watchdog recovers a hung server without waiting forever`() = runTest {
        var opens = 0
        val r = RadioRecovery(backgroundScope, true, { opens++ }, {})
        r.start(); r.playerState(false, true, false)
        advanceTimeBy(32_000); runCurrent(); assertEquals(2, opens)
    }
    @Test fun `audio focus suppression prevents reconnect until focus returns`() = runTest {
        var opens = 0
        val r = RadioRecovery(backgroundScope, true, { opens++ }, {})
        r.start(); r.failure(true); r.playerState(false, false, true)
        r.networkChanged(false); r.networkChanged(true)
        advanceTimeBy(60_000); runCurrent(); assertEquals(1, opens)
        r.playerState(false, false, false); advanceTimeBy(5_000); runCurrent()
        assertEquals(2, opens)
    }
    @Test fun `healthy buffered playback survives route changes without unnecessary restart`() = runTest {
        var opens = 0
        val r = RadioRecovery(backgroundScope, true, { opens++ }, {})
        r.start(); r.playerState(true, false, false)
        r.networkChanged(false); r.networkChanged(true); advanceTimeBy(60_000); runCurrent()
        assertEquals(1, opens)
    }
    @Test fun `terminal stream errors do not loop and closing cancels every pending task`() = runTest {
        var opens = 0
        val r = RadioRecovery(backgroundScope, true, { opens++ }, {})
        r.start(); r.failure(false); r.networkChanged(false); r.networkChanged(true)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, opens); assertFalse(r.wanted); assertNotNull(r.error)
        r.start(); r.failure(true); r.close(); advanceTimeBy(60_000); runCurrent()
        assertEquals(2, opens)
    }
    @Test fun `unexpected live stream end reconnects`() = runTest {
        var opens = 0
        val r = RadioRecovery(backgroundScope, true, { opens++ }, {})
        r.start(); r.playerState(false, false, false, ended = true)
        advanceTimeBy(2_000); runCurrent(); assertEquals(2, opens)
    }
}
