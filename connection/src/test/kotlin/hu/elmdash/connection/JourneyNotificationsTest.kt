package hu.elmdash.connection

import android.app.Notification
import android.app.NotificationManager
import hu.elmdash.trip.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class JourneyNotificationsTest {
    private fun record(id: String = "trip") = JourneyRecord(id, 0, 1000, 1000, JourneyEnd.ENGINE_OFF,
        TripSummary(started = true, distanceKm = 12.5, fuelLiters = 0.875,
            pairedDistanceKm = 12.5, pairedFuelLiters = 0.875, containsEstimate = true),
        sources = setOf(FuelSource.MAP_ESTIMATE))

    @Test fun `summary contains localized distance liters and average with honest missing data`() {
        val text = JourneyNotifications.summary(record())
        assertTrue(text.contains("12,50 km")); assertTrue(text.contains("0,88 l benzin"))
        assertTrue(text.contains("7,0 l/100 km")); assertTrue(text.contains("Becsült"))
        val missing = JourneyNotifications.summary(record().copy(summary = TripSummary(hasGaps = true), sources = emptySet()))
        assertTrue(missing.contains("— l benzin")); assertTrue(missing.contains("— l/100 km"))
        assertTrue(missing.contains("Részleges"))
    }
    @Test fun `notification survives sender recreation but dismissed summary is not posted twice`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(NotificationManager::class.java)
        context.getSharedPreferences("elm-journey-notifications", 0).edit().clear().commit()
        manager.cancelAll()
        JourneyNotifications(context).show(record())
        val n = manager.activeNotifications.single()
        assertEquals("Utazás véget ért", n.notification.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(n.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("7,0 l/100 km"))
        manager.cancel("trip", 330)
        JourneyNotifications(context).show(record())
        assertTrue(manager.activeNotifications.isEmpty())
        JourneyNotifications(context).show(record("reset").copy(end = JourneyEnd.RESET))
        assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun `controller finalizes failed connection only after grace period and posts summary`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        listOf("elm-journal", "elm-dashboard", "elm-journey-notifications").forEach {
            context.getSharedPreferences(it, 0).edit().clear().commit()
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        var broken = false
        val controller = DashboardController(context, { currentTime }, backgroundScope, wallClock = { 100_000 + currentTime }) { _, _, _ ->
            val demo = DemoTransport { currentTime }
            object : hu.elmdash.elm.ElmTransport {
                override suspend fun connect() { if (broken) error("Disconnected"); demo.connect() }
                override suspend fun exchange(command: String, timeoutMs: Long): String {
                    if (broken) error("Disconnected")
                    return demo.exchange(command, timeoutMs)
                }
                override fun close() = demo.close()
            }
        }
        controller.startLive("00:11:22:33:44:55", false)
        advanceTimeBy(30_000); runCurrent()
        val id = controller.state.value.journal.active!!.id
        broken = true
        advanceTimeBy(120_000); runCurrent()
        assertEquals(Phase.ERROR, controller.state.value.phase)
        assertEquals(id, controller.state.value.journal.active!!.id)
        assertTrue(manager.activeNotifications.isEmpty())
        advanceTimeBy(65_000); runCurrent()
        assertNull(controller.state.value.journal.active)
        assertEquals(JourneyEnd.CONNECTION_LOST, controller.state.value.journal.journeys.single().end)
        assertEquals(id, manager.activeNotifications.single().tag)
        controller.stop()
        assertEquals(1, manager.activeNotifications.size)
    }
}
