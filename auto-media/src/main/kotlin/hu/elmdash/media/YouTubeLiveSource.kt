package hu.elmdash.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI

/** Experimental public-live resolver. No accounts/cookies and no persisted signed URLs. */
internal object YouTubeLiveSource {
    const val WATCH = "https://www.youtube.com/watch?v=tRsQsTMvPNg"
    fun manifest(html: String): String {
        val encoded = Regex("\"hlsManifestUrl\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\")").find(html)?.groupValues?.get(1)
            ?: error("No public live HLS stream")
        return JSONArray("[$encoded]").getString(0).also(::validate)
    }
    private fun validate(url: String) {
        val uri=URI(url)
        require(uri.scheme == "https" && (uri.host == "youtube.com" || uri.host.endsWith(".youtube.com") || uri.host.endsWith(".googlevideo.com")))
    }
    fun lowestVariant(master: String): String {
        val lines=master.lines()
        return lines.indices.mapNotNull { i ->
            if (!lines[i].startsWith("#EXT-X-STREAM-INF:") || i+1 >= lines.size) null else {
                val bandwidth=Regex("(?:^|[:,])BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull()
                bandwidth?.let { it to lines[i+1].trim() }
            }
        }.minByOrNull { it.first }?.second?.also(::validate) ?: error("No live variant")
    }
    private fun read(url: String, maxBytes: Int, body: String? = null, visitor: String? = null): String {
        val c=URI(url).toURL().openConnection() as HttpURLConnection
        try {
            c.connectTimeout=15_000; c.readTimeout=15_000
            c.setRequestProperty("User-Agent", UA)
            if(body != null) {
                c.requestMethod="POST"; c.doOutput=true
                c.setRequestProperty("Content-Type","application/json")
                c.setRequestProperty("X-YouTube-Client-Name","101")
                c.setRequestProperty("X-YouTube-Client-Version","1.02")
                c.setRequestProperty("Origin","https://www.youtube.com")
                visitor?.let { c.setRequestProperty("X-Goog-Visitor-Id",it) }
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            require(c.responseCode == 200)
            return c.inputStream.use { stream ->
                val output=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                while (true) {
                    val count=stream.read(buffer)
                    if (count < 0) break
                    require(output.size()+count <= maxBytes)
                    output.write(buffer,0,count)
                }
                val bytes=output.toByteArray()
                require(bytes.size <= maxBytes)
                bytes.toString(Charsets.UTF_8)
            }
        } finally { c.disconnect() }
    }
    private const val UA="Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"
    fun audioVariant(master: String): String = master.lines().firstOrNull {
        it.startsWith("#EXT-X-MEDIA:") && it.contains("TYPE=AUDIO")
    }?.let { Regex("URI=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }?.also(::validate)
        ?: error("No audio stream")
    suspend fun resolve(): String = withContext(Dispatchers.IO) {
        val html=read(WATCH,4_000_000)
        val visitor=Regex("\"visitorData\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?: error("Missing public visitor context")
        val client=org.json.JSONObject().put("clientName","VISIONOS").put("clientVersion","1.02")
            .put("deviceMake","Apple").put("deviceModel","RealityDevice17,1").put("userAgent",UA)
            .put("osName","visionOS").put("osVersion","26.5.23O471").put("hl","en")
            .put("timeZone","UTC").put("utcOffsetMinutes",0).put("visitorData",visitor)
        val request=org.json.JSONObject().put("context",org.json.JSONObject().put("client",client))
            .put("videoId","tRsQsTMvPNg").put("contentCheckOk",true).put("racyCheckOk",true)
        val response=read("https://www.youtube.com/youtubei/v1/player?prettyPrint=false",2_000_000,request.toString(),visitor)
        audioVariant(read(manifest(response),500_000))
    }
}
