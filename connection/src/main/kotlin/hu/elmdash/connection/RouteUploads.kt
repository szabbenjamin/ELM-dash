package hu.elmdash.connection

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit

object RouteUploads {
    fun enqueue(context: Context, file: File, retry: Boolean = false) {
        if (File(file.path + ".sent").readTextOrEmpty() == "jsonl+html-v1") return
        val config = runCatching { RouteSettings(context).load() }.getOrNull() ?: return
        if (!config.upload) return
        val target = runCatching { file.bufferedReader().use { JSONObject(it.readLine()).optString("targetId") } }.getOrNull()
        if (target != runCatching { config.targetId() }.getOrNull()) return
        val request = OneTimeWorkRequestBuilder<RouteUploadWorker>()
            .setInputData(workDataOf("file" to file.name))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).addTag("elm-webdav").build()
        WorkManager.getInstance(context).enqueueUniqueWork("route-${file.name}", if (retry) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
    }
    fun enqueuePending(context: Context, retry: Boolean = false) {
        RouteRecorder.directory(context).listFiles().orEmpty().filter { it.extension == "jsonl" }.forEach { enqueue(context, it, retry) }
    }
    fun cancel(context: Context) { WorkManager.getInstance(context).cancelAllWorkByTag("elm-webdav") }
}

class DavFailure(val code: Int) : Exception("WebDAV HTTP $code") {
    val retryable get() = code == 408 || code == 429 || code in 500..599
}

/** Redirects are deliberately not followed: a server must never redirect credentials or route data elsewhere. */
class WebDavClient(private val client: OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false).followSslRedirects(false).connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS).build()) {
    fun upload(config: RouteConfig, file: File) {
        val destination = config.destination()
        val base = URI(config.server.trim()).toASCIIString().trimEnd('/') + "/"
        val relative = destination.removePrefix(base).trimEnd('/')
        var current = base
        relative.split('/').forEach { segment ->
            current += "$segment/"
            val status = request(config, current, "MKCOL")
            if (status !in 200..299 && status != 405) throw DavFailure(status)
        }
        val status = request(config, destination + file.name, "PUT", file)
        if (status !in 200..299) throw DavFailure(status)
    }
    internal fun request(config: RouteConfig, url: String, method: String, file: File? = null): Int {
        val body = file?.asRequestBody((if (file.extension == "html") "text/html; charset=utf-8" else "application/x-ndjson; charset=utf-8").toMediaType()) ?: ByteArray(0).toRequestBody()
        val request = Request.Builder().url(url).method(method, body)
            .header("Authorization", "Basic " + Base64.getEncoder().encodeToString("${config.username}:${config.password}".toByteArray(Charsets.UTF_8))).build()
        return client.newCall(request).execute().use { it.code }
    }
}

class RouteUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = RouteSettings(applicationContext)
        try {
            val config = settings.load()
            if (!config.upload) return@withContext Result.success()
            val name = inputData.getString("file") ?: return@withContext Result.failure()
            if (!name.matches(Regex("[a-zA-Z0-9-]+\\.jsonl"))) return@withContext Result.failure()
            val file = File(RouteRecorder.directory(applicationContext), name)
            if (!file.exists()) return@withContext Result.success()
            val target = file.bufferedReader().use { JSONObject(it.readLine()).optString("targetId") }
            if (target != config.targetId()) { settings.status = "A célhely megváltozott; a korábbi utak helyben maradnak."; return@withContext Result.failure() }
            if (File(file.path + ".sent").readTextOrEmpty() == "jsonl+html-v1") return@withContext Result.success()
            settings.status = "Útnapló feltöltése…"
            val report = RouteReport.generate(applicationContext, file)
            val client = WebDavClient()
            client.upload(config, file)
            client.upload(config, report)
            File(file.path + ".sent").writeText("jsonl+html-v1")
            settings.status = "Utolsó feltöltés sikeres: ${java.time.Instant.now()}"
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: DavFailure) {
            settings.status = "WebDAV HTTP ${e.code}. " + if (e.retryable) "Automatikus újrapróbálkozás." else "Ellenőrizd az URL-t, jogosultságot és jelszót, majd próbáld újra."
            if (e.retryable) Result.retry() else Result.failure()
        } catch (_: java.io.IOException) {
            settings.status = "Hálózati vagy TLS-hiba; az út helyben megmaradt, újrapróbálkozás."
            Result.retry()
        } catch (_: Exception) {
            settings.status = "A WebDAV beállítás vagy a napló nem olvasható. Ellenőrizd a beállításokat."
            Result.failure()
        }
    }
}

private fun File.readTextOrEmpty(): String = if (exists()) readText() else ""
