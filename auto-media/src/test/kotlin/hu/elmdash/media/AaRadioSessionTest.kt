package hu.elmdash.media

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AaRadioSessionTest {
    @Test fun `AA starts once and stops after a sustained disconnect`() = runTest {
        var starts = 0; var stops = 0; var paused = false
        val s = AaRadioSession(backgroundScope, { true }, { paused }, { paused = it }, { starts++; true }, { stops++ })
        s.connectionChanged(false); assertEquals(0, starts)
        s.connectionChanged(true); s.connectionChanged(true); assertEquals(1, starts)
        s.connectionChanged(false); advanceTimeBy(4_999); runCurrent(); assertEquals(0, stops)
        advanceTimeBy(1); runCurrent(); assertEquals(1, stops)
        s.connectionChanged(true); assertEquals(2, starts)
    }
    @Test fun `manual pause survives network changes and a short AA flap but not a new car session`() = runTest {
        var starts = 0; var paused = false
        val s = AaRadioSession(backgroundScope, { true }, { paused }, { paused = it }, { starts++; true }, {})
        s.connectionChanged(true); s.manualPause(); assertTrue(paused)
        s.connectionChanged(false); advanceTimeBy(2_000); s.connectionChanged(true)
        advanceTimeBy(10_000); runCurrent(); assertTrue(paused); assertEquals(1, starts)
        s.connectionChanged(false); advanceTimeBy(5_000); runCurrent(); assertFalse(paused)
        s.connectionChanged(true); assertEquals(2, starts)
    }
    @Test fun `disabled autoplay and restored pause never start audio on observing AA`() = runTest {
        var starts = 0; var enabled = false; var paused = false
        val s = AaRadioSession(backgroundScope, { enabled }, { paused }, { paused = it }, { starts++; true }, {})
        s.connectionChanged(true); assertEquals(0, starts)
        s.connectionChanged(false); advanceTimeBy(5_000); runCurrent()
        enabled = true; paused = true; s.connectionChanged(true); assertEquals(0, starts)
        s.explicitPlay(); assertFalse(paused)
    }
    @Test fun `manual pause outside AA does not disable the next automatic start`() = runTest {
        var starts = 0; var paused = false
        val s = AaRadioSession(backgroundScope, { true }, { paused }, { paused = it }, { starts++; true }, {})
        s.connectionChanged(false); s.manualPause(); assertFalse(paused)
        s.connectionChanged(true); assertEquals(1, starts)
    }
}
