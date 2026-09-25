package hu.elmdash.auto

import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.*
import androidx.car.app.model.*
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import hu.elmdash.connection.*
import hu.elmdash.obd.Pid
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/** Unsupported local experiment. No phone Compose / arbitrary surface is projected to Auto. */
class DashboardCarService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        check(BuildConfig.DEBUG) { "Auto Lab is debug-only" }
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }
    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = DashboardCarScreen(carContext, 0)
    }
}

class DashboardCarScreen(context: CarContext, private val page: Int) : Screen(context) {
    private val controller = DashboardGraph.get(context)
    private var lastContent: Any? = null

    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    val content = if (page == 0) CarDashboardTiles.create(controller.state.value)
                        else CarRows.create(page, controller.state.value)
                    if (content != lastContent) { lastContent = content; invalidate() }
                    delay(2_000) // Host may throttle more. Stable row titles allow template refreshes.
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val state = controller.state.value
        val control = Action.Builder()
            .setTitle(if (state.active) "Leállítás" else "Demó")
            .setOnClickListener {
                if (controller.state.value.active) {
                    controller.stop()
                    carContext.stopService(Intent(carContext, ObdService::class.java))
                } else controller.startDemo()
                invalidate()
            }.build()
        val actions = ActionStrip.Builder().addAction(control).build()
        val pane = Pane.Builder()
        CarRows.create(page, state).forEachIndexed { index, (label, value) ->
            val body = SpannableString(value)
            if (!CarRows.fresh(page, index, state)) {
                body.setSpan(ForegroundCarColorSpan.create(CarColor.createCustom(0xFF737D86.toInt(), 0xFF8A959F.toInt())),
                    0, body.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val row = Row.Builder().setTitle(label).addText(body)
            if (page == 0 && carContext.carAppApiLevel < 4 && index < 2)
                row.setImage(CarDashboardTiles.indicator(state, index), Row.IMAGE_TYPE_LARGE)
            pane.addRow(row.build())
        }
        if (page == 0) {
            if (carContext.carAppApiLevel >= 4) pane.setImage(CarDashboardTiles.overview(state))
            pane.addAction(Action.Builder().setTitle("Motor / szenzorok")
                .setOnClickListener { screenManager.push(DashboardCarScreen(carContext, 1)) }.build())
            pane.addAction(Action.Builder().setTitle("Út és fogyasztás")
                .setOnClickListener { screenManager.push(DashboardCarScreen(carContext, 3)) }.build())
        } else if (page == 1) {
            pane.addAction(Action.Builder().setTitle("Szenzorok")
                .setOnClickListener { screenManager.push(DashboardCarScreen(carContext, 2)) }.build())
        }
        return PaneTemplate.Builder(pane.build()).setTitle(if (page == 0) "Kalos 1.2 • LAB" else "ELM Dash • LAB")
            .setActionStrip(actions).setHeaderAction(if (page == 0) Action.APP_ICON else Action.BACK).build()
    }
}

internal object CarRows {
    private fun n(value: Double?, unit: String, digits: Int = 1) =
        if (value == null) "— $unit" else String.format(Locale.forLanguageTag("hu-HU"), "%.${digits}f %s", value, unit)

    fun create(page: Int, s: DashboardState): List<Pair<String, String>> {
        fun p(pid: Pid, digits: Int = 0): String {
            val live = s.telemetry.value(pid, s.nowMs)
            val last = s.telemetry.readings[pid]?.lastKnownValue
            return n(live ?: last, pid.unit, digits) + if (live == null && last != null) " (utolsó)" else ""
        }
        if (page == 0) return listOf(
            "Fordulatszám / terhelés" to "${p(Pid.RPM)} • ${p(Pid.LOAD)}" + (s.driving.estimatedGear?.let { " • ≈$it." } ?: ""),
            "Vízhőfok" to p(Pid.COOLANT),
            "Fogyasztás / útátlag" to "${n(s.fuelDisplay.value, s.fuelDisplay.unit)} • ${n(s.trip.averageL100, "l/100")}",
            "Sebesség / kapcsolat" to "${p(Pid.SPEED)} • ${s.phase.label}"
        )
        val rows = when (page) {
            3 -> listOf(
                "Pillanatnyi fogyasztás" to (n(s.fuelDisplay.value, s.fuelDisplay.unit) + " • " +
                    if (s.fuelDisplay.fresh) s.fuelDisplay.source.label
                    else if (s.fuelDisplay.value != null) "utolsó ismert adat" else "várakozás adatra"),
                "Út átlaga" to (n(s.trip.averageL100, "l/100 km") + if (s.trip.hasGaps) " • részleges" else if (s.trip.containsEstimate) " • becslés" else ""),
                "Mért út / üzemanyag" to "${n(s.trip.distanceKm, "km", 2)} / ${n(s.trip.fuelLiters, "l", 2)}"
            )
            1 -> listOf("Fordulatszám" to p(Pid.RPM), "Sebesség" to p(Pid.SPEED),
                "Terhelés / hűtőfolyadék" to "${p(Pid.LOAD)} / ${p(Pid.COOLANT)}")
            else -> listOf("Szívócsőnyomás / MAP" to p(Pid.MAP), "Fojtószelep / TPS" to p(Pid.TPS),
                "Feszültség / MAF" to "${p(Pid.VOLTAGE, 1)} / ${p(Pid.MAF, 1)} • ${s.telemetry.readings[Pid.VOLTAGE]?.source ?: "—"}")
        }
        return rows + ("Kapcsolat • kísérleti mód" to if (s.active) s.phase.label
            else "${s.phase.label} • Demó: a fejlécben. OBD-kapcsolat: a telefonon.")
    }

    fun fresh(page: Int, row: Int, s: DashboardState): Boolean {
        if (page == 0) return when (row) {
            0 -> s.telemetry.value(Pid.RPM, s.nowMs) != null && s.telemetry.value(Pid.LOAD, s.nowMs) != null
            1 -> s.telemetry.value(Pid.COOLANT, s.nowMs) != null
            2 -> s.fuelDisplay.fresh
            else -> s.telemetry.value(Pid.SPEED, s.nowMs) != null
        }
        if (row == 3) return true
        if (page == 3) return row != 0 || s.fuelDisplay.fresh
        val pids = if (page == 1) listOf(listOf(Pid.RPM), listOf(Pid.SPEED), listOf(Pid.LOAD, Pid.COOLANT))
            else listOf(listOf(Pid.MAP), listOf(Pid.TPS), listOf(Pid.VOLTAGE, Pid.MAF))
        return pids[row].all { s.telemetry.value(it, s.nowMs) != null }
    }
}
