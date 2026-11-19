package hu.elmdash.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun RefuelDialog(onDismiss: () -> Unit, onSave: (String?, Double?, Double?, String?, Boolean, String?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var station by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var litersText by rememberSaveable { mutableStateOf("") }
    var paidText by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var review by rememberSaveable { mutableStateOf(false) }
    var full by rememberSaveable { mutableStateOf(false) }
    var onlyFull by rememberSaveable { mutableStateOf(false) }
    val liters = litersText.replace(',', '.').toDoubleOrNull()
    val paid = paidText.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && liters != null && liters.isFinite() && liters > 0 && liters <= 45 && paid != null && paid.isFinite() && paid > 0 && paid/liters in 200.0..2000.0
    fun number(n: Double?, digits: Int) = n?.let { String.format(Locale.forLanguageTag("hu-HU"),"%.${digits}f",it) } ?: "—"
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding().padding(20.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(if(review) "Így mentjük" else "Tankolás",style=MaterialTheme.typography.headlineSmall)
                    TextButton(onClick=onDismiss) { Text("Mégse") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    if (!review) {
                        ReceiptCapture(onSuggestion={ fuel, l, h, place -> station=place; name=fuel; litersText=l; paidText=h; onlyFull=false; full=false },onBusy={busy=it}, onPhoto={photo=it})
                        if (!onlyFull) {
                            OutlinedTextField(name,{name=it.take(120)},label={Text("Milyen benzin?")},placeholder={Text("Például: 95 E10")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                            OutlinedTextField(litersText,{litersText=it},label={Text("Hány litert tankoltál?")},suffix={Text("liter")},textStyle=LocalTextStyle.current.copy(fontSize=28.sp,fontWeight=FontWeight.Bold),modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                            OutlinedTextField(paidText,{paidText=it},label={Text("Mennyit fizettél a benzinért?")},suffix={Text("Ft")},textStyle=LocalTextStyle.current.copy(fontSize=28.sp,fontWeight=FontWeight.Bold),modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                            Text("Kedvezmények után, a többi vásárlás nélkül.",style=MaterialTheme.typography.bodySmall)
                            if (valid) Text("${number(paid!!/liters!!,1)} Ft/l",color=MaterialTheme.colorScheme.primary)
                        }
                        OutlinedTextField(station,{station=it.take(240)},label={Text("Benzinkút / cím (ellenőrizhető becslés)")},modifier=Modifier.fillMaxWidth())
                        TextButton(onClick={onlyFull=!onlyFull}) { Text(if(onlyFull) "Megadom a tankolás adatait" else "Nincs blokk/adat – csak a tele tankot jelölöm") }
                    } else {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                                Text(if(onlyFull) "Tele tank • adatok nélkül" else name,style=MaterialTheme.typography.titleLarge)
                                if(station.isNotBlank()) Text(station)
                                if (!onlyFull) {
                                    Text("${number(liters,2)} liter",fontSize=36.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                                    Text("${number(paid,0)} Ft",fontSize=36.sp,fontWeight=FontWeight.Bold)
                                    Text("${number(paid!!/liters!!,1)} Ft/l • kedvezmények után")
                                }
                            }
                        }


                        if(onlyFull) Text("Fogyasztási eltérést és új literárat így nem számolunk.")
                        TextButton(onClick={review=false}) { Text("Adatok javítása") }
                    }
                    Row { Checkbox(full,{full=it}); Text("Lekattanásig tankoltam (tele tank).",Modifier.padding(top=12.dp)) }
                    Text(if(full) "A tank újra tele; a megfelelően megfigyelt ciklusból tanulunk." else "Részleges tankolás: a betöltött litert hozzáadjuk a becsléshez. Nem jelöljük tele a tankot.",style=MaterialTheme.typography.bodySmall)
                }
                saveError?.let { Text(it, color=MaterialTheme.colorScheme.error) }
                if (photo != null) Text("A blokkfotót a tankolással együtt ezen a telefonon mentjük.", style=MaterialTheme.typography.bodySmall)
                Button(onClick={if(review) {
                    saving=true
                    scope.launch {
                        try {
                            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                photo?.let { bytes ->
                                    val dir=java.io.File(context.noBackupFilesDir,"receipt-photos").apply { mkdirs() }
                                    val target=java.io.File(dir,java.util.UUID.randomUUID().toString()+".jpg")
                                    try { target.writeBytes(bytes); target.name } catch(e:Exception) { target.delete(); throw e }
                                }
                            }
                            onSave(if(onlyFull)null else name,if(onlyFull)null else liters,if(onlyFull)null else paid,file,full,station.takeIf { it.isNotBlank() })
                        } catch(e:Exception) { saveError="A mentés nem sikerült. Próbáld újra." } finally { saving=false }
                    }
                } else review=true},
                    enabled=!saving && !busy && (if(onlyFull) full else valid),modifier=Modifier.fillMaxWidth().padding(top=12.dp).height(56.dp)) {
                    Text(if(review) "Helyesek az adatok · Mentés" else "Ellenőrzés")
                }
            }
        }
    }
}
