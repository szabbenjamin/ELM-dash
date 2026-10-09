package hu.elmdash.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.elmdash.connection.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
fun RouteSettingsScreen(controller: DashboardController) {
    val context = LocalContext.current
    val initial = remember { controller.routeConfig() }
    var record by remember { mutableStateOf(initial.record) }
    var upload by remember { mutableStateOf(initial.upload) }
    var server by remember { mutableStateOf(initial.server) }
    var folder by remember { mutableStateOf(initial.folder) }
    var user by remember { mutableStateOf(initial.username) }
    // Never save credentials into savedInstanceState.
    var password by remember { mutableStateOf(initial.password) }
    var message by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val status by controller.routeStatus.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        message = if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) "Pontos hely engedélyezve. Az automatikus induláshoz állítsd a helyengedélyt Mindig engedélyezettre is." else "Pontos hely nélkül csak OBD-adatnapló készül."
    }
    val backgroundPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        message = "Az engedély módosítása után indítsd újra az OBD-kapcsolatot."
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            message = withContext(Dispatchers.IO) {
                runCatching {
                    val files = RouteRecorder.directory(context).listFiles().orEmpty().filter { it.extension == "jsonl" }
                    val out = checkNotNull(context.contentResolver.openOutputStream(uri))
                    ZipOutputStream(out).use { zip -> files.forEach { file ->
                        zip.putNextEntry(ZipEntry(file.name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                        val html = RouteReport.generate(context, file)
                        zip.putNextEntry(ZipEntry(html.name)); html.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    } }
                    "${files.size} lezárt út exportálva. A ZIP pontos helyadatokat tartalmazhat."
                }.getOrElse { "Az exportálás sikertelen." }
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Útvonal és WebDAV", style = MaterialTheme.typography.headlineSmall)
        Text("Járó motornál másodpercenként mentjük az elérhető OBD-adatokat, percenként GPS-helyzetet kérünk. A fájl pontos helyeket és időpontokat tartalmaz. Demóban nem készül útvonalfájl.")
        Row { Switch(record, { record = it }); Text("Útvonal- és részletes adatnapló", Modifier.padding(12.dp)) }
        Text("Az OBD-napló a jelenleg olvasott standard PID-eket tartalmazza, minőség- és frissességjelzéssel. Nem teljes szervizdiagnosztika: ABS, légzsák, gyártóspecifikus modulok és hibakód-lekérdezés nincs benne.")
        OutlinedButton(onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Pontos hely engedélyezése") }
        if (Build.VERSION.SDK_INT >= 29) OutlinedButton(onClick = {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                message = "Előbb engedélyezd a pontos helyet."
            } else if (Build.VERSION.SDK_INT == 29) backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }) { Text("Háttérbeli hely: Mindig engedélyezett") }
        Text("Android 10-től a GPS-rögzítéshez a Mindig engedélyezett helyhozzáférés is szükséges, hogy Android Auto-ról automatikusan indulhasson. Engedély nélkül az OBD-adatok továbbra is menthetők; a GPS hiányát jelöljük.")
        HorizontalDivider()
        Row { Switch(upload, { upload = it }); Text("Automatikus feltöltés út végén", Modifier.padding(12.dp)) }
        Text("Csak az engedélyezés után rögzített utak kerülnek a megadott célhelyre. HTTPS és WebDAV írási jog kell. Mobilinternetet is használhat; offline sorban várakozik.")
        OutlinedTextField(server, { server = it }, label = { Text("WebDAV alap-URL (https://…)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(folder, { folder = it }, label = { Text("Célmappa (például ELM-Dash/utak)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(user, { user = it }, label = { Text("Felhasználónév") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(password, { password = it }, label = { Text("Jelszó / alkalmazásjelszó") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = {
            runCatching { controller.saveRouteConfig(RouteConfig(record, upload, server, folder, user, password)) }
                .onSuccess { message = "Mentve. A GPS-beállítás érvényesítéséhez indítsd újra az OBD-kapcsolatot." }
                .onFailure { message = it.message ?: "A beállítás nem menthető." }
        }) { Text("Beállítások mentése") }
        Text(status)
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        OutlinedButton(onClick = { controller.retryRouteUploads(); message = "A mentett célhelyhez tartozó függő utak újrapróbálása ütemezve." }) { Text("Feltöltés újrapróbálása") }
        OutlinedButton(onClick = { export.launch("elm-utvonalnaplok.zip") }) { Text("Lezárt útvonalnaplók exportálása") }
        Text("A fájlok JSONL formátumúak, útazonosítóval. Sikertelen feltöltéskor helyben maradnak. Más szerver vagy felhasználó beállítása nem küldi el a régi útvonalakat az új célhelyre.")
        OutlinedButton(onClick = { deleteAll = false; confirmDelete = true }) { Text("Már feltöltött helyi naplók törlése") }
        OutlinedButton(onClick = { deleteAll = true; confirmDelete = true }) { Text("Összes lezárt helyi útvonalfájl törlése") }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Helyi másolatok törlése?") },
        text = { Text(if (deleteAll) "Minden lezárt részletes útvonalfájl törlődik, a még fel nem töltöttek is. Előbb exportáld, amit megőriznél. Az aktív út és az összesítő útnapló megmarad." else "Csak a sikeresen feltöltött, lezárt útvonalfájlok törlődnek. A távoli példány és az összesítő útnapló megmarad.") },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            scope.launch { message = withContext(Dispatchers.IO) {
                var count = 0
                RouteRecorder.directory(context).listFiles().orEmpty().filter { it.extension == "jsonl" }.forEach { file ->
                    val marker = File(file.path + ".sent")
                    if ((deleteAll || marker.exists()) && file.delete()) { marker.delete(); File(file.parentFile, file.nameWithoutExtension + ".html").delete(); count++ }
                }
                "$count helyi fájl törölve."
            } }
        }) { Text("Törlés") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Mégse") } })
}
