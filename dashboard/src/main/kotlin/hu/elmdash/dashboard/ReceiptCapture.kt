package hu.elmdash.dashboard

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import hu.elmdash.trip.ReceiptParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Bundled on-device OCR. The photo and recognized text are never persisted or sent to a backend. */
@Composable
internal fun ReceiptCapture(onSuggestion: (String, String, String) -> Unit, onBusy: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var busy by remember { mutableStateOf(false) }
    var alive by remember { mutableStateOf(true) }
    var raw by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var cameraPending by rememberSaveable { mutableStateOf(false) }
    val photoPath = rememberSaveable {
        val dir = File(context.cacheDir,"receipts").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis()-it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        File.createTempFile("receipt-", ".jpg",dir).absolutePath
    }
    val photo = remember(photoPath) { File(photoPath) }
    val photoUri = remember(photo) { FileProvider.getUriForFile(context,"${context.packageName}.receipts",photo) }
    val latestSuggestion by rememberUpdatedState(onSuggestion)
    val latestBusy by rememberUpdatedState(onBusy)
    DisposableEffect(Unit) { onDispose { alive=false; recognizer.close(); if (!cameraPending) photo.delete(); latestBusy(false) } }
    fun recognize(uri: Uri) {
        if (busy) return
        busy=true; latestBusy(true); message="Szövegfelismerés a telefonon…"; raw=""
        latestSuggestion("", "", "")
        scope.launch {
            val image = withContext(Dispatchers.IO) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= 28) {
                        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) { decoder, info, _ ->
                            val scale = minOf(1.0,2400.0/maxOf(info.size.width,info.size.height))
                            decoder.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1),(info.size.height*scale).toInt().coerceAtLeast(1))
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                        InputImage.fromBitmap(bitmap,0)
                    } else InputImage.fromFilePath(context,uri)
                }.getOrNull()
            }
            if (image == null) { busy=false; latestBusy(false); message="A kép nem olvasható. Próbálj másik fotót vagy töltsd ki kézzel."; return@launch }
            recognizer.process(image).addOnSuccessListener { result ->
                if (alive) {
                    raw = result.text.take(50_000)
                    val suggestion = ReceiptParser.parse(raw)
                    latestSuggestion(suggestion.fuel.orEmpty(), suggestion.liters?.let { "%.3f".format(java.util.Locale.US,it) }.orEmpty(), suggestion.paidHuf?.let { "%.2f".format(java.util.Locale.US,it) }.orEmpty())
                    message = listOfNotNull(suggestion.fuel?.let { "Felismert tétel: $it" },*suggestion.warnings.toTypedArray()).joinToString("\n")
                }
            }.addOnFailureListener { if (alive) message="A felismerés nem sikerült. Új fotó vagy kézi bevitel szükséges." }
                .addOnCompleteListener { photo.delete(); if (alive) { busy=false; latestBusy(false) } }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) recognize(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> cameraPending = false; if (ok) recognize(photoUri) else photo.delete() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Blokk beolvasása · helyben", style=MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled=!busy,onClick={ runCatching { cameraPending = true; camera.launch(photoUri) }.onFailure { cameraPending = false; message="Nem érhető el kamera. Válassz képet." } }) { Text("Fotózás") }
            OutlinedButton(enabled=!busy,onClick={ picker.launch("image/*") }) { Text("Képválasztás") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(if (raw.isBlank()) message else "Beolvasva. Ellenőrizd a három mezőt.",style=MaterialTheme.typography.bodySmall)
        if (raw.isNotBlank()) {
            TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Részletek bezárása" else "Felismerés és kedvezmények részletei") }
            if (expanded) { Text(message,style=MaterialTheme.typography.bodySmall); Text(raw,style=MaterialTheme.typography.bodySmall) }
        }
        Text("Helyi felismerés · a fotó nem kerül felhőbe.",style=MaterialTheme.typography.bodySmall)
    }
}
