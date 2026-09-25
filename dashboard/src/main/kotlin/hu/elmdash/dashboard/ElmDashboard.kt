package hu.elmdash.dashboard

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.elmdash.connection.*
import hu.elmdash.obd.*
import hu.elmdash.trip.*
import java.util.Locale

private val Lime = Color(0xFFB8F568)
private val Ink = Color(0xFF0B1117)
private val Panel = Color(0xFF151F28)
private val Muted = Color(0xFF94A5B4)
private val Blue = Color(0xFF87C9FF)
private val Amber = Color(0xFFFFCD80)
private fun number(value: Double?, digits: Int = 1): String = value?.let { String.format(Locale.forLanguageTag("hu-HU"), "%.${digits}f", it) } ?: "—"

@Composable
fun ElmDashboard(controller: DashboardController, autoSurface: String, radioContent: (@Composable () -> Unit)? = null) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var fuelSettingsOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(settingsOpen) { if (fuelSettingsOpen) fuelSettingsOpen = false else settingsOpen = false }
    val stop = {
        AutoObd.pause(context)
        controller.stop()
        context.stopService(Intent(context, ObdService::class.java))
        Unit
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Ink, background = Ink,
        surface = Panel, onSurface = Color(0xFFF3F6F8), onSurfaceVariant = Muted, secondary = Blue)) {
        Scaffold(containerColor = Ink, bottomBar = {
            NavigationBar(containerColor = Ink, tonalElevation = 0.dp) {
                (listOf("Műszerfal", "Napló") + if (radioContent != null) listOf("Rádió") else emptyList()).forEachIndexed { i, title ->
                    NavigationBarItem(selected = !settingsOpen && tab == i, onClick = { tab = i; settingsOpen = false; fuelSettingsOpen = false },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Lime, selectedTextColor = Lime,
                            indicatorColor = Lime.copy(alpha = 0.14f), unselectedIconColor = Muted, unselectedTextColor = Muted),
                        icon = { Text(listOf("◫", "▤", "♫")[i], fontSize = 23.sp) }, label = { Text(title) })
                }
            }
        }) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ELM / DASH", fontWeight = FontWeight.Black, fontSize = 23.sp, letterSpacing = 2.sp)
                        Text(when (autoSurface) { "media" -> "ANDROID AUTO MÉDIA  ·  0.13"; "unsupported" -> "ANDROID AUTO LAB  ·  0.13"; else -> "DIRECT BLUETOOTH OBD  ·  0.13" }, color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    }
                    if (settingsOpen) IconButton(onClick = { if (fuelSettingsOpen) fuelSettingsOpen = false else settingsOpen = false },
                        modifier = Modifier.semantics { contentDescription = "Vissza" }) { Text("←", fontSize = 26.sp) }
                    else IconButton(onClick = { settingsOpen = true; fuelSettingsOpen = false },
                        modifier = Modifier.semantics { contentDescription = "Kapcsolat és beállítások" }) { Text("⚙", fontSize = 28.sp) }
                    if (state.active) TextButton(onClick = stop) { Text("Leállítás") }
                }
                if (state.carUnavailable && !settingsOpen && tab == 2) {
                    Text("Nincs kapcsolat az autóval", color = Amber, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp))
                }
                if (settingsOpen) {
                    if (fuelSettingsOpen) FuelSettingsScreen(controller, state)
                    else {
                        TextButton(onClick = { fuelSettingsOpen = true }) { Text("Fogyasztás és autó beállításai →") }
                        ConnectionScreen(controller, state, autoSurface, onStarted = { settingsOpen = false; tab = 0 })
                    }
                } else when (tab) {
                    0 -> TelemetryScreen(state, onJournal = { tab = 1 }, onConnect = { settingsOpen = true; fuelSettingsOpen = false })
                    1 -> JournalScreen(controller, state)
                    2 -> radioContent?.invoke()
                }
            }
        }
    }
}

