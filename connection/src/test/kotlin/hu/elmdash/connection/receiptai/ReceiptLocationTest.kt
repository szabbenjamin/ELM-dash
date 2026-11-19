package hu.elmdash.connection.receiptai
import android.location.Location
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class ReceiptLocationTest {
    @Test fun oldImpreciseAndFutureLocationsAreRejected() {
        val fix=Location("gps").apply { accuracy=20f; elapsedRealtimeNanos=1_000_000_000L }
        assertTrue(ReceiptLocation.usable(fix,2000))
        assertFalse(ReceiptLocation.usable(fix,400_000))
        assertFalse(ReceiptLocation.usable(fix,500))
        fix.accuracy=1000f
        assertFalse(ReceiptLocation.usable(fix,2000))
    }
}
