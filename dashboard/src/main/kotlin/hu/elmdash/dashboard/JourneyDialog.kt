package hu.elmdash.dashboard

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import hu.elmdash.connection.RouteReport
import hu.elmdash.trip.JourneyRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun JourneyDialog(record: JourneyRecord, onClose: () -> Unit) {
    val context = LocalContext.current
    var html by remember(record.id) { mutableStateOf<String?>(null) }
    var error by remember(record.id) { mutableStateOf(false) }
    LaunchedEffect(record.id) {
        html = withContext(Dispatchers.IO) { runCatching { RouteReport.journey(context, record) }.getOrNull() }
        error = html == null
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Utazás", Modifier.padding(12.dp), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onClose) { Text("Bezárás ✕") }
                }
                if (error) Text("Az út részletei nem tölthetők be. A mentett napló megmaradt.", Modifier.padding(20.dp))
                else if (html == null) CircularProgressIndicator(Modifier.padding(20.dp))
                else AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.domStorageEnabled = false
                        settings.userAgentString += " ELM-Dash/0.15 (+https://github.com/szabbenjamin/ELM-dash)"
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (request.url.scheme == "https" && request.url.host == "www.openstreetmap.org")
                                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)) }
                                return true
                            }
                        }
                        loadDataWithBaseURL("https://appassets.androidplatform.net/route/", html!!, "text/html", "UTF-8", null)
                    }
                }, onRelease = { it.stopLoading(); it.destroy() })
            }
        }
    }
}
