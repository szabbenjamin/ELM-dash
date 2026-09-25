package hu.elmdash.trip

import hu.elmdash.obd.*
import org.junit.Assert.*
import org.junit.Test

class DrivingAdvisorTest {
    private fun sample(now: Long, rpm: Double = 2_400.0, gear: Int = 4, load: Double = 35.0,
        tps: Double = 20.0, coolant: Double = 90.0, speed: Double = rpm / Kalos12Profile.rpmPerKmh(gear)) =
        Telemetry(mapOf(Pid.RPM to rpm, Pid.SPEED to speed, Pid.LOAD to load, Pid.TPS to tps,
            Pid.COOLANT to coolant).mapValues { Reading(it.value, now, Quality.OK) })

    private fun settle(advisor: DrivingAdvisor = DrivingAdvisor(), start: Long = 0,
        rpm: Double = 2_400.0, gear: Int = 4, load: Double = 35.0, tps: Double = 20.0,
        coolant: Double = 90.0): DrivingAdvice {
        var result = DrivingAdvice()
        for (time in start..start + 6_000 step 250) result = advisor.update(sample(time, rpm, gear, load, tps, coolant), time)
        return result
    }

    @Test fun `warm steady driving identifies economical band and a stable gear`() {
        val a = settle()
        assertEquals(DriveCue.EFFICIENT, a.cue)
        assertEquals(4, a.estimatedGear)
        assertEquals(CoolantBand.NORMAL, a.coolant)
    }
    @Test fun `upshift requires light load and a usable next gear`() {
        assertEquals(DriveCue.UPSHIFT, settle(rpm = 3_200.0).cue)
        assertNotEquals(DriveCue.UPSHIFT, settle(rpm = 3_200.0, load = 85.0).cue)
        assertNotEquals(DriveCue.UPSHIFT, settle(rpm = 3_200.0, gear = 5).cue)
        assertNotEquals(DriveCue.UPSHIFT, settle(rpm = 3_000.0, gear = 1).cue)
        assertEquals(DriveCue.UPSHIFT, settle(rpm = 3_500.0, gear = 1).cue)
    }
    @Test fun `loaded low rpm suggests downshift but coasting and first gear do not`() {
        assertEquals(DriveCue.DOWNSHIFT, settle(rpm = 1_650.0, load = 85.0).cue)
        assertNotEquals(DriveCue.DOWNSHIFT, settle(rpm = 1_650.0, load = 30.0).cue)
        assertNotEquals(DriveCue.DOWNSHIFT, settle(rpm = 1_650.0, load = 85.0, tps = 3.0).cue)
        assertNotEquals(DriveCue.DOWNSHIFT, settle(rpm = 1_650.0, load = 85.0, gear = 1).cue)
    }
    @Test fun `cold hot and stationary states suppress shift advice`() {
        assertEquals(DriveCue.COLD, settle(rpm = 3_200.0, coolant = 50.0).cue)
        assertEquals(DriveCue.HOT, settle(rpm = 3_200.0, coolant = 111.0).cue)
        assertEquals(DriveCue.IDLE, DrivingAdvisor().update(sample(0, rpm = 850.0, speed = 0.0), 0).cue)
    }
    @Test fun `missing input immediately withdraws an existing arrow`() {
        for (missing in listOf(Pid.RPM, Pid.SPEED, Pid.LOAD, Pid.TPS, Pid.COOLANT)) {
            val advisor = DrivingAdvisor()
            assertEquals(DriveCue.UPSHIFT, settle(advisor, rpm = 3_200.0).cue)
            val t = sample(6_250, rpm = 3_200.0)
            assertEquals("Missing $missing", DriveCue.NO_DATA,
                advisor.update(t.copy(readings = t.readings - missing), 6_250).cue)
        }
    }
    @Test fun `old samples and an unrecognised clutch ratio never produce a shift arrow`() {
        val advisor = DrivingAdvisor()
        assertEquals(DriveCue.UPSHIFT, settle(advisor, rpm = 3_200.0).cue)
        assertEquals(DriveCue.NO_DATA, advisor.update(sample(0, rpm = 3_200.0), 6_250).cue)
        val odd = advisor.update(sample(6_500, rpm = 3_200.0, speed = 17.0), 6_500)
        assertNull(odd.estimatedGear)
        assertNotEquals(DriveCue.UPSHIFT, odd.cue)
    }
    @Test fun `ratio changes during a shift immediately clear the previous arrow`() {
        val advisor = DrivingAdvisor()
        assertEquals(DriveCue.UPSHIFT, settle(advisor, rpm = 3_200.0).cue)
        val changing = advisor.update(sample(6_250, rpm = 2_500.0, gear = 5), 6_250)
        assertNull(changing.estimatedGear)
        assertNotEquals(DriveCue.UPSHIFT, changing.cue)
    }
    @Test fun `short spikes do not request shifts and small threshold noise keeps the cue stable`() {
        val advisor = DrivingAdvisor()
        settle(advisor)
        assertNotEquals(DriveCue.UPSHIFT, advisor.update(sample(6_250, rpm = 3_050.0), 6_250).cue)
        assertEquals(DriveCue.UPSHIFT, settle(advisor, start = 6_500, rpm = 3_050.0).cue)
        for (t in 12_750L..14_000L step 250) {
            assertEquals(DriveCue.UPSHIFT, advisor.update(sample(t, rpm = if (t % 500 == 0L) 2_980.0 else 3_020.0), t).cue)
        }
    }
    @Test fun `coolant warning has hysteresis and stale temperature removes its color`() {
        val advisor = DrivingAdvisor()
        assertEquals(CoolantBand.WARM, advisor.update(sample(0, coolant = 104.0), 0).coolant)
        assertEquals(CoolantBand.WARM, advisor.update(sample(250, coolant = 102.0), 250).coolant)
        assertEquals(CoolantBand.NORMAL, advisor.update(sample(500, coolant = 99.0), 500).coolant)
        assertEquals(CoolantBand.HOT, advisor.update(sample(750, coolant = 111.0), 750).coolant)
        assertEquals(CoolantBand.HOT, advisor.update(sample(1_000, coolant = 108.0), 1_000).coolant)
        assertEquals(CoolantBand.NO_DATA, advisor.update(Telemetry(), 1_250).coolant)
    }
}
