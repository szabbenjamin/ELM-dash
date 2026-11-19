package hu.elmdash.connection

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Time-weighted OBD samples grouped by GPS fix. Gaps are never interpolated. */
internal class RouteChartData {
    private data class Group(val fix: String,val lat:Double,val lon:Double,val segment:Int,
        val sums:DoubleArray=DoubleArray(4),val seconds:DoubleArray=DoubleArray(4))
    private data class Sample(val time:Long,val group:Group,val values:List<Double?>)
    private val groups=mutableListOf<Group>()
    private var previous:Sample?=null
    private var segment=0
    fun breakSegment() { previous=null;segment++ }
    fun add(row:JSONObject) {
        val gps=row.optJSONObject("gps") ?: run {breakSegment();return}
        val fix=gps.optString("fixUtc");val time=runCatching {Instant.parse(fix).toEpochMilli()}.getOrNull()
        val lat=gps.optDouble("latitude");val lon=gps.optDouble("longitude")
        if(time==null || !lat.isFinite() || !lon.isFinite() || lat !in -85.0..85.0 || lon !in -180.0..180.0) {breakSegment();return}
        val at=row.optLong("elapsedRealtimeMs",-1)
        if(at<0) {breakSegment();return}
        val old=previous
        if(old!=null && at-old.time !in 1..10000)breakSegment()
        val last=groups.lastOrNull()
        if(last!=null && last.fix!=fix && time-Instant.parse(last.fix).toEpochMilli() !in 1..120000)breakSegment()
        val group=groups.lastOrNull()?.takeIf {it.fix==fix && it.segment==segment} ?: Group(fix,lat,lon,segment).also {groups+=it}
        previous?.let { prev ->
            val dt=(at-prev.time)/1000.0
            prev.values.forEachIndexed { i,v -> if(v!=null) {prev.group.sums[i]+=v*dt;prev.group.seconds[i]+=dt} }
        }
        val obd=row.optJSONObject("obd")
        val values=listOf("SPEED","VOLTAGE","COOLANT","LOAD").map { key ->
            obd?.optJSONObject(key)?.optDouble("value",Double.NaN)?.takeIf {it.isFinite()}
        }
        previous=Sample(at,group,values)
    }
    fun json():JSONArray=JSONArray().also { a -> groups.forEach { g ->
        a.put(JSONObject().put("utc",g.fix).put("latitude",g.lat).put("longitude",g.lon).put("segment",g.segment).also { j ->
            listOf("speed","voltage","coolant","load").forEachIndexed { i,key ->
                j.put(key,if(g.seconds[i]>0)g.sums[i]/g.seconds[i] else JSONObject.NULL)
            }
        })
    } }
}
