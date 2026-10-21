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

@Composable
internal fun RefuelDialog(onDismiss: () -> Unit, onSave: (String?, Double?, Double?) -> Unit) {
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
                        ReceiptCapture(onSuggestion={ fuel, l, h -> name=fuel; litersText=l; paidText=h; onlyFull=false; full=false },onBusy={busy=it})
                        if (!onlyFull) {
                            OutlinedTextField(name,{name=it.take(120)},label={Text("Milyen benzin?")},placeholder={Text("Például: 95 E10")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                            OutlinedTextField(litersText,{litersText=it},label={Text("Hány litert tankoltál?")},suffix={Text("liter")},textStyle=LocalTextStyle.current.copy(fontSize=28.sp,fontWeight=FontWeight.Bold),modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                            OutlinedTextField(paidText,{paidText=it},label={Text("Mennyit fizettél a benzinért?")},suffix={Text("Ft")},textStyle=LocalTextStyle.current.copy(fontSize=28.sp,fontWeight=FontWeight.Bold),modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                            Text("Kedvezmények után, a többi vásárlás nélkül.",style=MaterialTheme.typography.bodySmall)
                            if (valid) Text("${number(paid!!/liters!!,1)} Ft/l",color=MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick={onlyFull=!onlyFull}) { Text(if(onlyFull) "Megadom a tankolás adatait" else "Nincs blokk/adat – csak a tele tankot jelölöm") }
                    } else {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                                Text(if(onlyFull) "Tele tank • adatok nélkül" else name,style=MaterialTheme.typography.titleLarge)
                                if (!onlyFull) {
                                    Text("${number(liters,2)} liter",fontSize=36.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                                    Text("${number(paid,0)} Ft",fontSize=36.sp,fontWeight=FontWeight.Bold)
                                    Text("${number(paid!!/liters!!,1)} Ft/l • kedvezmények után")
                                }
                            }
                        }
                        Row { Checkbox(full,{full=it}); Text("Igen, tele tankoltam az autót.",Modifier.padding(top=12.dp)) }
                        Text("Az első automatikus pisztolyleoldásig töltés elegendő; nem kell tovább tölteni.",style=MaterialTheme.typography.bodySmall)
                        if(onlyFull) Text("Fogyasztási eltérést és új literárat így nem számolunk.")
                        TextButton(onClick={review=false;full=false}) { Text("Adatok javítása") }
                    }
                }
                Button(onClick={if(review) onSave(if(onlyFull)null else name,if(onlyFull)null else liters,if(onlyFull)null else paid) else review=true},
                    enabled=if(review) full else !busy && (onlyFull || valid),modifier=Modifier.fillMaxWidth().padding(top=12.dp).height(56.dp)) {
                    Text(if(review) "Helyesek az adatok · Mentés" else "Ellenőrzés")
                }
            }
        }
    }
}
