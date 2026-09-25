package hu.elmdash.connection

import android.content.Intent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AutoObdTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun setup() {
        // Each Robolectric test has a new Application; discard the process singleton from the previous one.
        DashboardGraph::class.java.getDeclaredField("controller").apply { isAccessible = true }.set(null, null)
        context.getSharedPreferences("elm-dashboard", 0).edit().clear().commit()
        AutoObd.install(context); AutoObd.automaticRunning = false; AutoObd.projectionChanged(false); AutoObd.released()
        SessionStore(context).apply { autoConnect = true; address = "00:11:22:33:44:55" }
        while (shadowOf(context).nextStartedService != null) { }
    }
    @Test fun `projection starts foreground OBD once and respects manual pause`() {
        AutoObd.projectionChanged(true)
        val intent = shadowOf(context).nextStartedService
        assertNotNull(DashboardGraph.get(context).state.value.toString(), intent)
        assertEquals(ObdService.ACTION_AUTO, intent.action)
        AutoObd.projectionChanged(true); AutoObd.request(context)
        assertNull(shadowOf(context).nextStartedService)
        AutoObd.pause(context); AutoObd.request(context)
        assertNull(shadowOf(context).nextStartedService)
        AutoObd.projectionChanged(false); AutoObd.projectionChanged(true)
        assertEquals(ObdService.ACTION_AUTO, shadowOf(context).nextStartedService.action)
    }
    @Test fun `disabled or missing configuration never starts a fabricated demo`() {
        val store = SessionStore(context)
        store.autoConnect = false; AutoObd.projectionChanged(true)
        assertNull(shadowOf(context).nextStartedService)
        store.autoConnect = true; store.address = ""; AutoObd.request(context)
        assertNull(shadowOf(context).nextStartedService)
        assertEquals(Phase.ERROR, DashboardGraph.get(context).state.value.phase)
        assertFalse(DashboardGraph.get(context).state.value.simulated)
        assertTrue(DashboardGraph.get(context).state.value.message.startsWith("Nincs kapcsolat az autóval"))
    }
    @Test fun `only selected head unit wakes OBD before projection is ready`() {
        val store = SessionStore(context); store.headUnitAddress = "AA:BB:CC:DD:EE:FF"
        val receiver = CarBluetoothReceiver()
        fun event(address: String) = Intent(BluetoothDevice.ACTION_ACL_CONNECTED)
            .putExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address))
        receiver.onReceive(context, event("00:00:00:00:00:01"))
        assertNull(shadowOf(context).nextStartedService)
        receiver.onReceive(context, event(store.headUnitAddress))
        assertEquals(ObdService.ACTION_AUTO, shadowOf(context).nextStartedService.action)
    }
    @Test fun `default enablement does not overwrite a user opt out`() {
        val store = SessionStore(context); store.autoConnect = false
        store.enableAutoByDefaultOnce()
        assertFalse(store.autoConnect)
    }
    @Test fun `policy blocks duplicate starts demo manual session and disabled automation`() {
        assertTrue(AutoObdPolicy.canStart(true, false, true, false, false))
        assertFalse(AutoObdPolicy.canStart(false, false, true, false, false))
        assertFalse(AutoObdPolicy.canStart(true, true, true, false, false))
        assertFalse(AutoObdPolicy.canStart(true, false, false, false, false))
        assertFalse(AutoObdPolicy.canStart(true, false, true, true, false))
        assertFalse(AutoObdPolicy.canStart(true, false, true, false, true))
    }
    @Test fun `short projection drop is tolerated and sustained disconnect stops only automatic collection`() {
        AutoObd.projectionChanged(true)
        AutoObd.automaticRunning = true
        AutoObd.projectionChanged(false)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3))
        assertNull(shadowOf(context).nextStoppedService)
        AutoObd.projectionChanged(true)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3))
        assertNull(shadowOf(context).nextStoppedService)
        AutoObd.projectionChanged(false)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(179))
        assertNull(shadowOf(context).nextStoppedService)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3))
        assertEquals(ObdService::class.java.name, shadowOf(context).nextStoppedService.component!!.className)
    }
    @Test fun `projection disconnect leaves a manually started measurement alone`() {
        AutoObd.projectionChanged(true)
        AutoObd.automaticRunning = false
        AutoObd.projectionChanged(false)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(183))
        assertNull(shadowOf(context).nextStoppedService)
    }

    @Test fun `manual stop away from the car does not suppress the next AA connection`() {
        AutoObd.pause(context)
        assertFalse(SessionStore(context).autoPaused)
        AutoObd.projectionChanged(true)
        assertEquals(ObdService.ACTION_AUTO, shadowOf(context).nextStartedService.action)
    }

}
