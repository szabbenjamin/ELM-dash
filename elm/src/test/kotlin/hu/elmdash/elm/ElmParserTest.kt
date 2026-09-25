package hu.elmdash.elm

import org.junit.Assert.*
import org.junit.Test

class ElmParserTest {
    private fun frames(raw: String, pid: Int) = (ElmParser.mode01(raw, pid) as ElmReply.Data).frames
    @Test fun `echo searching and spaced headerless response`() {
        val f = frames("010C\rSEARCHING...\r41 0C 1A F8\r>", 0x0C).single()
        assertEquals(listOf(0x1A, 0xF8), f.bytes)
    }
    @Test fun `compact headerless response`() {
        assertEquals(listOf(0x28), frames("410D28\r>", 0x0D).single().bytes)
    }
    @Test fun `CAN frames preserve responding ECU and trim padding`() {
        val f = frames("7E8 04 41 0C 1A F8 00 00 00\r7E9 04 41 0C 00 00\r>", 0x0C)
        assertEquals(listOf("7E8", "7E9"), f.map { it.ecu })
        assertEquals(listOf(0x1A, 0xF8), f.first().bytes)
    }
    @Test fun `29 bit CAN and legacy K line`() {
        assertEquals("18DAF110", frames("18DAF110 03 41 05 7B", 5).single().ecu)
        assertEquals("18DAF110", frames("18 DA F1 10 03 41 05 7B", 5).single().ecu)
        val legacy = frames("48 6B 10 41 0C 1A F8 22\r>", 12).single()
        assertEquals("10", legacy.ecu)
        assertEquals(listOf(0x1A, 0xF8), legacy.bytes)
    }
    @Test fun `wrong PID truncated CAN and multi frame rejected`() {
        assertTrue(ElmParser.mode01("41 0D 20", 12) is ElmReply.Error)
        assertTrue(ElmParser.mode01("7E8 04 41 0C 1A", 12) is ElmReply.Error)
        assertTrue(ElmParser.mode01("7E8 10 06 41 0C 1A F8", 12) is ElmReply.Error)
    }
    @Test fun `no data errors and voltage are explicit`() {
        assertEquals(ElmReply.NoData, ElmParser.mode01("NO DATA\r>", 12))
        assertTrue(ElmParser.mode01("CAN ERROR\r>", 12) is ElmReply.Error)
        assertTrue(ElmParser.mode01("7F 01 12\r>", 12) is ElmReply.Error)
        assertEquals(14.2, ElmParser.voltage("ATRV\r14.2V\r>")!!, 0.0001)
        assertNull(ElmParser.voltage("NO DATA"))
    }
}
