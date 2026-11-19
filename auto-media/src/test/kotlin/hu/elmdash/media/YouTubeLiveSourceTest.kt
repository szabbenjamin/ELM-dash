package hu.elmdash.media
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class YouTubeLiveSourceTest {
    @Test fun extractsEscapedManifestAndChoosesLowestBandwidth() {
        assertEquals("https://manifest.googlevideo.com/live?a=1&b=2",YouTubeLiveSource.manifest("""{"hlsManifestUrl":"https://manifest.googlevideo.com/live?a=1\u0026b=2"}"""))
        assertEquals("https://manifest.googlevideo.com/low",YouTubeLiveSource.lowestVariant("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=500\nhttps://manifest.googlevideo.com/high\n#EXT-X-STREAM-INF:BANDWIDTH=100\nhttps://manifest.googlevideo.com/low"))
    }
    @Test fun rejectsMissingStreamAndUntrustedManifestHost() {
        assertThrows(Exception::class.java) { YouTubeLiveSource.manifest("consent required") }
        assertThrows(Exception::class.java) { YouTubeLiveSource.manifest("""{"hlsManifestUrl":"https://example.org/live"}""") }
    }
}
