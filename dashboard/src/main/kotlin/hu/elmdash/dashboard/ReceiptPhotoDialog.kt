package hu.elmdash.dashboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun ReceiptPhotoDialog(name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, name) {
        value = withContext(Dispatchers.IO) {
            if (!name.matches(Regex("[a-f0-9-]+\\.jpg"))) null
            else BitmapFactory.decodeFile(File(context.noBackupFilesDir, "receipt-photos/$name").path)
        }
    }
    Dialog(onDismissRequest=onClose, properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding().padding(16.dp)) {
                TextButton(onClick=onClose) { Text("Blokkfotó · Bezárás") }
                bitmap?.let { Image(it.asImageBitmap(), "Tankoláshoz mentett blokk", Modifier.fillMaxSize(), contentScale=ContentScale.Fit) }
                    ?: Text("A blokkfotó nem érhető el ezen a telefonon.")
            }
        }
    }
}
