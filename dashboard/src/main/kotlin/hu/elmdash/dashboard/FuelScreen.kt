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
    return if (minutes >= 60) "${minutes / 60} ó ${minutes % 60} p ${seconds.toLong() % 60} mp" else "$minutes p ${seconds.toLong() % 60} mp"
}

@Composable
internal fun FuelScreen(controller: DashboardController, state: DashboardState) {
    val log = state.journal
    var refuelDialog by rememberSaveable { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<String?>(null) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text("Üzemanyag", fontSize = 26.sp, fontWeight = FontWeight.SemiBold) }
        item { TankCard(log.tank, state.range) }
        item {
            Button(onClick = { refuelDialog = true }, enabled = !state.simulated, modifier = Modifier.fillMaxWidth()) {
                Text("Tankoltam")
            }
            if (state.simulated) Text("Demó alatt a valódi tank és napló nem módosul.", fontSize = 12.sp)
            Text("Blokkal vagy kézzel is rögzíthető, részleges és teletankolás egyaránt.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        log.tank.fullAtMs?.let { at -> item {
            Text("Utolsó teletankolás: ${date(at)}\nAzóta megfigyelt fogyasztás: ${value(log.tank.consumedLiters, 2)} l", fontSize = 13.sp)
        } }
        item { Text("Tankolási napló", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (log.refills.isEmpty()) item { Text("Még nincs rögzített tankolás.") }
        items(log.refills, key = { "refill:${it.id}" }) { refill ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    refill.receiptPhoto?.let { photo -> TextButton(onClick = { receipt = photo }) { Text("Blokkfotó megnyitása") } }
                    Text(date(refill.atMs) + if(refill.isFull) " · Tele tank" else " · Részleges", fontWeight = FontWeight.SemiBold)
                    Text(refill.fuelName ?: "Benzintípus nincs megadva", fontWeight = FontWeight.SemiBold)
                    Text("${value(refill.pumpedLiters, 2)} liter  •  ${value(refill.totalPaidHuf, 0)} Ft", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                    refill.pumpedLiters?.let { Text("Kúton betöltve: ${value(it, 2)} l", fontSize = 13.sp) }
                    refill.totalPaidHuf?.let { Text("Fizetve: ${value(it, 0)} Ft • ${value(refill.hufPerLiter, 1)} Ft/l", fontSize = 13.sp) }
                    refill.differenceLiters?.let { difference ->
                        Text("Teljes ciklus tankolása − becslés: ${value(difference, 2)} l (${value(refill.differencePercent)}%). Pozitív: az app kevesebbet becsült.", fontSize = 12.sp)
                        Text(if (refill.previousHasGaps) "Adathiány miatt ez nem tiszta becslési hiba; kalibrációra nem használható."
                            else "Mért tankolás / nyers becslés: ${value(refill.suggestedMultiplier, 3)}. Megbízható, legalább 10 literes szakasznál automatikus, csak felfelé történő tanulás.", fontSize = 12.sp)
                    }
                    refill.previousConsumedLiters?.let {
                        Text("Előző teletankolás óta megfigyelt: ${value(it, 2)} l" + if (refill.previousHasGaps) " • adathiányos" else "", fontSize = 12.sp)
                    }
                }
            }
        }
    }
    if (refuelDialog) RefuelDialog(onDismiss = { refuelDialog = false }, onSave = { name, liters, total, photo, full ->
        controller.recordRefill(liters, total, name, photo, full)
        refuelDialog = false
    })
    receipt?.let { ReceiptPhotoDialog(it) { receipt = null } }
}
