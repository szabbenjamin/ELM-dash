package hu.elmdash.connection

import android.location.Location
import android.location.LocationListener
import hu.elmdash.obd.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30])
class ParkingTest {
    @Test fun motorStopFreezesFreshFixAndRestartCancelsParking() {
        val context = RuntimeEnvironment.getApplication()
        val store = ParkingStore(context); store.clear()
        context.getSharedPreferences("elm-parking", 0).edit().putBoolean("enabled", false).commit() // legacy opt-out no longer gates automatic parking
        var now = 10000L
        val tracker = ParkingTracker(context, { now }, { 1000000L+now })
        fun state(rpm: Double) = DashboardState(phase=Phase.LIVE, telemetry=Telemetry(mapOf(Pid.RPM to Reading(rpm,now,Quality.OK))))
        tracker.tick(state(900.0),true)
        val listener = ParkingTracker::class.java.getDeclaredField("listener").apply { isAccessible=true }.get(tracker) as LocationListener
        listener.onLocationChanged(Location("gps").apply { latitude=47.0; longitude=19.0; accuracy=5f; elapsedRealtimeNanos=now*1000000 })
        tracker.tick(state(0.0),true)
        val frozen = store.load()!!
        assertFalse(frozen.confirmed)
        now += 1000
        listener.onLocationChanged(Location("gps").apply { latitude=47.1; longitude=19.1; accuracy=5f; elapsedRealtimeNanos=now*1000000 })
        tracker.tick(state(0.0),true)
        assertEquals(frozen,store.load())
        tracker.tick(state(900.0),true)
        assertNull(store.load())
        tracker.tick(state(0.0),true)
        now += 120000
        tracker.tick(state(0.0),true)
        assertTrue(store.load()!!.confirmed)
        tracker.tick(state(900.0),false)
        assertNotNull(store.load()) // permission loss must not overwrite the saved parking location
        tracker.stop()
    }
    @Test fun staleFixAndDemoNeverCreateParking() {
        val context = RuntimeEnvironment.getApplication()
        val store = ParkingStore(context); store.clear()
        val tracker = ParkingTracker(context, { 50000L })
        tracker.tick(DashboardState(simulated=true),true)
        assertNull(store.load())
        tracker.tick(DashboardState(telemetry=Telemetry(mapOf(Pid.RPM to Reading(0.0,50000,Quality.OK)))),true)
        assertNull(store.load()) // zero RPM without observed running engine isn't a stop
        tracker.stop()
    }
}
