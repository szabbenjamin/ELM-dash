package hu.elmdash.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.elmdash.connection.DashboardController
import hu.elmdash.connection.DashboardState
import hu.elmdash.trip.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun value(n: Double?, digits: Int = 1) = n?.let { String.format(Locale.forLanguageTag("hu-HU"), "%.${digits}f", it) } ?: "—"
private fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy.MM.dd. HH:mm"))
private fun duration(seconds: Double): String {
    val minutes = seconds.toLong() / 60
    return if (minutes >= 60) "${minutes / 60} ó ${minutes % 60} p" else "$minutes p ${seconds.toLong() % 60} mp"
}

@Composable
internal fun TankCard(tank: TankEstimate, onOpen: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Becsült üzemanyagszint", fontWeight = FontWeight.SemiBold)
            Text(tank.percent?.let { "≈${value(it, 0)} %  •  ≈${value(tank.remainingLiters)} / 45 l" } ?: "Még nincs teletankolás megadva",
                fontSize = if (tank.percent != null) 27.sp else 16.sp, color = MaterialTheme.colorScheme.primary)
            tank.percent?.let { LinearProgressIndicator(progress = { (it / 100).toFloat() }, modifier = Modifier.fillMaxWidth()) }
            Text(if (tank.hasGaps) "Bizonytalan: volt nem megfigyelt fogyasztás. A valós szint alacsonyabb is lehet."
                else "45 literes Kalos-tank. A teletankolástól megfigyelt fogyasztás alapján, nem tankszenzorból.",
                color = if (tank.hasGaps) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            if (onOpen != null) TextButton(onClick = onOpen) { Text("Tankolás és útnapló") }
        }
    }
}

