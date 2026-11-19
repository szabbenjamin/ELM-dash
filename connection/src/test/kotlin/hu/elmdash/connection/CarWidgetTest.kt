package hu.elmdash.connection

import android.graphics.Color
import hu.elmdash.trip.RangeEstimate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CarWidgetTest {
    @Test fun fuelArcUsesSameRangeThresholdsAsAutoAndUnknownStaysEmpty() {
        val thresholds=listOf(99.0,100.0,150.0,250.0,251.0)
        val colors=thresholds.map { km -> CarWidget.ring(100.0,RangeEstimate(km,8.0,"test",false)).getPixel(24,120) }
        assertNotEquals(colors[0],colors[1]);assertNotEquals(colors[1],colors[2]);assertEquals(colors[2],colors[3]);assertNotEquals(colors[3],colors[4])
        val empty=CarWidget.ring(null,RangeEstimate(null,null,"",true))
        assertEquals(Color.rgb(48,64,76),empty.getPixel(24,120))
    }
}
