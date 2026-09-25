package hu.elmdash.connection

import android.content.Context
import hu.elmdash.trip.PetrolPrice
import hu.elmdash.trip.PetrolPriceParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.io.ByteArrayOutputStream

fun interface PetrolPriceProvider { suspend fun atJourneyStart(startMs: Long): PetrolPrice? }

class PetrolPriceRepository(
    context: Context,
    private val download: suspend () -> String = { downloadPage() }
) : PetrolPriceProvider {
    private val prefs = context.getSharedPreferences("elm-petrol-price", Context.MODE_PRIVATE)
    override suspend fun atJourneyStart(startMs: Long): PetrolPrice? {
        val result = try { PetrolPriceParser.parse(download())?.let { PetrolPrice(it, startMs) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        if (result != null) {
            if (result.fetchedAtMs >= prefs.getLong("at", -1))
                prefs.edit().putString("price", result.hufPerLiter.toString()).putLong("at", result.fetchedAtMs).apply()
            return result
        }
        val at = prefs.getLong("at", -1)
        val price = prefs.getString("price", null)?.toDoubleOrNull()
        return if (at >= 0 && startMs - at in 0..MAX_CACHE_MS && price != null)
            runCatching { PetrolPrice(price, at, cached = true) }.getOrNull() else null
    }
    companion object {
        const val MAX_CACHE_MS = 7 * 24 * 60 * 60 * 1000L
        private suspend fun downloadPage(): String = withContext(Dispatchers.IO) {
            val connection = URI(PetrolPrice.SOURCE_URL).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", "ELMDash/0.12 (Android; petrol-price estimate)")
                connection.setRequestProperty("Accept", "text/html")
                check(connection.responseCode == 200)
                check(connection.contentType?.contains("text/html", ignoreCase = true) == true)
                connection.inputStream.use {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    val deadline = System.nanoTime() + 8_000_000_000L
                    while (true) {
                        check(System.nanoTime() < deadline)
                        val count = it.read(buffer)
                        if (count < 0) break
                        check(output.size() + count <= 512 * 1024)
                        output.write(buffer, 0, count)
                    }
                    String(output.toByteArray(), Charsets.UTF_8)
                }
            } finally { connection.disconnect() }
        }
    }
}
