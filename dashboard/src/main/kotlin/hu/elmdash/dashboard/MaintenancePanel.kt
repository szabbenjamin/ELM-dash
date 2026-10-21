package hu.elmdash.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hu.elmdash.connection.MaintenanceStore
import hu.elmdash.trip.ServiceEntry
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun MaintenanceScreen() {
    val context = LocalContext.current
    val store = remember { MaintenanceStore(context) }
    val prefs = remember { context.getSharedPreferences("elm-maintenance", android.content.Context.MODE_PRIVATE) }
    var weather by remember { mutableStateOf(prefs.getBoolean("weather", false)) }
    var entries by remember { mutableStateOf(store.entries()) }
    var savedOdo by remember { mutableStateOf(store.odometer) }
    var odo by rememberSaveable { mutableStateOf(store.odometer?.toString().orEmpty()) }
    var name by rememberSaveable { mutableStateOf("Olaj és olajszűrő") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var km by rememberSaveable { mutableStateOf("") }
    var interval by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val due = entries.groupBy { it.name.lowercase() }.values.map { it.maxBy { e -> e.performed } }
        .filter { it.soon(today, savedOdo) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Szerviznapló", style = MaterialTheme.typography.headlineSmall)
                due.forEach { Text("${it.name}: " + if (it.due(today, savedOdo)) "esedékes" else "hamarosan esedékes", color = MaterialTheme.colorScheme.tertiary) }
                OutlinedTextField(odo, { odo = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Aktuális kilométeróra (kézzel frissítendő)") })
                Button(onClick = {
                    val n = odo.replace(',', '.').toDoubleOrNull()
                    if (n != null && n.isFinite() && n >= 0) { store.odometer = n; savedOdo = n; entries = store.entries(); error = null }
                    else error = "Érvényes kilométeróra szükséges."
                }) { Text("Kilométeróra mentése") }
                Text("Az app nem ismeri a teljes kilométeróra-állást. A kilométeres emlékeztető a legutóbb kézzel megadott értékhez viszonyít.", style = MaterialTheme.typography.bodySmall)
                Row { Switch(weather, { weather = it; prefs.edit().putBoolean("weather",it).apply(); hu.elmdash.connection.MaintenanceWorker.schedule(context) }); Text("Időjárásos guminyomás-emlékeztető") }
                Text("Naponta ellenőriz, 7 °C alatt vagy legalább 10 °C lehűléskor szól, legfeljebb hetente. Az Open-Meteónak az utolsó, legfeljebb 7 napos parkolóhely ~1 km-re kerekített helyét küldi. Parkolóhely és internet nélkül nincs időjárásos jelzés. Forrás: Open-Meteo (CC BY 4.0). Értesítési engedély szükséges.", style = MaterialTheme.typography.bodySmall)
                Text("Új elvégzett szerviz", style = MaterialTheme.typography.titleMedium)
                Row { TextButton(onClick = { name = "Olaj és olajszűrő" }) { Text("Olaj") }; TextButton(onClick = { name = "Vezérműszíj" }) { Text("Vezérlés") }; TextButton(onClick = { name = "Guminyomás" }) { Text("Gumi") } }
                OutlinedTextField(name, { name = it.take(100) }, modifier = Modifier.fillMaxWidth(), label = { Text("Alkatrész / művelet") })
                OutlinedTextField(date, { date = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Elvégzés dátuma (ÉÉÉÉ-HH-NN)") })
                OutlinedTextField(km, { km = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Kilométeróra a szervizkor") })
                OutlinedTextField(interval, { interval = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Újra esedékes ennyi km után (opcionális)") })
                OutlinedTextField(months, { months = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Újra esedékes ennyi hónap után (opcionális)") })
                OutlinedTextField(note, { note = it.take(2000) }, modifier = Modifier.fillMaxWidth(), label = { Text("Megjegyzés / költség / alkatrész") })
                Text("A dátum- és km-határ közül az előbb elért számít. Előjelzés: 30 nap vagy 500 km. Ez emlékeztető, nem alkatrészállapot-mérés.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    runCatching {
                        val d = LocalDate.parse(date); require(!d.isAfter(today))
                        val entry = ServiceEntry(UUID.randomUUID().toString(),name.trim(),d,km.replace(',', '.').toDouble(),
                            interval.takeIf { it.isNotBlank() }?.replace(',', '.')?.toDouble(), months.takeIf { it.isNotBlank() }?.toInt(),note)
                        store.save(entry); entries = store.entries(); error = null
                        km = ""; interval = ""; months = ""; note = ""
                    }.onFailure { error = "Ellenőrizd a dátumot, a kilométert és a pozitív intervallumokat." }
                }) { Text("Elvégzett szerviz mentése") }
                entries.forEach { entry ->
                    HorizontalDivider()
                    Text("${entry.name} • ${entry.performed} • ${entry.odometerKm.toInt()} km")
                    Text("Következő: " + listOfNotNull(entry.intervalKm?.let { "${(entry.odometerKm + it).toInt()} km" },entry.intervalMonths?.let { entry.performed.plusMonths(it.toLong()).toString() }).joinToString(" vagy "))
                    if (entry.note.isNotBlank()) Text(entry.note)
                    TextButton(onClick = { store.remove(entry.id); entries = store.entries() }) { Text("Hibás bejegyzés törlése") }
                }
    }
}
