package hu.elmdash.auto

import androidx.car.app.model.PaneTemplate
import androidx.car.app.testing.TestCarContext
import androidx.car.app.HandshakeInfo
import hu.elmdash.connection.*
import hu.elmdash.obd.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarTemplateTest {
    @Test fun `all pages build valid four row templates`() {
        val context = TestCarContext.createCarContext(RuntimeEnvironment.getApplication())
        for (page in 1..3) {
            val template = DashboardCarScreen(context, page).onGetTemplate() as PaneTemplate
            assertEquals(4, template.pane.rows.size)
            assertEquals(if (page == 1) 1 else 0, template.pane.actions.size)
            assertEquals("Demó", template.actionStrip!!.actions.single().title.toString())
        }
    }
    @Test fun `primary Auto screen has a large graphic and RPM coolant rows first`() {
        val context = TestCarContext.createCarContext(RuntimeEnvironment.getApplication())
        context.updateHandshakeInfo(HandshakeInfo("test.host", 8))
        val template = DashboardCarScreen(context, 0).onGetTemplate() as PaneTemplate
        assertEquals(listOf("Fordulatszám / terhelés", "Vízhőfok", "Fogyasztás / útátlag", "Sebesség / kapcsolat"),
            template.pane.rows.map { it.title.toString() })
        assertNotNull(template.pane.image)
        assertEquals(2, template.pane.actions.size)
        assertEquals("Demó", template.actionStrip!!.actions.single().title.toString())
    }
    @Test fun `old hosts get row graphics without unsupported pane image API`() {
        val context = TestCarContext.createCarContext(RuntimeEnvironment.getApplication())
        context.updateHandshakeInfo(HandshakeInfo("test.host", 3))
        val template = DashboardCarScreen(context, 0).onGetTemplate() as PaneTemplate
        assertNull(template.pane.image)
        assertTrue(template.pane.rows.take(2).all { it.image != null })
    }
    @Test fun `graphic tiles retain stale numbers and remove old shift arrows`() {
        val s = DashboardState(telemetry = Telemetry(mapOf(
            Pid.RPM to Reading(null, 10, Quality.NO_DATA, lastKnownValue = 3_200.0),
            Pid.COOLANT to Reading(null, 10, Quality.NO_DATA, lastKnownValue = 90.0)
        )), nowMs = 20)
        val tiles = CarDashboardTiles.create(s)
        assertEquals("3200", tiles[0].value)
        assertEquals("90", tiles[1].value)
        assertFalse(tiles[0].fresh)
        assertFalse(tiles[1].fresh)
        assertEquals("dash", tiles[0].symbol)
        assertEquals(tiles[0].color, tiles[1].color)
    }
    @Test fun `demo and offline state are visible without changing row titles`() {
        val offline = CarRows.create(0, DashboardState())
        val demo = CarRows.create(0, DashboardState(phase = Phase.DEMO))
        assertEquals(offline.map { it.first }, demo.map { it.first })
        assertTrue(demo.last().second.contains("Demó"))
        assertTrue(offline.last().second.contains("Nincs kapcsolat"))
    }
    @Test fun `stale car rows retain their number and request grey styling`() {
        val data = Telemetry(mapOf(Pid.RPM to Reading(null, 10, Quality.NO_DATA, lastKnownValue = 800.0)))
        val state = DashboardState(telemetry = data, nowMs = 20)
        assertTrue(CarRows.create(1, state).first().second.contains("800"))
        assertFalse(CarRows.fresh(1, 0, state))
    }
}
