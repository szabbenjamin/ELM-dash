package hu.elmdash.connection

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30])
class RouteChartDataTest {
    private fun row(t:Long,speed:Double?,fix:String="2026-10-29T10:00:00Z")=JSONObject()
        .put("elapsedRealtimeMs",t).put("gps",JSONObject().put("fixUtc",fix).put("latitude",47).put("longitude",19))
        .put("obd",JSONObject().put("SPEED",JSONObject().put("value",speed ?: JSONObject.NULL))
            .put("VOLTAGE",JSONObject().put("value",14.1)))
    @Test fun timeWeightedGpsGroupsDoNotInterpolateGapsOrMissingValues() {
        val data=RouteChartData()
        data.add(row(0,20.0));data.add(row(1000,40.0));data.add(row(4000,null))
        data.add(row(5000,60.0,"2026-10-29T10:01:00Z"))
        data.add(row(6000,80.0,"2026-10-29T10:01:00Z"))
        data.add(row(20000,100.0,"2026-10-29T10:01:00Z"))
        val points=data.json()
        assertEquals(3,points.length())
        assertEquals(35.0,points.getJSONObject(0).getDouble("speed"),0.001)
        assertEquals(14.1,points.getJSONObject(0).getDouble("voltage"),0.001)
        assertTrue(points.getJSONObject(0).isNull("coolant"))
        assertEquals(60.0,points.getJSONObject(1).getDouble("speed"),0.001)
        assertTrue(points.getJSONObject(2).isNull("speed"))
        assertNotEquals(points.getJSONObject(1).getInt("segment"),points.getJSONObject(2).getInt("segment"))
    }
}