@Composable
internal fun JournalScreen(controller: DashboardController, state: DashboardState) {
    val log = state.journal
    var resetDialog by rememberSaveable { mutableStateOf(false) }
    var dailyExpanded by rememberSaveable { mutableStateOf(false) }
    var refuelDialog by rememberSaveable { mutableStateOf(false) }
    var paid by rememberSaveable { mutableStateOf("") }
    var pumped by rememberSaveable { mutableStateOf("") }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestLog by rememberUpdatedState(log)
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val snapshot = latestLog
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val output = checkNotNull(context.contentResolver.openOutputStream(uri))
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(JourneyCsv.export(snapshot)) }
                }.isSuccess
            }
            exportMessage = if (ok) "A CSV napló mentve." else "A fájl nem menthető. Próbáld újra másik helyre."
        }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("Napló", fontSize = 26.sp, fontWeight = FontWeight.SemiBold) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mai nap • ${state.today}", fontWeight = FontWeight.SemiBold)
                    Text("${value(state.daily.distanceKm, 2)} km • ${if (state.daily.containsEstimate) "≈" else ""}${value(state.daily.averageL100)} l/100 km", fontSize = 22.sp)
                    Text("${value(state.daily.fuelLiters, 2)} l • ${duration(state.daily.observedSeconds)}" + if (state.daily.hasGaps) " • részleges" else "", fontSize = 14.sp)
                    TextButton(onClick = { dailyExpanded = !dailyExpanded }) { Text(if (dailyExpanded) "Napi részletek bezárása" else "Napi részletek") }
                    if (dailyExpanded) {
                        TripDetails(state.daily)
                        Text("A mai utak összesítése, éjfélkor új nap kezdődik. A demó külön számol.", fontSize = 12.sp)
                    }
                }
            }
        }
        log.active?.let { record -> item(key = "active:${record.id}") { JourneyCard(record, active = true) } }
        if (state.simulated) item {
            Text("Aktuális demómérés", fontWeight = FontWeight.SemiBold)
            TripDetails(state.trip)
        }
        if (log.active != null || state.simulated) item {
            OutlinedButton(onClick = { resetDialog = true }) { Text("Útmérés nullázása") }
        }
        item { TankCard(log.tank) }
        item {
            Button(onClick = { pumped = ""; paid = ""; refuelDialog = true }, enabled = !state.simulated, modifier = Modifier.fillMaxWidth()) {
                Text("Teletankoltam")
            }
            if (state.simulated) Text("Demó alatt a valódi tank és napló nem módosul.", fontSize = 12.sp)
            Text("Csak akkor jelöld, amikor valóban tele lett. A százalék az app nélkül megtett utak és a fogyasztásbecslés hibáját nem tudja kijavítani.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        log.tank.fullAtMs?.let { at -> item {
            Text("Utolsó teletankolás: ${date(at)}\nAzóta megfigyelt fogyasztás: ${value(log.tank.consumedLiters, 2)} l", fontSize = 13.sp)
        } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Utazások", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                TextButton(onClick = { exporter.launch("elm-utnaplo-${state.today}.csv") }, enabled = log.journeys.isNotEmpty() || log.active != null) { Text("CSV mentése") }
            }
            exportMessage?.let { Text(it, fontSize = 12.sp) }
            Text("Az utolsó 200 valódi mérés. Indulás járó motornál; lezárás leállításkor, új motorindításkor vagy tartós kapcsolatvesztéskor. A korábbi verziók útjai nem rekonstruálhatók.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }

        if (log.journeys.isEmpty() && log.active == null) item {
            Text("Még nincs naplózott út. A következő valódi OBD-mérés automatikusan ide kerül.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(log.journeys, key = { it.id }) { JourneyCard(it) }
        if (log.refills.isNotEmpty()) item { Text("Teletankolások", fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        items(log.refills, key = { "refill:${it.id}" }) { refill ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(date(refill.atMs), fontWeight = FontWeight.SemiBold)
                    Text("Tele jelölve • 45 l alapérték", fontSize = 13.sp)
                    refill.pumpedLiters?.let { Text("Kúton betöltve: ${value(it, 2)} l", fontSize = 13.sp) }
                    refill.totalPaidHuf?.let { Text("Fizetve: ${value(it, 0)} Ft • ${value(refill.hufPerLiter, 1)} Ft/l", fontSize = 13.sp) }
                    refill.previousConsumedLiters?.let {
                        Text("Előző teletankolás óta megfigyelt: ${value(it, 2)} l" + if (refill.previousHasGaps) " • adathiányos" else "", fontSize = 12.sp)
                    }
                }
            }
        }
    }
    if (resetDialog) AlertDialog(onDismissRequest = { resetDialog = false }, title = { Text("Új mérés indítása?") },
        text = { Text("Az aktuális út lezárul és új mérés kezdődik. A napi összesítés és a mentett utak megmaradnak.") },
        confirmButton = { TextButton(onClick = { controller.resetTrip(); resetDialog = false }) { Text("Nullázás") } },
        dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("Mégse") } })
    if (refuelDialog) {
        val liters = pumped.replace(',', '.').toDoubleOrNull()
        val total = paid.replace(',', '.').toDoubleOrNull()
        val litersValid = pumped.isBlank() || (liters != null && liters.isFinite() && liters > 0 && liters <= KALOS_TANK_LITERS)
        val priceValid = paid.isBlank() || (total != null && total.isFinite() && total > 0 && liters != null && liters > 0 && total / liters in 200.0..2000.0)
        val valid = litersValid && priceValid
        AlertDialog(onDismissRequest = { refuelDialog = false }, title = { Text("Teletankolás rögzítése") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("A becsült szint most 100%-ra, 45 literre áll. A napi fogyasztás és az útnapló megmarad.")
                OutlinedTextField(value = pumped, onValueChange = { pumped = it }, label = { Text("Betöltött liter (elhagyható)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = !valid)
                OutlinedTextField(value = paid, onValueChange = { paid = it }, label = { Text("Fizetett összeg (Ft, elhagyható)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = !priceValid)
                if (priceValid && total != null && liters != null && liters > 0)
                    Text("Számított literár: ${value(total / liters)} Ft/l", fontWeight = FontWeight.SemiBold)
                Text("A következő utak benzinköltségét a fizetett összegből és a betöltött literből számoljuk. Ár nélkül az előző megadott tankolási ár marad.", fontSize = 12.sp)
                if (!valid) Text("Az összeghez add meg a litert is (0–45 l). A számított literár 200–2000 Ft/l lehet.", color = MaterialTheme.colorScheme.error)
            } }, confirmButton = { TextButton(enabled = valid && !state.simulated, onClick = {
                controller.markFullTank(if (pumped.isBlank()) null else liters, if (paid.isBlank()) null else total)
                refuelDialog = false
            }) { Text("Teletankolás mentése") } },
            dismissButton = { TextButton(onClick = { refuelDialog = false }) { Text("Mégse") } })
    }
}

@Composable
private fun JourneyCard(record: JourneyRecord, active: Boolean = false) {
    var expanded by rememberSaveable(record.id) { mutableStateOf(active) }
    val t = record.summary; val s = record.stats
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (active) "Folyamatban • ${date(record.startedAtMs)}" else date(record.startedAtMs), fontWeight = FontWeight.SemiBold)
            Text("${value(t.distanceKm, 2)} km • ${if (t.containsEstimate) "≈" else ""}${value(t.averageL100)} l/100 km", fontSize = 22.sp)
            Text("${duration(record.durationSeconds)} • ${value(t.fuelLiters, 2)} l" + if (t.hasGaps) " • részleges" else "", fontSize = 14.sp)
            Text(record.estimatedCostHuf?.let { "Becsült benzinköltség: ≈${value(it, 0)} Ft" }
                ?: "Benzinköltség: — (nincs használható ár vagy fogyasztásadat)", fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary)
            record.petrolPrice?.let { price ->
                Text("Benzin: ${value(price.hufPerLiter)} Ft/l • " + (if (price.sourceUrl == PetrolPrice.MANUAL_SOURCE) "Saját tankolás" else "Holtankoljak.hu") +
                    (if (price.sourceUrl == PetrolPrice.MANUAL_SOURCE) "\nTankolás: " else "\nÁr lekérve: ") + date(price.fetchedAtMs) + (if (price.cached) " • korábbi mentett ár" else "") +
                    (if (price.appliedAfterStart) "\nUtólag rögzített ár" else ""), fontSize = 12.sp)
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Részletek bezárása" else "Út részletei") }
            if (expanded) {
                fun line(label: String, v: String) = "$label: $v"
                Text(listOf(
                    line("Indulás", date(record.startedAtMs)),
                    line("Befejezés", record.endedAtMs?.let { date(it) } ?: "folyamatban"),
                    line("Lezárás", record.end?.label ?: "—"),
                    line("Átlag / legnagyobb sebesség", "${value(record.averageSpeed)} / ${value(s.maxSpeed, 0)} km/h"),
                    line("Mozgás / alapjárat megfigyelve", "${duration(s.movingSeconds)} / ${duration(s.idleSeconds)}"),
                    line("Alapjárati fogyasztás", "${value(s.idleFuelLiters, 3)} l"),
                    line("Legnagyobb fordulatszám", "${value(s.maxRpm, 0)} rpm"),
                    line("Vízhőfok min–max", "${value(s.minCoolant, 0)}–${value(s.maxCoolant, 0)} °C"),
                    line("Átlagos motorterhelés", "${value(s.averageLoad, 0)} %"),
                    line("Fogyasztásadat lefedettsége", "${value(t.coveragePercent, 0)} %"),
                    line("Átlaghoz használt szakasz", "${value(t.pairedFuelLiters, 3)} l / ${value(t.pairedDistanceKm, 2)} km"),
                    line("Forrás", record.sources.joinToString { it.label }.ifEmpty { "Nincs fogyasztásadat" }),
                    line("Profil", record.profile)
                ).joinToString("\n"), fontSize = 13.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!t.fromEngineStart) Text("Mérés a csatlakozástól; az indulás előtti rész nem ismert.", fontSize = 12.sp)
                if (t.hasGaps) Text("A hiányzó szakaszokat nem töltjük ki kitalált adatokkal.", fontSize = 12.sp)
            }
        }
    }
}
