package hu.elmdash.media

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RadioPanel() {
    val context = LocalContext.current
    val store = remember { RadioGraph.get(context) }
    val state by store.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    fun command(action: String, id: String? = null) {
        runCatching {
            val intent = Intent(context, DashboardMediaService::class.java).setAction(action).putExtra("station", id)
            if (action in setOf(DashboardMediaService.ACTION_PLAY, DashboardMediaService.ACTION_NEXT, DashboardMediaService.ACTION_PREVIOUS)) context.startForegroundService(intent) else context.startService(intent)
        }.onFailure { formError = "A rádió nem indítható: ${it.message}" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Rádió", style = MaterialTheme.typography.headlineLarge)
        Text(state.selected.name, style = MaterialTheme.typography.titleLarge)
        Text(when { state.error != null -> state.error!!; state.recoveryMessage != null -> state.recoveryMessage!!; state.buffering -> "Kapcsolódás / pufferelés…";
            state.playing -> "Szól az internetes rádió"; else -> "Szünetel" })
        Text("Valódi hanglejátszás mobilneten vagy Wi-Fi-n. A rádió és az OBD-mérés egymástól független.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { command(DashboardMediaService.ACTION_PLAY, state.selected.id) }) { Text("Lejátszás") }
            OutlinedButton(onClick = { command(DashboardMediaService.ACTION_PAUSE) }, enabled = state.playing || state.buffering) { Text("Szünet") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { command(DashboardMediaService.ACTION_PREVIOUS) }) { Text("Előző") }
            OutlinedButton(onClick = { command(DashboardMediaService.ACTION_NEXT) }) { Text("Következő") }
            TextButton(onClick = { command(DashboardMediaService.ACTION_STOP) }) { Text("Leállítás") }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(state.autoPlayOnAa, onCheckedChange = { store.autoPlay(it) })
            Text("Rádió indítása Android Auto csatlakozáskor", modifier = Modifier.padding(start = 12.dp))
        }
        Text("Térerőkiesés után automatikusan újracsatlakozik. A kézi szünet elsőbbséget élvez.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, label = { Text("Állomás keresése") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text("Csak kedvencek") })
        val visible = state.stations.filter { RadioStore.matches(it.name, query) && (!favoritesOnly || it.id in state.favorites) }
        if (visible.isEmpty()) Text("Nincs találat. Módosítsd a keresést vagy a kedvencszűrőt.")
        visible.forEach { station ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(station.name, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { command(DashboardMediaService.ACTION_PLAY, station.id) }) { Text("Hallgatás") }
                        TextButton(onClick = { store.toggleFavorite(station.id) }) { Text(if (station.id in state.favorites) "★ Kedvenc" else "☆ Kedvenc") }
                        if (!station.builtIn) TextButton(enabled = station.id != state.selectedId || !(state.playing || state.buffering),
                            onClick = { store.remove(station.id) }) { Text("Törlés") }
                    }
                }
            }
        }
        HorizontalDivider()
        Text("Saját rádió hozzáadása", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, label = { Text("Állomás neve") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(url, { url = it }, label = { Text("Közvetlen stream URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("MP3, AAC, Ogg és HLS stream. A rádió weboldalának címe önmagában nem elég; DRM-es vagy bejelentkezést igénylő adás nem támogatott.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { runCatching { store.add(name, url); name = ""; url = ""; formError = null }
            .onFailure { formError = it.message } }) { Text("Állomás mentése") }
        formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(24.dp))
    }
}
