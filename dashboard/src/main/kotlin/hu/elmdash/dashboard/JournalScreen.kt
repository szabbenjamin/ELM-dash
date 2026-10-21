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
internal fun TankCard(tank: TankEstimate, range: RangeEstimate, onOpen: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Becsült üzemanyagszint", fontWeight = FontWeight.SemiBold)
            Text(tank.percent?.let { "≈${value(it, 0)} %  •  ≈${value(tank.remainingLiters)} / 45 l" } ?: "Még nincs teletankolás megadva",
                fontSize = if (tank.percent != null) 27.sp else 16.sp, color = MaterialTheme.colorScheme.primary)
            Text(range.displayKm?.let { "Becsült hatótáv: ≈$it km" } ?: "Becsült hatótáv: —",
                fontSize = 21.sp, color = MaterialTheme.colorScheme.primary)
            Text(if (range.km != null) "${value(range.averageL100)} l/100 km • ${range.basis}" + if (range.uncertain) " • bizonytalan" else ""
                else if (tank.remainingLiters == null) "Előbb jelöld a teletankolást."
                else "Legalább 1 km fogyasztással együtt mért szakasz szükséges.", fontSize = 12.sp)
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
        item { TankCard(log.tank, state.range) }
        item {
            Button(onClick = { refuelDialog = true }, enabled = !state.simulated, modifier = Modifier.fillMaxWidth()) {
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
                    Text(refill.fuelName ?: "Benzintípus nincs megadva", fontWeight = FontWeight.SemiBold)
                    Text("${value(refill.pumpedLiters, 2)} liter  •  ${value(refill.totalPaidHuf, 0)} Ft", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                    refill.pumpedLiters?.let { Text("Kúton betöltve: ${value(it, 2)} l", fontSize = 13.sp) }
                    refill.totalPaidHuf?.let { Text("Fizetve: ${value(it, 0)} Ft • ${value(refill.hufPerLiter, 1)} Ft/l", fontSize = 13.sp) }
                    refill.differenceLiters?.let { difference ->
                        Text("Tankolás − becslés: ${value(difference, 2)} l (${value(refill.differencePercent)}%). Pozitív: az app kevesebbet becsült.", fontSize = 12.sp)
                        Text(if (refill.previousHasGaps) "Adathiány miatt ez nem tiszta becslési hiba; kalibrációra nem használható."
                            else "Javasolt szorzó a jelenlegi korrekcióhoz: ${value(refill.suggestedMultiplier, 3)}. Nem alkalmazzuk automatikusan.", fontSize = 12.sp)
                    }
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
    if (refuelDialog) RefuelDialog(onDismiss = { refuelDialog = false }, onSave = { name, liters, total ->
        controller.markFullTank(liters, total, name)
        refuelDialog = false
    })

}

@Composable
private fun JourneyCard(record: JourneyRecord, active: Boolean = false) {
    var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
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
            TextButton(onClick = { expanded = true }) { Text("Út részletei és térkép") }
        }
    }
    if (expanded) JourneyDialog(record) { expanded = false }
}
