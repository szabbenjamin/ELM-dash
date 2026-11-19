package hu.elmdash.dashboard

import android.content.Intent
import android.graphics.Bitmap
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
import hu.elmdash.connection.receiptai.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
internal fun ReceiptCapture(onSuggestion:(String,String,String,String)->Unit,onBusy:(Boolean)->Unit,onPhoto:(ByteArray?)->Unit) {
    val context=LocalContext.current
    val ai=remember { ChatGptReceipts(context) }
    val scope=rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    val accounts=remember(revision) { runCatching { ai.store.accounts() }.getOrDefault(emptyList()) }
    val selected=remember(revision) { runCatching { ai.store.selected() }.getOrNull() }
    val signed=selected?.optString("access_token")?.isNotBlank()==true
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var models by remember { mutableStateOf(emptyList<ReceiptModel>()) }
    var model by remember { mutableStateOf(ai.store.model) }
    var sourceChoice by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Uri?>(null) }
    var cameraPending by rememberSaveable { mutableStateOf(false) }
    val photoPath=rememberSaveable {
        val dir=File(context.cacheDir,"receipts").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis()-it.lastModified()>86400000 }?.forEach { it.delete() }
        File.createTempFile("receipt-ai-",".jpg",dir).absolutePath
    }
    val photo=remember(photoPath) { File(photoPath) }
    val photoUri=remember(photoPath) { FileProvider.getUriForFile(context,"${context.packageName}.receipts",photo) }
    val latestSuggestion by rememberUpdatedState(onSuggestion)
    val latestBusy by rememberUpdatedState(onBusy)
    fun status(value:Boolean) { busy=value;latestBusy(value) }
    fun failure(e:Throwable) { message=if(e is ReceiptAiException)e.message.orEmpty() else "A feldolgozás vagy a biztonságos belépés nem sikerült (${e.javaClass.simpleName}, ${e.stackTrace.firstOrNull { it.className.startsWith("hu.elmdash.") }?.let { "${it.fileName}:${it.lineNumber}" } ?: "feldolgozás"}). Próbáld újra, vagy add meg kézzel az adatokat." }
    suspend fun loadModels() {
        models=ai.models()
        if(models.none { it.id==model }) { model=models.firstOrNull()?.id.orEmpty();ai.store.model=model }
        if(models.isEmpty())message="Ehhez a fiókhoz nem érhető el választható modell."
    }
    DisposableEffect(Unit) { onDispose { if(!cameraPending)photo.delete();latestBusy(false) } }
    LaunchedEffect(selected?.optString("client_id"),signed) {
        if(signed) { status(true);try { loadModels() } catch(e:CancellationException){throw e} catch(e:Exception){failure(e)} finally { status(false) } }
        else models=emptyList()
    }
    fun login(client:String?) {
        status(true);message="Belépés a böngészőben. Utána térj vissza az ELM Dash-be."
        scope.launch {
            try {
                ai.login(client) { url->context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }
                loadModels()
                revision++;message="ChatGPT-fiók csatlakoztatva."
                sourceChoice=models.isNotEmpty()
            } catch(e:CancellationException){throw e} catch(e:Exception){failure(e)} finally { status(false) }
        }
    }
    fun process(uri: Uri) {
                status(true);message="Blokk feldolgozása ChatGPT-vel…";latestSuggestion("","","","")
                scope.launch {
                    try {
                        val bytes=withContext(Dispatchers.IO) {
                            if(Build.VERSION.SDK_INT<28)throw ReceiptAiException("Ehhez a képfeldolgozáshoz Android 9 vagy újabb kell. Add meg kézzel az adatokat.")
                            val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) {decoder,info,_->
                                val scale=minOf(1.0,2400.0/maxOf(info.size.width,info.size.height))
                                decoder.setTargetSize((info.size.width*scale).toInt().coerceAtLeast(1),(info.size.height*scale).toInt().coerceAtLeast(1))
                                decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
                            }
                            try { ByteArrayOutputStream().use { output->check(bitmap.compress(Bitmap.CompressFormat.JPEG,88,output));output.toByteArray() } } finally {bitmap.recycle()}
                        }
                        onPhoto(bytes)
                        val location = hu.elmdash.connection.receiptai.ReceiptLocation.context(context)
                        val result=ai.analyze(bytes,model,location)
                        fun number(n:Double?)=n?.let {String.format(java.util.Locale.US,"%.3f",it)}.orEmpty()
                        latestSuggestion(result.fuel.orEmpty(),number(result.liters),number(result.paidHuf),result.station.orEmpty())
                        message="Ellenőrizd az üzemanyagot, litert és összeget.\n"+result.explanation
                        pending=null;photo.delete()
                    } catch(e:CancellationException){throw e} catch(e:Exception){failure(e)} finally {status(false)}
                }
    }
    fun choose(uri:Uri?) { if(uri!=null) { onPhoto(null);pending=uri;message="Kép kiválasztva.";latestSuggestion("","","","");process(uri) } }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { choose(it) }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok->cameraPending=false;if(ok)choose(photoUri)else photo.delete() }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Button(enabled=!busy,onClick={
            if(!signed) login(accounts.firstOrNull()?.optString("client_id"))
            else if(models.isEmpty()) scope.launch {
                status(true)
                try { loadModels(); if(models.isNotEmpty()) sourceChoice=true } catch(e:CancellationException) { throw e } catch(e:Exception) { failure(e) } finally { status(false) }
            } else sourceChoice=true
        },modifier=Modifier.fillMaxWidth()) { Text(if(busy) "Blokk feldolgozása…" else "Blokk beolvasása") }
        Text("ChatGPT felismerés · a fotó és az elérhető friss helyadat az OpenAI-hoz kerül. Mentés előtt ellenőrizheted.",style=MaterialTheme.typography.bodySmall)
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall)
    }
    if(sourceChoice) AlertDialog(onDismissRequest={sourceChoice=false}, title={Text("Blokk beolvasása")},
        text={Text("Válassz fájlt vagy fotózd le a blokkot. A felismerés automatikusan elindul.")},
        confirmButton={TextButton(onClick={sourceChoice=false; runCatching { cameraPending=true;camera.launch(photoUri) }.onFailure {cameraPending=false;message="Nem érhető el kamera."}}) {Text("Befotózás")}},
        dismissButton={TextButton(onClick={sourceChoice=false;picker.launch("image/*")}) {Text("Fájl feltöltése")}})
}