@Composable
private fun Status(state: DashboardState) {
    val color = when (state.phase) { Phase.LIVE -> Lime; Phase.DEMO -> Blue; else -> Amber }
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(color, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(8.dp))
                Text(if (state.carUnavailable) "Nincs kapcsolat az autóval" else state.phase.label, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text(if (state.phase == Phase.DEMO) "DEMO" else "OBD-II", color = color, fontSize = 10.sp)
            }
            Text(state.message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun TelemetryScreen(state: DashboardState, onJournal: () -> Unit, onConnect: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 800.dp) 4 else if (maxWidth >= 600.dp) 3 else 2
        LazyVerticalGrid(columns = GridCells.Fixed(columns), verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }) { Status(state) }
            item(span = { GridItemSpan(maxLineSpan) }) { TankCard(state.journal.tank, onJournal) }
            if (!state.active) item(span = { GridItemSpan(maxLineSpan) }) {
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Csatlakozás") }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Fogyasztás", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 10.dp))
            }
            item(span = { GridItemSpan(if (columns > 2) 2 else 1) }) {
                val display = state.fuelDisplay
                MetricCard("10 MP ÁTLAG", number(display.value), display.unit,
                    if (state.telemetry.value(Pid.SPEED, state.nowMs)?.let { it < 5 } == true) "Álló/lassú • l/100 km nem értelmezhető"
                    else if (display.fresh) display.source.label else if (display.value != null) "Utolsó ismert adat" else state.fuel.unavailableReason ?: "Adatra vár",
                    if (display.fresh) Lime else Muted, true)
            }
            item(span = { GridItemSpan(if (columns == 4) 2 else 1) }) {
                MetricCard("MAI ÁTLAG", (if (state.daily.containsEstimate && state.daily.averageL100 != null) "≈" else "") + number(state.daily.averageL100), "l/100 km",
                    when { !state.daily.started -> "Mérésre vár"
                        state.daily.averageL100 == null -> "Legalább 100 m fogyasztással mért út kell"
                        state.daily.hasGaps -> if (state.daily.containsEstimate) "Részleges • becsült adatokból" else "Részleges napi adatokból"
                        state.daily.containsEstimate -> "Becslésből számolva"
                        else -> "Mért szakasz átlaga" }, Blue, true)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("Ma: ${number(state.daily.distanceKm, 2)} km • ${state.today}", color = Blue, fontSize = 13.sp)
                    Text("Fogyasztási átlaghoz: ${number(state.daily.pairedFuelLiters, 2)} l / ${number(state.daily.pairedDistanceKm, 2)} km" +
                        if (state.daily.containsEstimate) " • becslés" else "", color = Muted, fontSize = 12.sp)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Motor és jármű", fontSize = 14.sp, color = Muted)
                    Text("${number(state.trip.distanceKm, 2)} km", color = Muted, fontSize = 12.sp)
                }
            }
            val metrics = listOf(Pid.SPEED to "SEBESSÉG", Pid.RPM to "FORDULATSZÁM", Pid.LOAD to "MOTOR TERHELÉSE",
                Pid.COOLANT to "HŰTŐFOLYADÉK", Pid.MAP to "SZÍVÓCSŐ / MAP", Pid.TPS to "FOJTÓSZELEP / TPS",
                Pid.VOLTAGE to "FESZÜLTSÉG", Pid.MAF to "LÉGTÖMEG / MAF",
                Pid.IAT to "BESZÍVOTT LEVEGŐ / IAT", Pid.FUEL_RATE to "ECU ÜZEMANYAGÁRAM")
            items(metrics, key = { it.first.name }) { (pid, label) ->
                val value = state.telemetry.value(pid, state.nowMs)
                val reading = state.telemetry.readings[pid]
                val hint = when {
                    reading?.quality == Quality.UNSUPPORTED -> "Nem támogatott PID"
                    reading?.quality == Quality.ERROR -> "Hibás válasz"
                    value == null -> if (reading?.lastKnownValue != null) "Utolsó ismert adat" else "Adatra vár"
                    pid == Pid.VOLTAGE -> reading?.source ?: "ECU-feszültség"
                    else -> "Élő • 01 %02X".format(pid.code)
                }
                MetricCard(label, number(value ?: reading?.lastKnownValue, if (pid in listOf(Pid.VOLTAGE, Pid.MAF)) 1 else 0), pid.unit,
                    hint, if (value == null) Muted else if (pid == Pid.COOLANT && value >= 105) Amber else Color.White)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("A szürke szám az utolsó ismert, már nem friss érték. A — azt jelzi, hogy még nem érkezett adat. A 10 másodperces átlag mindig l/100 km. Álló/lassú helyzetben — látszik; a napi összesítés az eredeti mintákból készül.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, unit: String, hint: String, accent: Color, hero: Boolean = false) {
    Surface(color = if (hero) Color(0xFF1B2B2C) else Panel, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(min = if (hero) 167.dp else 147.dp).padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(value, color = accent, fontSize = if (hero) 43.sp else 34.sp, fontWeight = FontWeight.SemiBold,
                    style = TextStyle(fontFeatureSettings = "tnum"), maxLines = 1)
                Text(unit, color = Muted, fontSize = 12.sp)
            }
            Text(hint, color = if (hero) accent.copy(alpha = 0.9f) else Muted, fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ConnectionScreen(controller: DashboardController, state: DashboardState, autoSurface: String, onStarted: () -> Unit) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf(controller.pairedDevices()) }
    var selected by rememberSaveable { mutableStateOf(controller.store.address) }
    var insecure by rememberSaveable { mutableStateOf(controller.store.insecure) }
    var permission by remember { mutableStateOf(controller.hasBluetoothPermission()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permission = controller.hasBluetoothPermission(); devices = controller.pairedDevices()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Kapcsolat", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Status(state)
        Text("1. Párosítás a telefonon", fontWeight = FontWeight.SemiBold)
        Text("Bluetooth Classic / SPP ELM327 adapter szükséges. Párosítsd az Android beállításaiban, majd válaszd ki alább. A Torque kapcsolatát előbb állítsd le.", color = Muted, fontSize = 14.sp)
        OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Bluetooth-beállítások") }
        Text("2. Adapter kiválasztása", fontWeight = FontWeight.SemiBold)
        Button(onClick = {
            val needed = buildList {
                if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (needed.isNotEmpty()) launcher.launch(needed.toTypedArray())
            else { permission = true; devices = controller.pairedDevices() }
        }) { Text(if (permission) "Eszközlista frissítése" else "Bluetooth-hozzáférés engedélyezése") }
        if (!permission) Text("A Közeli eszközök engedély szükséges a kapcsolathoz. Elutasítás után az alkalmazás rendszerbeállításaiban is engedélyezheted.", color = Amber, fontSize = 13.sp)
        if (permission && devices.isEmpty()) Text("Nincs párosított eszköz. A demó adapter nélkül is elindítható.", color = Muted)
        devices.forEach { (address, name) ->
            Surface(onClick = { selected = address; controller.store.address = address }, color = if (address == selected) Color(0xFF283D30) else Panel, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == address, onClick = { selected = address; controller.store.address = address })
                    Column { Text(name, fontWeight = FontWeight.Medium); Text(address, color = Muted, fontSize = 12.sp) }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(insecure, onCheckedChange = { insecure = it; controller.store.insecure = it }, enabled = !state.active)
            Column { Text("Kompatibilis RFCOMM mód"); Text("Csak ha a normál kapcsolat nem működik.", color = Muted, fontSize = 12.sp) }
        }
        Button(enabled = selected.isNotBlank() && permission && !state.active, onClick = {
            try {
                val intent = Intent(context, ObdService::class.java).putExtra("address", selected).putExtra("insecure", insecure)
                context.startForegroundService(intent)
                onStarted()
            } catch (e: Exception) { controller.error(e.message ?: "Nem sikerült elindítani") }
        }, modifier = Modifier.fillMaxWidth()) { Text("OBD-kapcsolat indítása") }

        if (autoSurface == "media") {
            var automatic by remember { mutableStateOf(controller.store.autoConnect) }
            var headUnit by remember { mutableStateOf(controller.store.headUnitAddress) }
            var backgroundAllowed by remember { mutableStateOf(AutoObd.backgroundAllowed(context)) }
            val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                backgroundAllowed = AutoObd.backgroundAllowed(context)
                if (backgroundAllowed) AutoObd.resume(context)
            }
            Text("Automatikus OBD-kapcsolat", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(automatic, onCheckedChange = {
                    automatic = it; controller.store.autoConnect = it
                    if (it) AutoObd.resume(context) else {
                        AutoObd.pause(context)
                        if (AutoObd.automaticRunning) context.stopService(Intent(context, ObdService::class.java))
                    }
                })
                Text("Kapcsolódás az Android Auto indulásakor", modifier = Modifier.padding(start = 12.dp))
            }
            Text("A kiválasztott OBD-adapter azonnal mentődik. Ha nem érhető el, az AA-kapcsolat alatt újrapróbálkozunk. A kézi Leállítás az aktuális AA-kapcsolatra szünetelteti az automatikát.", color = Muted, fontSize = 13.sp)
            Text("Autós fejegység Bluetooth-eszköze", fontWeight = FontWeight.Medium)
            Text("Válaszd a Carpuride-ot, hogy annak Bluetooth-csatlakozása a bezárt alkalmazást is felébressze. Ez külön eszköz az OBD-adaptertől.", color = Muted, fontSize = 13.sp)
            devices.filter { it.first != selected }.forEach { (address, name) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(headUnit == address, onClick = { headUnit = address; controller.store.headUnitAddress = address })
                    Text(name)
                }
            }
            TextButton(onClick = { headUnit = ""; controller.store.headUnitAddress = "" }) { Text("Fejegység-ébresztés törlése") }
            Text(if (backgroundAllowed) "Háttérindítás engedélyezve" else "A megbízható háttérindításhoz egyszeri rendszerengedély szükséges.", color = if (backgroundAllowed) Lime else Amber)
            if (!backgroundAllowed) OutlinedButton(onClick = {
                batteryLauncher.launch(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:${context.packageName}")))
            }) { Text("Automatikus háttérkapcsolat engedélyezése") }
            TextButton(onClick = { AutoObd.resume(context) }) { Text("Automatika folytatása") }
        }
        Text("Android Auto", fontWeight = FontWeight.SemiBold)
        Text(if (autoSurface == "media") "Az AA Média kísérleti változat az Android Auto médialejátszójában mutatja az adatokat. A cím a pillanatnyi és mai átlagfogyasztás; a műszerkép a fordulatszámot és vízhőfokot is mutatja. A Rádió lapon indítható valódi hanglejátszás az AA médiakártyájához. Az előző/következő gomb állomást vált; a rács gomb műszerlapot. Az automatikus kapcsolat a mentett adaptert használja. AA fejlesztői beállítások: Fejlesztői alkalmazásmód és Ismeretlen források. Ez nem hivatalos dashboard-kategória."
            else if (autoSurface == "unsupported") "Ez az Auto Lab debug változat. A kísérleti dashboard külön Car App Library felületen jelenik meg, ha a host engedi. Előbb itt indíts OBD-kapcsolatot vagy demót. Az Unknown sources kapcsoló önmagában nem elég; részletek a README-ben."
            else "A telefonos változat nem tartalmaz Auto-szolgáltatást. Az OBD-dashboard nem hivatalos Android Auto kategória. A projekthez külön Auto Lab debug build tartozik.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun FuelSettingsScreen(controller: DashboardController, state: DashboardState) {
    var profile by remember { mutableStateOf(controller.store.fuelSettings.profile) }
    var factor by remember { mutableFloatStateOf(controller.store.fuelSettings.correction.toFloat()) }
    var efficiency by remember { mutableFloatStateOf(controller.store.fuelSettings.volumetricEfficiency.toFloat()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Autó és fogyasztás", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text("Android Auto • Kalos 1.2", fontWeight = FontWeight.SemiBold)
        Text("2005 • 1.2 8V szívó benzines • kézi váltó", color = Blue, fontSize = 14.sp)
        Text("Zöld pipa: kedvező fordulatszám. Sárga ↑ / ↓: fel- vagy visszaváltás megfontolható. Kék: melegedő motor. Piros: magas vízhőfok. Szürke: nincs friss adat.", color = Muted, fontSize = 13.sp)
        Text("A jelzés terhelésből és becsült fokozatból készül, nem gyári váltásjelző. A vízhőfok nem olajhőmérséklet. Valódi autós finomhangolás még szükséges.", color = Muted, fontSize = 12.sp)
        HorizontalDivider(color = Panel)
        Text("Fogyasztás forrása", fontWeight = FontWeight.SemiBold)
        Text("Elsőként az ECU üzemanyagáramát (015E), majd a mért MAF-ot használjuk. A Kalos profil MAF nélkül a friss MAP, RPM és IAT adatokból becsül. A MAP-becslés nem gyári fogyasztásmérés.", color = Muted, fontSize = 14.sp)
        FuelProfile.entries.forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(profile == option, onClick = { profile = option }, enabled = !state.active)
                Text(option.label)
            }
        }
        if (profile == FuelProfile.KALOS_12) {
            Text("Kalos: 1150 cm³ • töltési tényező (VE): ${number(efficiency.toDouble(), 2)}")
            Slider(value = efficiency, onValueChange = { efficiency = it }, valueRange = 0.4f..1.2f, enabled = !state.active)
            Text("A 0,80 VE kiinduló feltételezés, nem az autóhoz mért érték. Tankolás alapján kalibrálandó; a pontosság fordulatszámtól és terheléstől is függ.", color = Muted, fontSize = 12.sp)
        }
        Text("Korrekció: ${number(factor.toDouble(), 2)}×")
        Slider(value = factor, onValueChange = { factor = it }, valueRange = 0.5f..2f, steps = 149, enabled = !state.active)
        Text("A MAF- és MAP-becslés nem méri a hidegindítási dúsítást vagy a motorfék alatti befecskendezés-leállítást. A korrekció az ECU-alapú értékre is hat.", color = Muted, fontSize = 12.sp)
        Button(enabled = !state.active, onClick = { controller.saveSettings(FuelSettings(profile, factor.toDouble(), efficiency.toDouble())) }) { Text("Beállítások mentése és nullázás") }
        if (state.active) Text("Beállításmódosításhoz állítsd le a mérést.", color = Amber, fontSize = 12.sp)
        state.lastTrip?.let {
            HorizontalDivider(color = Panel)
            Text("Utolsó mentett valódi mérés", fontWeight = FontWeight.SemiBold)
            TripDetails(it)
        }
        Text("A hiányzó szakaszok nem kerülnek a fogyasztási átlagba. Új folyamat vagy új kézi kapcsolat új mérést kezd. Rövid automatikus újracsatlakozás megtartja az utat, és adathiányként jelöli a kimaradást.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
    }

}

@Composable
internal fun TripDetails(trip: TripSummary) {
    Surface(color = Panel, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Detail("Mért távolság", "${number(trip.distanceKm, 2)} km")
            Detail("Mért üzemanyag", "${number(trip.fuelLiters, 3)} l")
            Detail("Átlag a közös adatszakaszokon", "${number(trip.averageL100)} l/100 km")
            Detail("Megfigyelt idő", "${number(trip.observedSeconds / 60, 1)} perc")
            Detail("Érvényes adat aránya", "${number(trip.coveragePercent, 0)} %")
            if (trip.hasGaps) Text("Részleges mérés: volt hiányzó adat.", color = Amber, fontSize = 12.sp)
            if (trip.containsEstimate) Text("Becsült fogyasztást is tartalmaz.", color = Blue, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = Muted, fontSize = 13.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
